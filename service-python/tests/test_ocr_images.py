import hashlib
import shutil
import subprocess
from pathlib import Path
from unittest.mock import patch

import pytest
from PIL import Image, ImageDraw, ImageFont

from app.services.ocr import ReceiptNormalizer, TesseractOcrEngine

TEXT = "Compra de prueba\nFECHA: 15/10/2026\nTOTAL ARS 1234,56"


def synthetic_receipt(path: Path, angle=0, exif=False):
    image = Image.new("RGB", (760, 260), (220, 220, 220))
    font = ImageFont.truetype("DejaVuSans.ttf", 36)
    ImageDraw.Draw(image).multiline_text((30, 30), TEXT, font=font, fill=(90, 90, 90), spacing=12)
    rotated = image.rotate(angle, expand=True)
    if exif:
        metadata = Image.Exif()
        metadata[274] = 6
        rotated.save(path, exif=metadata)
    else:
        rotated.save(path)
    image.close()
    rotated.close()


def test_readable_original_does_not_need_preprocessing(tmp_path):
    engine = TesseractOcrEngine()
    with patch("app.services.ocr.subprocess.run", return_value=subprocess.CompletedProcess([], 0, TEXT, "")) as run:
        with patch.object(engine, "_prepare_image", side_effect=AssertionError("No debe prepararse")):
            assert engine.extract_text(tmp_path / "source.png") == TEXT
    assert run.call_count == 1


def test_fallback_preserves_original_and_removes_temporary_copies(tmp_path):
    source = tmp_path / "source.png"
    Image.new("RGB", (100, 60), "white").save(source)
    before = hashlib.sha256(source.read_bytes()).digest()
    temporary_paths = []

    def recognize(command, **kwargs):
        path = Path(command[1])
        if path == source:
            return subprocess.CompletedProcess(command, 0, "", "")
        temporary_paths.append(path)
        assert path.exists()
        return subprocess.CompletedProcess(command, 0, TEXT, "")

    with patch("app.services.ocr.subprocess.run", side_effect=recognize):
        assert TesseractOcrEngine().extract_text(source) == TEXT
    assert hashlib.sha256(source.read_bytes()).digest() == before
    assert temporary_paths and all(not path.exists() for path in temporary_paths)


def test_time_budget_is_shared_between_attempts(tmp_path):
    source = tmp_path / "source.png"
    Image.new("RGB", (100, 60), "white").save(source)
    with patch("app.services.ocr.time.monotonic", side_effect=[0, 0, 2]):
        with patch("app.services.ocr.subprocess.run", side_effect=subprocess.TimeoutExpired("tesseract", 1)) as run:
            assert TesseractOcrEngine(timeout_seconds=1).extract_text(source) == ""
    assert run.call_count == 1
    assert run.call_args.kwargs["timeout"] == 1


def test_exif_orientation_is_applied_without_changing_source(tmp_path):
    source = tmp_path / "source.jpg"
    Image.new("RGB", (600, 1000), "white").save(source, exif=Image.Exif())
    with Image.open(source) as image:
        metadata = image.getexif()
        metadata[274] = 6
        image.save(source, exif=metadata)
    with TesseractOcrEngine._prepare_image(source) as prepared:
        assert prepared.width > prepared.height
        assert prepared.mode == "L"
        assert max(prepared.size) <= 3200
    with Image.open(source) as original:
        assert original.size == (600, 1000)
        assert original.getexif()[274] == 6


def test_document_crop_removes_dark_background_but_keeps_paper():
    with Image.new("RGB", (600, 900), (20, 20, 20)) as source:
        ImageDraw.Draw(source).rectangle((120, 60, 480, 840), fill=(235, 235, 235))
        with TesseractOcrEngine._crop_document(source) as cropped:
            assert 360 <= cropped.width < 420
            assert 780 <= cropped.height < 840
        assert source.size == (600, 900)


def test_full_frame_paper_is_not_cropped():
    with Image.new("RGB", (600, 900), "white") as source:
        with TesseractOcrEngine._crop_document(source) as cropped:
            assert cropped.size == source.size


def test_ambiguous_bright_regions_are_not_arbitrarily_cropped():
    with Image.new("RGB", (600, 900), (20, 20, 20)) as source:
        draw = ImageDraw.Draw(source)
        draw.rectangle((30, 60, 260, 840), fill="white")
        draw.rectangle((340, 60, 570, 840), fill="white")
        with TesseractOcrEngine._crop_document(source) as cropped:
            assert cropped.size == source.size


def test_binarization_separates_faint_ink_without_changing_source():
    with Image.new("L", (100, 100), 235) as source:
        ImageDraw.Draw(source).rectangle((20, 20, 30, 30), fill=185)
        with TesseractOcrEngine._binarize(source) as binary:
            assert binary.getpixel((25, 25)) == 0
            assert binary.getpixel((80, 80)) == 255
        assert source.getpixel((25, 25)) == 185


def test_fallback_prefers_repeated_total_over_first_candidate_with_misread_cents(tmp_path):
    source = tmp_path / "source.png"
    Image.new("RGB", (100, 60), "white").save(source)
    first = "FECHA: 15/10/2026\nTOTAL 8123.90"
    supported = "FECHA: 15/10/2026\nDT AL 8123. 45\nOtros medios de pago 8123.45\nSuma de pagos 8123.45"
    responses = [subprocess.CompletedProcess([], 0, text, "") for text in ["", first, supported]]
    with patch("app.services.ocr.subprocess.run", side_effect=responses):
        text = TesseractOcrEngine().extract_text(source)
    assert ReceiptNormalizer.normalize(text)[1] == 8123.45


@pytest.mark.skipif(not shutil.which("tesseract"), reason="Requiere Tesseract local")
@pytest.mark.parametrize("angle,exif", [(0, False), (90, False), (90, True)])
def test_real_engine_recognizes_synthetic_receipt_variants(tmp_path, angle, exif):
    source = tmp_path / "receipt.jpg"
    synthetic_receipt(source, angle, exif)
    text = TesseractOcrEngine().extract_text(source)
    date_value, amount, _, _ = ReceiptNormalizer.normalize(text)
    assert date_value == "2026-10-15"
    assert amount == 1234.56
