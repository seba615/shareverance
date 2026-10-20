import os
from uuid import uuid4

import psycopg
import pytest
from fastapi.testclient import TestClient

from app.api.predictions import get_settings, get_views_repository
from app.config import Settings
from app.main import app
from app.schemas.requests import InternalAnalyticsRequest
from app.services.analytics import AnalyticsService, compare_amounts
from app.services.views_repository import ViewsRepository
from app.validation.history import HistoryValidationError

URL = "/internal/v2/analytics/monthly"


def make_request(from_month="2026-01", to_month="2026-03"):
    return InternalAnalyticsRequest(
        contract_version="2",
        request_id=uuid4(),
        organization_id=1,
        from_month=from_month,
        to_month=to_month,
    )


def test_compare_amounts_known_values():
    result = compare_amounts(17000000, 17500000)
    assert result.change_amount_minor == 500000
    assert result.change_percentage == 2.94
    assert result.direction == "INCREASING"

    decrease = compare_amounts(200, 150)
    assert decrease.change_amount_minor == -50
    assert decrease.change_percentage == -25.0
    assert decrease.direction == "DECREASING"

    assert compare_amounts(5000000, 5000000).direction == "UNCHANGED"
    assert compare_amounts(5000000, 5000000).change_percentage == 0.0


def test_compare_amounts_rounds_half_up_and_zero_base():
    assert compare_amounts(8, 9).change_percentage == 12.5
    assert compare_amounts(3, 4).change_percentage == 33.33
    zero_base = compare_amounts(0, 100)
    assert zero_base.change_percentage is None
    assert zero_base.change_amount_minor == 100
    assert zero_base.direction == "INCREASING"
    both_zero = compare_amounts(0, 0)
    assert both_zero.change_percentage is None
    assert both_zero.direction == "UNCHANGED"


def test_analysis_reconciles_totals_with_categories_and_zero_fill():
    response = AnalyticsService().analyze(
        make_request(),
        ["2026-01", "2026-02", "2026-03"],
        [
            ("2026-01", 10, 1000),
            ("2026-02", 10, 1200),
            ("2026-02", 20, 300),
            ("2026-03", 20, 500),
        ],
    )

    assert response.status == "OK"
    assert response.simulated is False
    assert response.comparison.previous_month == "2026-02"
    assert response.comparison.current_month == "2026-03"
    assert response.organization.previous_amount_minor == 1500
    assert response.organization.current_amount_minor == 500
    assert response.organization.direction == "DECREASING"

    by_category = {c.category_id: c for c in response.categories}
    assert list(by_category) == [10, 20]
    assert by_category[10].current_amount_minor == 0
    assert by_category[10].change_percentage == -100.0
    assert by_category[20].change_percentage == 66.67

    for item in response.evolution:
        assert item.covered
        assert item.total_amount_minor == sum(
            c.amount_minor for c in item.category_totals
        )
        assert [c.category_id for c in item.category_totals] == [10, 20]
    assert [e.total_amount_minor for e in response.evolution] == [1000, 1500, 500]
    assert sum(c.previous_amount_minor for c in response.categories) == (
        response.organization.previous_amount_minor
    )


def test_category_new_in_current_month_has_null_percentage():
    response = AnalyticsService().analyze(
        make_request("2026-02", "2026-03"),
        ["2026-02", "2026-03"],
        [("2026-02", 10, 100), ("2026-03", 10, 100), ("2026-03", 20, 50)],
    )
    category = {c.category_id: c for c in response.categories}[20]
    assert category.previous_amount_minor == 0
    assert category.change_percentage is None
    assert category.direction == "INCREASING"


def test_uncovered_month_is_not_zero_and_blocks_comparison():
    response = AnalyticsService().analyze(
        make_request("2026-01", "2026-03"),
        ["2026-01", "2026-03"],
        [("2026-01", 10, 100), ("2026-02", 10, 999), ("2026-03", 10, 100)],
    )
    assert response.status == "INSUFFICIENT_HISTORY"
    assert response.comparison is None
    assert response.organization.change_amount_minor is None
    assert all(c.status == "INSUFFICIENT_HISTORY" for c in response.categories)
    february = response.evolution[1]
    assert february.covered is False
    assert february.total_amount_minor is None
    assert february.category_totals == []


def test_single_month_and_empty_coverage_are_insufficient():
    service = AnalyticsService()
    single = service.analyze(
        make_request("2026-03", "2026-03"), ["2026-03"], [("2026-03", 10, 5)]
    )
    assert single.comparison is None
    assert single.status == "INSUFFICIENT_HISTORY"

    empty = service.analyze(make_request(), [], [])
    assert empty.comparison is None
    assert empty.categories == []
    assert all(not e.covered for e in empty.evolution)


def test_covered_month_without_expenses_is_confirmed_zero():
    response = AnalyticsService().analyze(
        make_request("2026-02", "2026-03"), ["2026-02", "2026-03"], []
    )
    assert response.status == "OK"
    assert response.organization.previous_amount_minor == 0
    assert response.organization.change_percentage is None
    assert response.organization.direction == "UNCHANGED"
    assert response.categories == []


def test_window_validation_rejects_reversed_and_oversized_ranges():
    with pytest.raises(HistoryValidationError):
        AnalyticsService.window(make_request("2026-03", "2026-01"))
    with pytest.raises(HistoryValidationError):
        AnalyticsService.window(make_request("2020-01", "2026-01"))
    assert len(AnalyticsService.window(make_request("2021-02", "2026-01"))) == 60


@pytest.fixture
def client():
    with TestClient(app) as test_client:
        yield test_client


def _settings():
    return Settings(
        predictor_mode="mock",
        database_url="postgresql://localhost/test",
        internal_service_token="active-secret",
        internal_service_token_previous="",
    )


def _body():
    return {
        "contract_version": "2",
        "request_id": "7f69bfaa-465e-4e74-a13f-169447481317",
        "organization_id": 1,
        "from_month": "2026-01",
        "to_month": "2026-03",
    }


def test_internal_analytics_requires_bearer_token(client):
    app.dependency_overrides[get_settings] = _settings
    try:
        assert client.post(URL, json=_body()).status_code == 401
        response = client.post(
            URL, json=_body(), headers={"Authorization": "Bearer wrong"}
        )
        assert response.status_code == 403
    finally:
        app.dependency_overrides.pop(get_settings, None)


def test_internal_analytics_returns_contract_response(client):
    class FakeRepository:
        def fetch_organization_views(self, organization_id):
            assert organization_id == 1
            return (
                ["2026-01", "2026-02", "2026-03"],
                [("2026-02", 10, 200), ("2026-03", 10, 300)],
            )

    app.dependency_overrides[get_settings] = _settings
    app.dependency_overrides[get_views_repository] = lambda: FakeRepository()
    try:
        response = client.post(
            URL, json=_body(), headers={"Authorization": "Bearer active-secret"}
        )
    finally:
        app.dependency_overrides.pop(get_settings, None)
        app.dependency_overrides.pop(get_views_repository, None)

    assert response.status_code == 200
    body = response.json()
    assert body["contract_version"] == "2"
    assert body["organization"]["change_percentage"] == 50.0
    assert body["categories"][0]["category_id"] == 10
    assert body["evolution"][0] == {
        "month": "2026-01",
        "covered": True,
        "total_amount_minor": 0,
        "category_totals": [{"category_id": 10, "amount_minor": 0}],
    }


def test_internal_analytics_rejects_invalid_window_without_reading_views(client):
    class ForbiddenRepository:
        def fetch_organization_views(self, _organization_id):
            raise AssertionError("No debe consultar vistas con ventana inválida")

    app.dependency_overrides[get_settings] = _settings
    app.dependency_overrides[get_views_repository] = lambda: ForbiddenRepository()
    body = _body() | {"from_month": "2026-05"}
    try:
        response = client.post(
            URL, json=body, headers={"Authorization": "Bearer active-secret"}
        )
        extra = client.post(
            URL,
            json=_body() | {"unknown": 1},
            headers={"Authorization": "Bearer active-secret"},
        )
    finally:
        app.dependency_overrides.pop(get_settings, None)
        app.dependency_overrides.pop(get_views_repository, None)
    assert response.status_code == 422 or response.status_code == 400
    assert extra.status_code in (400, 422)


@pytest.mark.skipif(
    not os.getenv("PYTHON_TEST_PG_URL"),
    reason="Requiere PostgreSQL real configurado en PYTHON_TEST_PG_URL",
)
def test_real_postgres_totals_match_direct_view_sums():
    pg_url = os.environ["PYTHON_TEST_PG_URL"]
    conn = psycopg.connect(pg_url, autocommit=True)
    try:
        repo = ViewsRepository(
            database_url=pg_url,
            connection_factory=lambda: conn,
            close_connection=False,
        )
        covered, rows = repo.fetch_organization_views(1)
        window = sorted(covered)[-12:]
        if not window:
            pytest.skip("La base de prueba no tiene meses confirmados")
        response = AnalyticsService().analyze(
            make_request(window[0], window[-1]), covered, rows
        )
        with conn.cursor() as cur:
            cur.execute("BEGIN READ ONLY")
            cur.execute("SELECT set_config('shareverance.organization_id','1',true)")
            cur.execute(
                "SELECT to_char(month,'YYYY-MM'), SUM(amount_minor)::bigint "
                "FROM analytics.v_monthly_expenses GROUP BY month"
            )
            direct = {m: int(t) for m, t in cur.fetchall()}
            cur.execute("COMMIT")
        for item in response.evolution:
            if item.covered:
                assert item.total_amount_minor == direct.get(item.month, 0)
    finally:
        conn.close()

