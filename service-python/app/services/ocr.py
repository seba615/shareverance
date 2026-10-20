import logging
import re
import subprocess
import time
from collections import deque
from tempfile import TemporaryDirectory
from datetime import date, datetime
from pathlib import Path
from typing import Protocol
from zoneinfo import ZoneInfo

from fastapi import HTTPException
from PIL import Image, ImageFilter, ImageOps, UnidentifiedImageError

from app.schemas.requests import InternalOcrRequest
from app.schemas.responses import InternalOcrResponse, OcrStatus

logger = logging.getLogger(__name__)

BUENOS_AIRES_TZ = ZoneInfo("America/Argentina/Buenos_Aires")
MAX_SAFE_AMOUNT = 9_999_999_999.99


class OcrEngine(Protocol):
    def extract_text(self, image_path: Path) -> str:
        ...


class TesseractOcrEngine:
    def __init__(
        self,
        binary_path: str = "tesseract",
        lang: str = "spa+eng",
        timeout_seconds: float = 12.0,
    ) -> None:
        self.binary_path = binary_path
        self.lang = lang
        self.timeout_seconds = timeout_seconds

    def extract_text(self, image_path: Path) -> str:
        deadline = time.monotonic() + self.timeout_seconds
        best = self._recognize(image_path, deadline, 3)
        if self._score(best)[0] == 2:
            return best
        try:
            # Sólo copias temporales; nunca escribir en el volumen de comprobantes.
            with TemporaryDirectory(prefix="shareverance-ocr-") as directory:
                with self._prepare_image(image_path) as prepared, self._binarize(prepared) as binary, self._binarize(prepared, 0.85) as alternate:
                    variants = [(binary, 0, 6), (alternate, 0, 6), (prepared, 0, 6), (binary, 0, 11),
                                (binary, 90, 6), (binary, 270, 6), (binary, 180, 6)]
                    for source, angle, mode in variants:
                        if deadline - time.monotonic() < 0.2:
                            break
                        candidate_path = Path(directory) / "candidate.png"
                        with source.rotate(angle, expand=True, fillcolor=255) as candidate:
                            candidate.save(candidate_path, dpi=(300, 300))
                        text = self._recognize(candidate_path, deadline, mode)
                        if self._score(text) > self._score(best):
                            best = text
                        if self._score(best)[0] == 2 and self._score(best)[1] >= 2:
                            break
        except (OSError, UnidentifiedImageError, ValueError, Image.DecompressionBombError):
            logger.warning("No se pudo preparar la imagen para OCR")
        return best

    @staticmethod
    def _score(text: str) -> tuple[int, int, int, int]:
        date_value, amount, description, _ = ReceiptNormalizer.normalize(text)
        repetitions = ReceiptNormalizer._find_amounts_in_text(text).count(amount) if amount is not None else 0
        return (int(date_value is not None) + int(amount is not None), min(repetitions, 3),
                int(description != "Varios"), min(sum(c.isalpha() for c in text), 1000))

    @staticmethod
    def _prepare_image(image_path: Path) -> Image.Image:
        with Image.open(image_path) as source:
            if source.width * source.height > 50_000_000:
                raise ValueError("Imagen fuera del límite de píxeles")
            oriented = ImageOps.exif_transpose(source)
            try:
                document = TesseractOcrEngine._crop_document(oriented)
                oriented.close()
                oriented = document
                oriented.thumbnail((2400, 2400))
                gray = ImageOps.grayscale(oriented)
            finally:
                oriented.close()
        try:
            scale = min(2.0, 1600 / gray.width, 3200 / max(gray.size))
            if scale > 1:
                resized = gray.resize((round(gray.width * scale), round(gray.height * scale)), Image.Resampling.LANCZOS)
                gray.close()
                gray = resized
            contrast = ImageOps.autocontrast(gray)
            try:
                return contrast.filter(ImageFilter.UnsharpMask(radius=1, percent=120, threshold=3))
            finally:
                contrast.close()
        finally:
            gray.close()

    @staticmethod
    def _binarize(image: Image.Image, percentile: float = 0.9) -> Image.Image:
        # Estimar el blanco del papel sin asumir una exposición fija.
        histogram = image.histogram()
        target = image.width * image.height * percentile
        accumulated = 0
        paper_level = 255
        for level, count in enumerate(histogram):
            accumulated += count
            if accumulated >= target:
                paper_level = level
                break
        threshold = max(64, min(235, paper_level - 20))
        return image.point(lambda value: 255 if value > threshold else 0)

    @staticmethod
    def _crop_document(image: Image.Image) -> Image.Image:
        """Recorte conservador de una región de papel clara sobre fondo oscuro."""
        with ImageOps.grayscale(image) as preview:
            preview.thumbnail((240, 240))
            width, height = preview.size
            values = preview.tobytes()
        total = width * height
        if sum(value < 140 for value in values) < total * 0.2:
            return image.copy()
        mask = bytearray(value >= 185 for value in values)
        components = []
        for start in range(total):
            if not mask[start]:
                continue
            queue = deque([start])
            mask[start] = 0
            count = 0
            left = right = start % width
            top = bottom = start // width
            while queue:
                index = queue.popleft()
                x, y = index % width, index // width
                count += 1
                left, right = min(left, x), max(right, x)
                top, bottom = min(top, y), max(bottom, y)
                for neighbor in (index - width if y else -1,
                                 index + width if y + 1 < height else -1,
                                 index - 1 if x else -1,
                                 index + 1 if x + 1 < width else -1):
                    if neighbor >= 0 and mask[neighbor]:
                        mask[neighbor] = 0
                        queue.append(neighbor)
            components.append((count, left, top, right + 1, bottom + 1))
        components.sort(reverse=True)
        if not components:
            return image.copy()
        count, left, top, right, bottom = components[0]
        area = (right - left) * (bottom - top)
        runner_up = components[1][0] if len(components) > 1 else 0
        if (count < total * 0.1 or not total * 0.1 < area < total * 0.85
                or count < area * 0.35 or count < runner_up * 1.5):
            return image.copy()
        pad = 3
        return image.crop((max(0, left - pad) * image.width // width,
                           max(0, top - pad) * image.height // height,
                           min(width, right + pad) * image.width // width,
                           min(height, bottom + pad) * image.height // height))

    def _recognize(self, image_path: Path, deadline: float, mode: int) -> str:
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            return ""
        try:
            res = subprocess.run(
                [self.binary_path, str(image_path), "stdout", "-l", self.lang, "--psm", str(mode)],
                capture_output=True,
                text=True,
                timeout=min(4.0, remaining),
                check=False,
            )
            if res.returncode != 0:
                logger.warning(
                    "Tesseract finalizó con código %s",
                    res.returncode,
                )
                return ""
            return res.stdout
        except (subprocess.TimeoutExpired, FileNotFoundError, OSError):
            logger.warning("No se pudo completar el intento OCR")
            return ""


class MockOcrEngine:
    def __init__(self, text: str = "") -> None:
        self.text = text

    def extract_text(self, image_path: Path) -> str:
        return self.text


class ReceiptNormalizer:
    """Normaliza texto crudo extraído por OCR a fecha, importe y descripción."""

    BOILERPLATE_PATTERNS = [
        r"\bcuit\b",
        r"\bi\.?v\.?a\.?\b",
        r"\bfactura\b",
        r"\bticket\b",
        r"\boriginal\b",
        r"\bduplicado\b",
        r"\bc\.?a\.?e\.?\b",
        r"\bvto\.?\b",
        r"\biibb\b",
        r"\bingresos brutos\b",
        r"\bpunto de venta\b",
        r"\bresponsable inscripto\b",
        r"\bmonotributo\b",
        r"\bconsumidor final\b",
        r"\bposnet\b",
        r"\bmercadopago\b",
        r"\btarjeta\b",
        r"\bdebito\b",
        r"\bcredito\b",
    ]

    TOTAL_KEYWORD_PATTERN = re.compile(
        r"\b(?:total\s+a\s+pagar|importe\s+total|total\s+neto|total\s+general|total\s*\$|total\s+ars|total)\b"
        r"|^\W*[td]\s*[o0]?\s*t\s*a\s*[l1i]\b",
        re.IGNORECASE,
    )

    SUBTOTAL_KEYWORD_PATTERN = re.compile(
        r"\bsubtotal\b",
        re.IGNORECASE,
    )

    AMOUNT_COMBINED_REGEX = re.compile(
        r"(?:(?:\$|ARS)\s*)?((?:\d{1,3}(?:\.\d{3})+,\d{2})|(?:\d{1,3}(?:,\d{3})+\.\d{2})|(?:\d+,\d{2})|(?:\d+\.\d{2}))\b",
        re.IGNORECASE,
    )

    DATE_REGEXES = [
        # DD/MM/YYYY, DD-MM-YYYY, DD.MM.YYYY
        re.compile(r"\b(\d{1,2})[/.-](\d{1,2})[/.-](\d{4})\b"),
        # YYYY/MM/DD, YYYY-MM-DD
        re.compile(r"\b(\d{4})[/.-](\d{1,2})[/.-](\d{1,2})\b"),
        # DD/MM/YY
        re.compile(r"\b(\d{1,2})[/.-](\d{1,2})[/.-](\d{2})\b"),
    ]

    DATE_KEYWORD_PATTERN = re.compile(
        r"(?:fecha\s+de\s+emisi[oó]n|f\.\s*emisi[oó]n|fecha\b|fech\b)",
        re.IGNORECASE,
    )

    @classmethod
    def normalize(cls, raw_text: str) -> tuple[str | None, float | None, str, list[str]]:
        warnings: list[str] = []
        if not raw_text or not raw_text.strip():
            warnings.append("No se pudo extraer texto del comprobante.")
            warnings.append("No se pudo reconocer la fecha del comprobante.")
            warnings.append("No se pudo reconocer el importe del comprobante.")
            return None, None, "Varios", warnings

        lines = [line.strip() for line in raw_text.splitlines() if line.strip()]

        extracted_date = cls._extract_date(lines, warnings)
        extracted_amount = cls._extract_amount(lines, warnings)
        extracted_desc = cls._extract_description(lines, warnings)

        return extracted_date, extracted_amount, extracted_desc, warnings

    @classmethod
    def _extract_date(cls, lines: list[str], warnings: list[str]) -> str | None:
        today_ba = datetime.now(BUENOS_AIRES_TZ).date()

        # Primero buscar en líneas con palabras clave de fecha
        candidates_prioritized: list[date] = []
        candidates_general: list[date] = []

        for line in lines:
            if re.search(r"inicio\s+de\s+activ|vencimiento|\bvto\b|\bcae\b", line, re.IGNORECASE):
                continue
            has_keyword = bool(cls.DATE_KEYWORD_PATTERN.search(line))
            found_dates = cls._find_dates_in_text(line, today_ba)
            if has_keyword:
                candidates_prioritized.extend(found_dates)
            else:
                candidates_general.extend(found_dates)

        chosen: date | None = None
        if candidates_prioritized:
            chosen = candidates_prioritized[0]
        elif candidates_general:
            # Preferir la más reciente no futura
            chosen = max(candidates_general)

        if chosen is not None:
            return chosen.isoformat()

        warnings.append("No se pudo reconocer la fecha del comprobante.")
        return None

    @classmethod
    def _find_dates_in_text(cls, text: str, max_date: date) -> list[date]:
        valid_dates: list[date] = []

        # DD/MM/YYYY o DD-MM-YYYY
        for match in cls.DATE_REGEXES[0].finditer(text):
            day, month, year = int(match.group(1)), int(match.group(2)), int(match.group(3))
            try:
                d = date(year, month, day)
                if d <= max_date and year >= 2000:
                    valid_dates.append(d)
            except ValueError:
                continue

        # YYYY-MM-DD
        for match in cls.DATE_REGEXES[1].finditer(text):
            year, month, day = int(match.group(1)), int(match.group(2)), int(match.group(3))
            try:
                d = date(year, month, day)
                if d <= max_date and year >= 2000:
                    valid_dates.append(d)
            except ValueError:
                continue

        # DD/MM/YY
        for match in cls.DATE_REGEXES[2].finditer(text):
            day, month, short_year = int(match.group(1)), int(match.group(2)), int(match.group(3))
            year = 2000 + short_year
            try:
                d = date(year, month, day)
                if d <= max_date:
                    valid_dates.append(d)
            except ValueError:
                continue

        return valid_dates

    @classmethod
    def _extract_amount(cls, lines: list[str], warnings: list[str]) -> float | None:
        # Priorizar líneas con palabras clave de TOTAL
        keyword_amounts: list[float] = []
        general_amounts: list[float] = []

        for line in lines:
            # Excluir líneas que son exclusivamente subtotal si no mencionan total
            is_subtotal = bool(cls.SUBTOTAL_KEYWORD_PATTERN.search(line))
            has_total_keyword = bool(cls.TOTAL_KEYWORD_PATTERN.search(line))

            found_amounts = cls._find_amounts_in_text(line)

            if has_total_keyword and not is_subtotal:
                keyword_amounts.extend(found_amounts)
            elif (not is_subtotal and re.search(r"\$|\bARS\b", line, re.IGNORECASE)
                  and not any(re.search(pattern, line, re.IGNORECASE) for pattern in cls.BOILERPLATE_PATTERNS)):
                general_amounts.extend(found_amounts)

        if keyword_amounts:
            # En la línea de total, tomar el monto mayor (el total general)
            chosen = max(keyword_amounts)
            return round(chosen, 2)

        if general_amounts:
            # Si no hubo keyword explícita, tomar el monto mayor razonable
            chosen = max(general_amounts)
            return round(chosen, 2)

        warnings.append("No se pudo reconocer el importe del comprobante.")
        return None

    @classmethod
    def _find_amounts_in_text(cls, text: str) -> list[float]:
        amounts: list[float] = []

        # Una fecha con puntos no es un importe decimal, incluso si es inválida.
        for date_pattern in cls.DATE_REGEXES:
            text = date_pattern.sub(" ", text)
        text = re.sub(r"(?<=\d)([.,])\s+(?=\d{2}\b)", r"\1", text)

        for match in cls.AMOUNT_COMBINED_REGEX.finditer(text):
            if text[:match.start()].rstrip().endswith("-"):
                continue
            raw = match.group(1)
            # Normalizar a decimal estándar
            if "," in raw and "." in raw:
                # Ejemplo 1.234,56
                if raw.find(".") < raw.find(","):
                    clean = raw.replace(".", "").replace(",", ".")
                else:
                    # Ejemplo 1,234.56
                    clean = raw.replace(",", "")
            elif "," in raw:
                # Ejemplo 1234,56
                clean = raw.replace(",", ".")
            else:
                clean = raw

            try:
                val = float(clean)
                if 0 < val <= MAX_SAFE_AMOUNT:
                    amounts.append(val)
            except ValueError:
                continue

        return amounts

    @classmethod
    def _extract_description(cls, lines: list[str], warnings: list[str]) -> str:
        clean_lines: list[str] = []

        for line in lines:
            line_stripped = line.strip()
            if not line_stripped:
                continue

            lower = line_stripped.lower()

            # Omitir si es línea de total o fecha
            if cls.TOTAL_KEYWORD_PATTERN.search(line_stripped):
                continue
            if cls.DATE_KEYWORD_PATTERN.search(line_stripped):
                continue

            # Omitir boilerplate impositivo / comprobante
            is_boilerplate = any(re.search(bp, lower) for bp in cls.BOILERPLATE_PATTERNS)
            if is_boilerplate:
                continue

            # Omitir líneas que son solamente números o símbolos
            if re.match(r"^[\d\s\.,;:/\$\-\#]+$", line_stripped):
                continue

            clean_lines.append(line_stripped)

        if not clean_lines:
            warnings.append("La descripción no fue utilizable y se asignó 'Varios'.")
            return "Varios"

        combined = " ".join(clean_lines)
        # Normalizar espacios repetidos
        normalized = re.sub(r"\s+", " ", combined).strip()

        if not normalized or len(normalized) > 500:
            warnings.append("La descripción no fue utilizable y se asignó 'Varios'.")
            return "Varios"

        # Verificar que tenga al menos alguna letra
        if not re.search(r"[a-zA-ZáéíóúÁÉÍÓÚñÑ]", normalized):
            warnings.append("La descripción no fue utilizable y se asignó 'Varios'.")
            return "Varios"

        return normalized


class OcrService:
    def __init__(self, storage_base_path: Path, engine: OcrEngine) -> None:
        self.storage_base_path = storage_base_path
        self.engine = engine

    def process_ocr(self, request: InternalOcrRequest) -> InternalOcrResponse:
        file_path = self._resolve_safe_path(request.organization_id, request.storage_key)

        raw_text = self.engine.extract_text(file_path)

        date_val, amount_val, desc_val, warnings = ReceiptNormalizer.normalize(raw_text)

        status: OcrStatus
        if date_val is not None and amount_val is not None:
            status = "OK"
        elif date_val is not None or amount_val is not None:
            status = "PARTIAL"
        else:
            status = "FAILED"

        return InternalOcrResponse(
            contract_version="1",
            request_id=request.request_id,
            organization_id=request.organization_id,
            receipt_id=request.receipt_id,
            currency="ARS",
            status=status,
            date=date_val,
            amount=amount_val,
            description=desc_val,
            raw_text=raw_text.strip() if raw_text else None,
            warnings=warnings,
        )

    def _resolve_safe_path(self, organization_id: int, storage_key: str) -> Path:
        key = storage_key.strip()
        if not key or key.startswith("/") or "\\" in key or ".." in key:
            raise HTTPException(
                status_code=400,
                detail="Clave de almacenamiento inválida.",
            )

        base = self.storage_base_path.absolute()
        org_prefix = base / f"org_{organization_id}"
        target = base / key

        # Comparar componentes, no prefijos textuales (org_1 != org_10).
        if not target.is_relative_to(org_prefix):
            raise HTTPException(
                status_code=400,
                detail="Acceso denegado a comprobante fuera del ámbito de la organización.",
            )

        # Inspeccionar la ruta original antes de resolve(), incluidos raíz y padres.
        curr = target
        while curr != curr.parent:
            if curr.is_symlink():
                raise HTTPException(
                    status_code=404,
                    detail="El archivo especificado no está disponible.",
                )
            curr = curr.parent

        target = target.resolve()
        if not target.is_relative_to(org_prefix.resolve()):
            raise HTTPException(status_code=400, detail="Ruta de comprobante no permitida.")

        if not target.exists() or not target.is_file():
            raise HTTPException(
                status_code=404,
                detail="El archivo de comprobante no existe.",
            )

        return target
