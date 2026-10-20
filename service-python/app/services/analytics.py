from decimal import ROUND_HALF_UP, Decimal
from typing import Iterable

from app.schemas.requests import InternalAnalyticsRequest
from app.schemas.responses import (
    AnalyticsResponse,
    AnalyticsResult,
    CategoryAnalyticsResult,
    ComparisonPeriod,
    EvolutionCategoryTotal,
    MonthlyEvolution,
)
from app.validation.history import (
    HistoryIssue,
    HistoryValidationError,
    month_index,
)

MAX_WINDOW_MONTHS = 60


def index_to_month(index: int) -> str:
    return f"{index // 12:04d}-{(index % 12) + 1:02d}"


def compare_amounts(previous: int, current: int) -> AnalyticsResult:
    """Aplica las fórmulas del contrato de comparación mensual."""
    change = current - previous
    percentage: float | None = None
    if previous != 0:
        percentage = float(
            (Decimal(change) * 100 / Decimal(previous)).quantize(
                Decimal("0.01"), rounding=ROUND_HALF_UP
            )
        )
    if change > 0:
        direction = "INCREASING"
    elif change < 0:
        direction = "DECREASING"
    else:
        direction = "UNCHANGED"
    return AnalyticsResult(
        status="OK",
        previous_amount_minor=previous,
        current_amount_minor=current,
        change_amount_minor=change,
        change_percentage=percentage,
        direction=direction,
    )


def _insufficient() -> AnalyticsResult:
    return AnalyticsResult(
        status="INSUFFICIENT_HISTORY",
        previous_amount_minor=None,
        current_amount_minor=None,
        change_amount_minor=None,
        change_percentage=None,
        direction=None,
    )


class AnalyticsService:
    """Compara los dos últimos meses de la ventana y concilia totales con categorías."""

    @staticmethod
    def window(request: InternalAnalyticsRequest) -> list[str]:
        try:
            start = month_index(request.from_month)
            end = month_index(request.to_month)
        except ValueError as exc:
            raise HistoryValidationError(
                [HistoryIssue(field="from_month", code="INVALID_VALUE")]
            ) from exc
        if start > end:
            raise HistoryValidationError(
                [HistoryIssue(field="to_month", code="INVALID_VALUE")]
            )
        if end - start + 1 > MAX_WINDOW_MONTHS:
            raise HistoryValidationError(
                [HistoryIssue(field="from_month", code="INVALID_VALUE")]
            )
        return [index_to_month(i) for i in range(start, end + 1)]

    def analyze(
        self,
        request: InternalAnalyticsRequest,
        covered_months: Iterable[str],
        expense_rows: Iterable[tuple[str, int, int]],
    ) -> AnalyticsResponse:
        months = self.window(request)
        covered = set(covered_months) & set(months)

        totals: dict[tuple[str, int], int] = {}
        for month, category_id, amount in expense_rows:
            if month in covered:
                key = (month, category_id)
                totals[key] = totals.get(key, 0) + amount
        category_ids = sorted({cat for (_, cat) in totals})

        evolution = []
        month_total: dict[str, int] = {}
        for month in months:
            if month not in covered:
                evolution.append(
                    MonthlyEvolution(
                        month=month,
                        covered=False,
                        total_amount_minor=None,
                        category_totals=[],
                    )
                )
                continue
            per_category = [
                EvolutionCategoryTotal(
                    category_id=cat, amount_minor=totals.get((month, cat), 0)
                )
                for cat in category_ids
            ]
            month_total[month] = sum(c.amount_minor for c in per_category)
            evolution.append(
                MonthlyEvolution(
                    month=month,
                    covered=True,
                    total_amount_minor=month_total[month],
                    category_totals=per_category,
                )
            )

        previous_month = months[-2] if len(months) >= 2 else None
        current_month = months[-1]
        comparable = previous_month in covered and current_month in covered

        if comparable:
            organization = compare_amounts(
                month_total[previous_month], month_total[current_month]
            )
            categories = [
                CategoryAnalyticsResult(
                    category_id=cat,
                    **compare_amounts(
                        totals.get((previous_month, cat), 0),
                        totals.get((current_month, cat), 0),
                    ).model_dump(),
                )
                for cat in category_ids
            ]
            comparison = ComparisonPeriod(
                previous_month=previous_month, current_month=current_month
            )
            status = "OK"
        else:
            organization = _insufficient()
            categories = [
                CategoryAnalyticsResult(
                    category_id=cat, **_insufficient().model_dump()
                )
                for cat in category_ids
            ]
            comparison = None
            status = "INSUFFICIENT_HISTORY"

        return AnalyticsResponse(
            contract_version="2",
            request_id=request.request_id,
            organization_id=request.organization_id,
            currency="ARS",
            status=status,
            simulated=False,
            comparison=comparison,
            organization=organization,
            categories=categories,
            evolution=evolution,
        )

