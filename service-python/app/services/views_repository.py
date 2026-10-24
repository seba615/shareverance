from collections.abc import Callable
from typing import Any

import psycopg

from app.schemas.requests import (
    CategoryCoverage,
    CategoryTotal,
    InternalForecastRequest,
    ForecastRequest,
    InternalPredictionRequest,
    MonthlyHistory,
    PredictionRequest,
)
from app.validation.history import (
    HistoryIssue,
    HistoryValidationError,
    month_index,
)

MAX_HISTORY_MONTHS = 60


class ViewsRepositoryError(Exception):
    """Error al consultar las vistas SQL autorizadas en PostgreSQL."""


def index_to_month(index: int) -> str:
    year = index // 12
    month = (index % 12) + 1
    return f"{year:04d}-{month:02d}"


def _normalize_month(value: Any) -> str:
    if hasattr(value, "strftime"):
        return value.strftime("%Y-%m")
    text = str(value).strip()
    return text[:7] if len(text) >= 7 else text


class ViewsRepository:
    """Lee las vistas SQL autorizadas acotadas por shareverance.organization_id."""

    def __init__(
        self,
        database_url: str,
        connect_timeout_seconds: int = 3,
        connection_factory: Callable[[], Any] | None = None,
        close_connection: bool = True,
    ):
        self.database_url = database_url
        self.connect_timeout_seconds = connect_timeout_seconds
        self._connection_factory = connection_factory
        self._close_connection = close_connection

    def _open_connection(self) -> Any:
        if self._connection_factory is not None:
            return self._connection_factory()
        return psycopg.connect(
            self.database_url,
            autocommit=True,
            connect_timeout=self.connect_timeout_seconds,
        )

    def fetch_organization_views(
        self,
        organization_id: int,
    ) -> tuple[list[str], list[tuple[str, int, int]]]:
        """Ejecuta la lectura dentro de BEGIN READ ONLY + set_config(..., true) y cierra la transacción."""
        try:
            conn = self._open_connection()
        except Exception as exc:
            raise ViewsRepositoryError(
                "No se pudo conectar a PostgreSQL para leer las vistas."
            ) from exc

        in_transaction = False
        try:
            with conn.cursor() as cur:
                cur.execute("BEGIN READ ONLY")
                in_transaction = True
                cur.execute(
                    "SELECT set_config('shareverance.organization_id', %s, true)",
                    (str(organization_id),),
                )
                cur.execute(
                    "SELECT month FROM analytics.v_monthly_coverage ORDER BY month ASC"
                )
                covered_months = [
                    _normalize_month(row[0]) for row in cur.fetchall()
                ]

                cur.execute(
                    "SELECT month, category_id, amount_minor "
                    "FROM analytics.v_monthly_expenses "
                    "ORDER BY month ASC, category_id ASC"
                )
                expense_rows = [
                    (_normalize_month(row[0]), int(row[1]), int(row[2]))
                    for row in cur.fetchall()
                ]

                cur.execute("COMMIT")
                in_transaction = False
                return covered_months, expense_rows
        except Exception as exc:
            if in_transaction:
                try:
                    with conn.cursor() as rollback_cur:
                        rollback_cur.execute("ROLLBACK")
                except Exception:
                    pass
            raise ViewsRepositoryError(
                "Fallo al consultar las vistas analíticas de PostgreSQL."
            ) from exc
        finally:
            if self._close_connection:
                try:
                    conn.close()
                except Exception:
                    pass

    def build_prediction_request(
        self,
        internal_request: InternalPredictionRequest,
    ) -> PredictionRequest:
        try:
            target_idx = month_index(internal_request.target_month)
        except ValueError as exc:
            raise HistoryValidationError(
                [HistoryIssue(field="target_month", code="INVALID_VALUE")]
            ) from exc

        covered_months, expense_rows = self.fetch_organization_views(
            internal_request.organization_id
        )

        covered_set = set(covered_months)
        contiguous_Descending: list[str] = []
        cursor_idx = target_idx - 1

        while cursor_idx >= 0 and len(contiguous_Descending) < MAX_HISTORY_MONTHS:
            candidate_month = index_to_month(cursor_idx)
            if candidate_month not in covered_set:
                break
            contiguous_Descending.append(candidate_month)
            cursor_idx -= 1

        contiguous_months = list(reversed(contiguous_Descending))

        if not contiguous_months:
            return PredictionRequest(
                contract_version="1",
                request_id=internal_request.request_id,
                organization_id=internal_request.organization_id,
                currency="ARS",
                target_month=internal_request.target_month,
                categories=[],
                history=[],
            )

        contiguous_set = set(contiguous_months)
        available_from: dict[int, str] = {}
        totals_by_month_cat: dict[tuple[str, int], int] = {}

        for month_str, category_id, amount_minor in expense_rows:
            if month_str not in contiguous_set:
                continue
            if (
                category_id not in available_from
                or month_str < available_from[category_id]
            ):
                available_from[category_id] = month_str
            totals_by_month_cat[(month_str, category_id)] = (
                totals_by_month_cat.get((month_str, category_id), 0)
                + amount_minor
            )

        sorted_category_ids = sorted(available_from.keys())
        categories = [
            CategoryCoverage(
                category_id=cat_id,
                available_from_month=available_from[cat_id],
            )
            for cat_id in sorted_category_ids
        ]

        history = [
            MonthlyHistory(
                month=month_str,
                category_totals=[
                    CategoryTotal(
                        category_id=cat_id,
                        amount_minor=totals_by_month_cat.get(
                            (month_str, cat_id), 0
                        ),
                    )
                    for cat_id in sorted_category_ids
                    if month_str >= available_from[cat_id]
                ],
            )
            for month_str in contiguous_months
        ]

        return PredictionRequest(
            contract_version="1",
            request_id=internal_request.request_id,
            organization_id=internal_request.organization_id,
            currency="ARS",
            target_month=internal_request.target_month,
            categories=categories,
            history=history,
        )

    def build_forecast_request(
        self,
        internal_request: InternalForecastRequest,
    ) -> ForecastRequest:
        try:
            origin_idx = month_index(internal_request.origin_month)
        except ValueError as exc:
            raise HistoryValidationError(
                [HistoryIssue(field="origin_month", code="INVALID_VALUE")]
            ) from exc

        covered_months, expense_rows = self.fetch_organization_views(
            internal_request.organization_id
        )

        covered_set = set(covered_months)
        contiguous_Descending: list[str] = []
        cursor_idx = origin_idx

        while cursor_idx >= 0 and len(contiguous_Descending) < MAX_HISTORY_MONTHS:
            candidate_month = index_to_month(cursor_idx)
            if candidate_month not in covered_set:
                break
            contiguous_Descending.append(candidate_month)
            cursor_idx -= 1

        contiguous_months = list(reversed(contiguous_Descending))

        if not contiguous_months or contiguous_months[-1] != internal_request.origin_month:
            return ForecastRequest(
                contract_version="1",
                request_id=internal_request.request_id,
                organization_id=internal_request.organization_id,
                currency="ARS",
                origin_month=internal_request.origin_month,
                horizon_months=internal_request.horizon_months,
                categories=[],
                history=[],
            )

        contiguous_set = set(contiguous_months)
        available_from: dict[int, str] = {}
        totals_by_month_cat: dict[tuple[str, int], int] = {}

        for month_str, category_id, amount_minor in expense_rows:
            if month_str not in contiguous_set:
                continue
            if (
                category_id not in available_from
                or month_str < available_from[category_id]
            ):
                available_from[category_id] = month_str
            totals_by_month_cat[(month_str, category_id)] = (
                totals_by_month_cat.get((month_str, category_id), 0)
                + amount_minor
            )

        sorted_category_ids = sorted(available_from.keys())
        categories = [
            CategoryCoverage(
                category_id=cat_id,
                available_from_month=available_from[cat_id],
            )
            for cat_id in sorted_category_ids
        ]

        history = [
            MonthlyHistory(
                month=month_str,
                category_totals=[
                    CategoryTotal(
                        category_id=cat_id,
                        amount_minor=totals_by_month_cat.get(
                            (month_str, cat_id), 0
                        ),
                    )
                    for cat_id in sorted_category_ids
                    if month_str >= available_from[cat_id]
                ],
            )
            for month_str in contiguous_months
        ]

        return ForecastRequest(
            contract_version="1",
            request_id=internal_request.request_id,
            organization_id=internal_request.organization_id,
            currency="ARS",
            origin_month=internal_request.origin_month,
            horizon_months=internal_request.horizon_months,
            categories=categories,
            history=history,
        )
