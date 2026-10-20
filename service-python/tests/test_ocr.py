import os
import uuid
from pathlib import Path
from unittest.mock import patch

import pytest
from fastapi import HTTPException
from fastapi.testclient import TestClient

from app.config import Settings
from app.main import app
from app.schemas.requests import InternalOcrRequest
from app.services.ocr import (
    MockOcrEngine,
    OcrService,
    ReceiptNormalizer,
    TesseractOcrEngine,
)


@pytest.fixture
def test_settings(tmp_path: Path) -> Settings:
    storage = tmp_path / "receipts"
    storage.mkdir()
    return Settings(
        predictor_mode="mock",
        database_url="postgresql://test:test@localhost:5432/test",
        internal_service_token="test-secret-token-12345",
        internal_service_token_previous="test-previous-secret",
        receipts_storage_path=str(storage),
        ocr_engine="mock",
    )


@pytest.fixture
def client(test_settings: Settings) -> TestClient:
    app.state.settings = test_settings
    app.state.ocr_service = OcrService(
        storage_base_path=Path(test_settings.receipts_storage_path),
        engine=MockOcrEngine(),
    )
    return TestClient(app)


def test_normalizer_complete_argentine_ticket():
    sample_text = """
    SUPERMERCADO DIA S.A.
    CUIT: 30-68584975-1
    AV. CORRIENTES 1234 - CABA
    IVA RESPONSABLE INSCRIPTO
    P.V.: 0012  TICKET FACTURA B: 0001-00045612
    FECHA: 15/10/2026   HORA: 18:32
    ----------------------------------------
    LECHE ENTERA LA SERENISIMA       $ 1.500,00
    PAN LACTAL INTEGRAL              $ 2.450,50
    CAFE MOLIDO 250G                 $ 4.200,00
    SUBTOTAL:                        $ 8.150,50
    DESCUENTO:                          $ 500,00
    TOTAL ARS:                       $ 7.650,50
    ----------------------------------------
    TARJETA DE DEBITO VISA           $ 7.650,50
    CAE: 71234567890123  VTO CAE: 25/10/2026
    GRACIAS POR SU COMPRA
    """

    date_val, amount_val, desc_val, warnings = ReceiptNormalizer.normalize(sample_text)

    assert date_val == "2026-10-15"
    assert amount_val == 7650.50
    assert "LECHE ENTERA" in desc_val
    assert len(desc_val) <= 500
    assert desc_val != "Varios"
    assert not any("importe" in w for w in warnings)
    assert not any("fecha" in w for w in warnings)


def test_normalizer_alternative_date_formats():
    text_iso = "COMPRA FARMACIA\nEMISION: 2026-09-28\nTOTAL: $ 12500,00"
    d1, a1, _, _ = ReceiptNormalizer.normalize(text_iso)
    assert d1 == "2026-09-28"
    assert a1 == 12500.00

    text_dots = "LIBRERIA DEL CENTRO\nFECHA 10.08.2026\nTOTAL A PAGAR $ 3.420,75"
    d2, a2, _, _ = ReceiptNormalizer.normalize(text_dots)
    assert d2 == "2026-08-10"
    assert a2 == 3420.75


def test_normalizer_rejects_future_date():
    future_text = "TICKET\nFECHA: 15/10/2099\nTOTAL: $ 500,00\nGASTO VARIO"
    d, a, _, warnings = ReceiptNormalizer.normalize(future_text)
    assert d is None
    assert a == 500.00
    assert any("fecha" in w for w in warnings)


def test_normalizer_fallback_to_varios_on_unusable_description():
    empty_desc_text = "FECHA: 10/10/2026\nTOTAL: $ 1000,00\n123456789\n$$$$ %%%"
    d, a, desc, warnings = ReceiptNormalizer.normalize(empty_desc_text)
    assert d == "2026-10-10"
    assert a == 1000.00
    assert desc == "Varios"
    assert any("Varios" in w for w in warnings)


def test_normalizer_fallback_to_varios_on_too_long_description():
    long_desc = "Gasto " * 150  # > 500 caracteres
    text = f"FECHA: 05/10/2026\nTOTAL: $ 250,00\n{long_desc}"
    d, a, desc, warnings = ReceiptNormalizer.normalize(text)
    assert d == "2026-10-05"
    assert a == 250.00
    assert desc == "Varios"
    assert any("Varios" in w for w in warnings)


def test_normalizer_empty_text():
    d, a, desc, warnings = ReceiptNormalizer.normalize("")
    assert d is None
    assert a is None
    assert desc == "Varios"
    assert len(warnings) >= 2


def test_ocr_api_authentication(client: TestClient):
    req_body = {
        "contract_version": "1",
        "request_id": str(uuid.uuid4()),
        "organization_id": 1,
        "receipt_id": str(uuid.uuid4()),
        "storage_key": "org_1/receipt.png",
    }

    # Sin encabezado Authorization
    res = client.post("/internal/v1/ocr", json=req_body)
    assert res.status_code == 401

    # Con Bearer inválido
    res = client.post(
        "/internal/v1/ocr",
        json=req_body,
        headers={"Authorization": "Bearer bad-token"},
    )
    assert res.status_code == 403

    # Con Bearer previo válido
    # Fallará en 404 porque no existe el archivo físico, lo que prueba que pasó la autenticación
    res = client.post(
        "/internal/v1/ocr",
        json=req_body,
        headers={"Authorization": "Bearer test-previous-secret"},
    )
    assert res.status_code == 404


def test_ocr_path_traversal_prevention(client: TestClient, test_settings: Settings):
    headers = {"Authorization": f"Bearer {test_settings.internal_service_token}"}

    # Traversal con ..
    res = client.post(
        "/internal/v1/ocr",
        json={
            "contract_version": "1",
            "request_id": str(uuid.uuid4()),
            "organization_id": 1,
            "receipt_id": str(uuid.uuid4()),
            "storage_key": "org_1/../../../etc/passwd",
        },
        headers=headers,
    )
    assert res.status_code == 400

    # Organización cruzada (storage_key apunta a org_2 para organization_id 1)
    res = client.post(
        "/internal/v1/ocr",
        json={
            "contract_version": "1",
            "request_id": str(uuid.uuid4()),
            "organization_id": 1,
            "receipt_id": str(uuid.uuid4()),
            "storage_key": "org_2/secret.png",
        },
        headers=headers,
    )
    assert res.status_code == 400


def test_ocr_successful_processing(client: TestClient, test_settings: Settings):
    storage_root = Path(test_settings.receipts_storage_path)
    org_dir = storage_root / "org_1"
    org_dir.mkdir(parents=True, exist_ok=True)
    sample_file = org_dir / "ticket.png"
    sample_file.write_bytes(b"dummy image content")

    ticket_text = """
    ESTACION YPF NORTE
    FECHA: 12/10/2026
    NAFTA INFINIA 40L
    TOTAL: $ 42.500,00
    """

    app.state.ocr_service.engine = MockOcrEngine(text=ticket_text)

    req_id = str(uuid.uuid4())
    rec_id = str(uuid.uuid4())

    res = client.post(
        "/internal/v1/ocr",
        json={
            "contract_version": "1",
            "request_id": req_id,
            "organization_id": 1,
            "receipt_id": rec_id,
            "storage_key": "org_1/ticket.png",
        },
        headers={"Authorization": f"Bearer {test_settings.internal_service_token}"},
    )

    assert res.status_code == 200
    data = res.json()
    assert data["contract_version"] == "1"
    assert data["request_id"] == req_id
    assert data["organization_id"] == 1
    assert data["receipt_id"] == rec_id
    assert data["currency"] == "ARS"
    assert data["status"] == "OK"
    assert data["date"] == "2026-10-12"
    assert data["amount"] == 42500.00
    assert "NAFTA INFINIA" in data["description"]
    assert "raw_text" in data


def test_tesseract_ocr_engine_execution(tmp_path: Path):
    engine = TesseractOcrEngine()
    # Ejecución sobre archivo inexistente devuelve cadena vacía de forma segura sin excepciones no controladas
    assert engine.extract_text(tmp_path / "nonexistent.png") == ""


@pytest.mark.parametrize("text", ["FECHA: 10.08.2026", "FECHA: 10/08/2026", "FECHA: 31.02.2026", "TOTAL: -$ 500,00"])
def test_dates_and_negative_values_are_not_positive_amounts(text):
    _, amount, _, warnings = ReceiptNormalizer.normalize(text)
    assert amount is None
    assert any("importe" in warning for warning in warnings)


def test_date_and_amount_on_same_line_preserves_real_amount():
    d, amount, _, _ = ReceiptNormalizer.normalize("FECHA: 10.08.2026 TOTAL $ 1.234,56")
    assert d == "2026-08-10"
    assert amount == 1234.56


@pytest.mark.parametrize("label", ["DT AL", "T0TAL", "T O T A L"])
def test_total_label_and_decimal_spacing_ocr_variants(label):
    _, amount, _, _ = ReceiptNormalizer.normalize(f"{label} 8123. 45")
    assert amount == 8123.45


@pytest.mark.parametrize("text", ["Ruido\n11.00", "IVA $ 812,00", "CAE 11.00"])
def test_isolated_numbers_and_tax_lines_are_not_expense_amounts(text):
    _, amount, _, warnings = ReceiptNormalizer.normalize(text)
    assert amount is None
    assert any("importe" in warning for warning in warnings)


def test_administrative_dates_do_not_become_expense_date():
    text = "Inicio de Actividades: 01/09/2011\nVTO CAE: 15/09/2026\nTOTAL: 8123.45"
    d, amount, _, warnings = ReceiptNormalizer.normalize(text)
    assert d is None
    assert amount == 8123.45
    assert any("fecha" in warning for warning in warnings)


@pytest.mark.parametrize("other_org", [10, 11])
def test_similar_organization_prefix_is_rejected(tmp_path, other_org):
    other = tmp_path / f"org_{other_org}"
    other.mkdir()
    (other / "ticket.png").write_bytes(b"synthetic")
    service = OcrService(tmp_path, MockOcrEngine())
    with pytest.raises(HTTPException) as error:
        service._resolve_safe_path(1, f"org_{other_org}/ticket.png")
    assert error.value.status_code == 400


@pytest.mark.parametrize("kind", ["file", "directory", "root"])
def test_symbolic_links_are_rejected_before_resolution(tmp_path, kind):
    storage = tmp_path / "receipts"
    external = tmp_path / "external"
    external.mkdir()
    (external / "ticket.png").write_bytes(b"synthetic")
    if kind == "root":
        (external / "org_1").mkdir()
        (external / "org_1" / "ticket.png").write_bytes(b"synthetic")
        storage.symlink_to(external, target_is_directory=True)
    else:
        storage.mkdir()
        if kind == "directory":
            (storage / "org_1").symlink_to(external, target_is_directory=True)
        else:
            (storage / "org_1").mkdir()
            (storage / "org_1" / "ticket.png").symlink_to(external / "ticket.png")
    service = OcrService(storage, MockOcrEngine())
    with pytest.raises(HTTPException) as error:
        service._resolve_safe_path(1, "org_1/ticket.png")
    assert error.value.status_code == 404

