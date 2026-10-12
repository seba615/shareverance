import json
from urllib.parse import parse_qs

from app.services.java_client import JavaServiceClient


class FakeResponse:
    def __init__(self, body):
        self.body = json.dumps(body).encode()

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return None

    def read(self):
        return self.body


def test_client_requests_and_reuses_scoped_oauth_token(monkeypatch):
    calls = []
    history = {
        "contract_version": "1",
        "request_id": "7f69bfaa-465e-4e74-a13f-169447481317",
        "organization_id": 4,
        "currency": "ARS",
        "target_month": "2026-10",
        "categories": [],
        "history": [],
    }

    def fake_urlopen(request, timeout):
        calls.append(request)
        if request.full_url.endswith("/api/v1/auth/token"):
            return FakeResponse(
                {
                    "access_token": "service-token",
                    "token_type": "Bearer",
                    "expires_in": 3600,
                    "scope": "analytics:read",
                }
            )
        return FakeResponse(history)

    monkeypatch.setattr("app.services.java_client.urlopen", fake_urlopen)
    client = JavaServiceClient(
        "http://java-service:8080",
        "python-client",
        "secret-value",
    )

    first = client.get_prediction_request(4, "2026-10")
    second = client.get_prediction_request(4, "2026-10")

    assert first.organization_id == 4
    assert second.target_month == "2026-10"
    assert len(calls) == 3
    assert calls[0].get_method() == "POST"
    assert calls[0].headers["Content-type"] == "application/x-www-form-urlencoded"
    form = parse_qs(calls[0].data.decode())
    assert form["grant_type"] == ["client_credentials"]
    assert form["client_id"] == ["python-client"]
    assert form["client_secret"] == ["secret-value"]
    assert calls[1].headers["Authorization"] == "Bearer service-token"
    assert "targetMonth=2026-10" in calls[1].full_url