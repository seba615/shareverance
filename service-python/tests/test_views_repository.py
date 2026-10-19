import os
from uuid import uuid4

import psycopg
import pytest

from app.schemas.requests import InternalPredictionRequest
from app.services.views_repository import (
    ViewsRepository,
    ViewsRepositoryError,
)


class FakeCursor:
    def __init__(
        self,
        covered_months: list[str],
        expense_rows: list[tuple[str, int, int]],
        statements: list[tuple[str, tuple | None]],
        fail_on_expenses: bool = False,
    ):
        self._covered_months = covered_months
        self._expense_rows = expense_rows
        self._statements = statements
        self._fail_on_expenses = fail_on_expenses
        self._last_results: list[tuple] = []

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False

    def execute(self, query: str, params: tuple | None = None):
        self._statements.append((query, params))
        if "FROM analytics.v_monthly_coverage" in query:
            self._last_results = [(m,) for m in self._covered_months]
        elif "FROM analytics.v_monthly_expenses" in query:
            if self._fail_on_expenses:
                raise RuntimeError("Simulated query failure")
            self._last_results = list(self._expense_rows)
        else:
            self._last_results = []

    def fetchall(self):
        return list(self._last_results)


class FakeConnection:
    def __init__(
        self,
        covered_months: list[str],
        expense_rows: list[tuple[str, int, int]],
        fail_on_expenses: bool = False,
    ):
        self.covered_months = covered_months
        self.expense_rows = expense_rows
        self.fail_on_expenses = fail_on_expenses
        self.statements: list[tuple[str, tuple | None]] = []
        self.closed = False

    def cursor(self):
        return FakeCursor(
            self.covered_months,
            self.expense_rows,
            self.statements,
            self.fail_on_expenses,
        )

    def close(self):
        self.closed = True


def test_views_repository_builds_contiguous_history_with_explicit_zeros():
    # Hay un hueco entre 2026-01 y 2026-04; el bloque continuo antes de 2026-07 es 2026-04..2026-06
    conn = FakeConnection(
        covered_months=["2026-01", "2026-04", "2026-05", "2026-06"],
        expense_rows=[
            ("2026-01", 10, 99999),  # Fuera del tramo continuo
            ("2026-04", 10, 15000),
            ("2026-05", 20, 30000),  # Categoría 20 inicia en 2026-05; categoría 10 queda en 0
            ("2026-06", 10, 20000),  # Categoría 20 queda en 0 en 2026-06
        ],
    )
    repo = ViewsRepository(
        database_url="postgresql://ignored",
        connection_factory=lambda: conn,
    )

    internal_req = InternalPredictionRequest(
        contract_version="2",
        request_id=uuid4(),
        organization_id=42,
        target_month="2026-07",
    )
    prediction_req = repo.build_prediction_request(internal_req)

    # Verifica transacción de solo lectura, set_config local y COMMIT
    assert conn.statements[0] == ("BEGIN READ ONLY", None)
    assert conn.statements[1] == (
        "SELECT set_config('shareverance.organization_id', %s, true)",
        ("42",),
    )
    assert conn.statements[-1] == ("COMMIT", None)
    assert conn.closed is True

    assert [c.model_dump() for c in prediction_req.categories] == [
        {"category_id": 10, "available_from_month": "2026-04"},
        {"category_id": 20, "available_from_month": "2026-05"},
    ]
    assert [h.model_dump() for h in prediction_req.history] == [
        {
            "month": "2026-04",
            "category_totals": [
                {"category_id": 10, "amount_minor": 15000},
            ],
        },
        {
            "month": "2026-05",
            "category_totals": [
                {"category_id": 10, "amount_minor": 0},
                {"category_id": 20, "amount_minor": 30000},
            ],
        },
        {
            "month": "2026-06",
            "category_totals": [
                {"category_id": 10, "amount_minor": 20000},
                {"category_id": 20, "amount_minor": 0},
            ],
        },
    ]


def test_views_repository_rolls_back_and_closes_on_error():
    conn = FakeConnection(
        covered_months=["2026-06"],
        expense_rows=[],
        fail_on_expenses=True,
    )
    repo = ViewsRepository(
        database_url="postgresql://ignored",
        connection_factory=lambda: conn,
    )

    with pytest.raises(ViewsRepositoryError):
        repo.fetch_organization_views(7)

    assert conn.statements[0] == ("BEGIN READ ONLY", None)
    assert conn.statements[-1] == ("ROLLBACK", None)
    assert conn.closed is True


@pytest.mark.skipif(
    not os.getenv("PYTHON_TEST_PG_URL"),
    reason="Requiere PostgreSQL real configurado en PYTHON_TEST_PG_URL",
)
def test_real_postgres_views_isolation_and_context_cleanup():
    pg_url = os.environ["PYTHON_TEST_PG_URL"]
    conn = psycopg.connect(pg_url, autocommit=True)
    try:
        repo = ViewsRepository(
            database_url=pg_url,
            connection_factory=lambda: conn,
            close_connection=False,
        )

        req_org_1 = repo.build_prediction_request(
            InternalPredictionRequest(
                contract_version="2",
                request_id=uuid4(),
                organization_id=1,
                target_month="2026-04",
            )
        )
        assert [h.month for h in req_org_1.history] == [
            "2026-01",
            "2026-02",
            "2026-03",
        ]

        # Al terminar la transacción, la misma conexión no conserva shareverance.organization_id
        with conn.cursor() as cur:
            cur.execute(
                "SELECT current_setting('shareverance.organization_id', true)"
            )
            setting_after = cur.fetchone()[0]
            assert setting_after in (None, "")

            cur.execute("SELECT COUNT(*) FROM analytics.v_monthly_expenses")
            assert cur.fetchone()[0] == 0
    finally:
        conn.close()

