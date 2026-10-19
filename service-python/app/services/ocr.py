import logging
import re
import subprocess
from datetime import date, datetime
from pathlib import Path
from typing import Protocol
from zoneinfo import ZoneInfo

from fastapi import HTTPException

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
        timeout_seconds: float = 15.0,
    ) -> None:
        self.binary_path = binary_path
        self.lang = lang
        self.timeout_seconds = timeout_seconds

    def extract_text(self, image_path: Path) -> str:
        try:
            res = subprocess.run(
                [self.binary_path, str(image_path), "stdout", "-l", self.lang],
                capture_output=True,
                text=True,
                timeout=self.timeout_seconds,
                check=False,
            )
            if res.returncode != 0:
                logger.warning(
                    "Tesseract finalizó con código %s: %s",
                    res.returncode,
                    res.stderr,
                )
                return ""
            return res.stdout
        except (subprocess.TimeoutExpired, FileNotFoundError, OSError) as e:
            logger.warning("Error ejecutando Tesseract: %s", e)
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
        r"\b(?:total\s+a\s+pagar|importe\s+total|total\s+neto|total\s+general|total\s*\$|total\s+ars|total)\b",
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
            elif not is_subtotal:
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
