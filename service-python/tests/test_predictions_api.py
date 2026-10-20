from uuid import UUID

import pytest
from fastapi.testclient import TestClient

from app.api.predictions import (
    get_prediction_service,
    get_settings,
    get_views_repository,
)
from app.config import Settings
from app.main import app
from app.schemas.requests import PredictionRequest
from app.services.views_repository import ViewsRepositoryError


@pytest.fixture
def client():
    with TestClient(app) as test_client:
        yield test_client


@pytest.fixture
def payload():
    return {
        "contract_version": "1",
        "request_id": "7f69bfaa-465e-4e74-a13f-169447481317",
        "organization_id": 1,
        "currency": "ARS",
        "target_month": "2026-07",
        "categories": [
            {
                "category_id": 1,
                "available_from_month": "2026-01",
            }
        ],
        "history": [
            {
                "month": f"2026-{month:02d}",
                "category_totals": [
                    {
                        "category_id": 1,
                        "amount_minor": month * 10000,
                    }
                ],
            }
            for month in range(1, 7)
        ],
    }


@pytest.fixture
def internal_payload():
    return {
        "contract_version": "2",
        "request_id": "7f69bfaa-465e-4e74-a13f-169447481317",
        "organization_id": 1,
        "target_month": "2026-07",
    }


URL = "/api/v1/predictions/monthly"
INTERNAL_URL = "/internal/v2/predictions/monthly"


def test_health_remains_available(client):
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {
        "status": "UP",
        "service": "python",
    }


def test_prediction_returns_contract_response(client, payload):
    app.dependency_overrides[get_settings] = lambda: Settings(
        predictor_mode="mock",
        database_url="postgresql://localhost/test",
        internal_service_token="active-secret",
        internal_service_token_previous="",
    )
    try:
        response = client.post(URL, json=payload)

        assert response.status_code == 200

        body = response.json()

        assert body["contract_version"] == "1"
        assert UUID(body["request_id"]) == UUID(payload["request_id"])
        assert body["organization_id"] == 1
        assert body["currency"] == "ARS"
        assert body["target_month"] == "2026-07"
        assert body["status"] == "OK"
        assert body["simulated"] is True

        assert body["organization"] == {
        "status": "OK",
        "estimated_amount_minor": 60000,
        "months_used": 6,
        "required_months": 6,
        "method": "mock",
        "model_version": "1",
    }

        assert body["categories"] == [
        {
            "category_id": 1,
            "status": "OK",
            "estimated_amount_minor": 60000,
            "months_used": 6,
            "required_months": 6,
            "method": "mock",
            "model_version": "1",
        }
    ]
    finally:
        app.dependency_overrides.pop(get_settings, None)


def test_legacy_callback_endpoint_is_retired(client):
    response = client.get(
        "/api/v1/predictions/organization/1/monthly?target_month=2026-07"
    )
    assert response.status_code == 404


def test_internal_prediction_rejects_missing_or_invalid_bearer_token(
    client,
    internal_payload,
):
    app.dependency_overrides[get_settings] = lambda: Settings(
        predictor_mode="mock",
        database_url="postgresql://localhost/test",
        internal_service_token="active-secret",
        internal_service_token_previous="previous-secret",
    )

    try:
        missing_response = client.post(INTERNAL_URL, json=internal_payload)
        assert missing_response.status_code == 401
        assert missing_response.json()["contract_version"] == "2"
        assert missing_response.json()["error"]["code"] == "UNAUTHORIZED"

        invalid_response = client.post(
            INTERNAL_URL,
            json=internal_payload,
            headers={"Authorization": "Bearer wrong-secret"},
        )
        assert invalid_response.status_code == 403
        assert invalid_response.json()["contract_version"] == "2"
        assert invalid_response.json()["error"]["code"] == "FORBIDDEN"
    finally:
        app.dependency_overrides.pop(get_settings, None)


def test_internal_prediction_accepts_active_and_previous_secret_and_reads_views(
    client,
    payload,
    internal_payload,
):
    class FakeViewsRepository:
        def build_prediction_request(self, req):
            assert req.organization_id == 1
            assert req.target_month == "2026-07"
            return PredictionRequest.model_validate(payload)

    app.dependency_overrides[get_settings] = lambda: Settings(
        predictor_mode="mock",
        database_url="postgresql://localhost/test",
        internal_service_token="active-secret",
        internal_service_token_previous="previous-secret",
    )
    app.dependency_overrides[get_views_repository] = (
        lambda: FakeViewsRepository()
    )

    try:
        for secret in ("active-secret", "previous-secret"):
            response = client.post(
                INTERNAL_URL,
                json=internal_payload,
                headers={"Authorization": f"Bearer {secret}"},
            )
            assert response.status_code == 200
            body = response.json()
            assert body["contract_version"] == "2"
            assert body["target_month"] == "2026-07"
            assert body["organization"]["estimated_amount_minor"] == 60000
    finally:
        app.dependency_overrides.pop(get_settings, None)
        app.dependency_overrides.pop(get_views_repository, None)


def test_views_repository_failure_uses_contract_error_response(
    client,
    internal_payload,
):
    class FailingViewsRepository:
        def build_prediction_request(self, _req):
            raise ViewsRepositoryError("Internal SQL details must not be exposed")

    app.dependency_overrides[get_settings] = lambda: Settings(
        predictor_mode="mock",
        database_url="postgresql://localhost/test",
        internal_service_token="active-secret",
        internal_service_token_previous="",
    )
    app.dependency_overrides[get_views_repository] = (
        lambda: FailingViewsRepository()
    )

    try:
        response = client.post(
            INTERNAL_URL,
            json=internal_payload,
            headers={"Authorization": "Bearer active-secret"},
        )
    finally:
        app.dependency_overrides.pop(get_settings, None)
        app.dependency_overrides.pop(get_views_repository, None)

    assert response.status_code == 503
    assert response.json()["contract_version"] == "2"
    assert response.json()["error"]["code"] == "SERVICE_UNAVAILABLE"
    assert "Internal SQL details" not in response.text


def test_insufficient_history_is_not_http_error(client, payload):
    payload["history"] = payload["history"][:2]
    payload["target_month"] = "2026-03"

    response = client.post(URL, json=payload)

    assert response.status_code == 200

    body = response.json()

    assert body["status"] == "INSUFFICIENT_HISTORY"
    assert body["organization"]["estimated_amount_minor"] is None
    assert body["organization"]["months_used"] == 0


def test_duplicate_month_returns_contract_error(client, payload):
    payload["history"][1]["month"] = "2026-01"

    response = client.post(URL, json=payload)

    assert response.status_code == 422

    body = response.json()

    assert body["request_id"] == payload["request_id"]
    assert body["error"]["code"] == "INVALID_REQUEST"
    assert {
        "field": "history",
        "code": "DUPLICATE_MONTH",
    } in body["error"]["details"]


def test_category_name_is_rejected(client, payload):
    payload["categories"][0]["name"] = "Alimentos"

    response = client.post(URL, json=payload)

    assert response.status_code == 422
    assert {
        "field": "categories[0].name",
        "code": "UNKNOWN_FIELD",
    } in response.json()["error"]["details"]


def test_invalid_request_id_returns_null(client, payload):
    payload["request_id"] = "no-es-un-uuid"

    response = client.post(URL, json=payload)

    assert response.status_code == 422
    assert response.json()["request_id"] is None


def test_malformed_json_returns_contract_error(client):
    response = client.post(
        URL,
        content='{"contract_version":',
        headers={"Content-Type": "application/json"},
    )

    assert response.status_code == 422

    body = response.json()

    assert body["request_id"] is None
    assert body["error"]["code"] == "INVALID_REQUEST"
    assert any(
        detail["code"] == "INVALID_JSON"
        for detail in body["error"]["details"]
    )


def test_wrong_content_type_is_rejected(client):
    response = client.post(
        URL,
        content="texto de prueba",
        headers={"Content-Type": "text/plain"},
    )

    assert response.status_code == 415

    body = response.json()

    assert body["request_id"] is None
    assert body["error"]["code"] == "UNSUPPORTED_MEDIA_TYPE"
    assert body["error"]["details"] == []


def test_large_body_is_rejected(client):
    response = client.post(
        URL,
        content=b" " * (1024 * 1024 + 1),
        headers={"Content-Type": "application/json"},
    )

    assert response.status_code == 413
    assert response.json()["error"]["code"] == "PAYLOAD_TOO_LARGE"


def test_json_content_type_with_charset_is_accepted(
    client,
    payload,
):
    import json

    response = client.post(
        URL,
        content=json.dumps(payload),
        headers={"Content-Type": "application/json; charset=utf-8"},
    )

    assert response.status_code == 200


def test_exact_body_limit_is_not_rejected_as_too_large(
    client,
    payload,
):
    import json

    encoded = json.dumps(payload).encode("utf-8")
    padded = encoded + b" " * (1024 * 1024 - len(encoded))

    response = client.post(
        URL,
        content=padded,
        headers={"Content-Type": "application/json"},
    )

    assert response.status_code == 200


class FailingPredictionService:
    def predict(self, payload, contract_version=None):
        raise RuntimeError("Fallo intencional de prueba.")


def test_internal_error_returns_contract_response(payload):
    app.dependency_overrides[get_prediction_service] = (
        lambda: FailingPredictionService()
    )

    try:
        with TestClient(
            app,
            raise_server_exceptions=False,
        ) as client:
            response = client.post(URL, json=payload)

        assert response.status_code == 500

        body = response.json()

        assert body["request_id"] == payload["request_id"]
        assert body["error"]["code"] == "INTERNAL_ERROR"
        assert body["error"]["details"] == []
        assert "Fallo intencional" not in response.text
    finally:
        app.dependency_overrides.pop(
            get_prediction_service,
            None,
        )
