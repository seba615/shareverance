from dataclasses import dataclass

from app.schemas.errors import DetailCode
from app.schemas.requests import (
    MAX_SAFE_INTEGER,
    HistoryRequest,
    PredictionRequest,
)


@dataclass(frozen=True)
class HistoryIssue:
    field: str
    code: DetailCode


class HistoryValidationError(Exception):
    """Solicitud estructuralmente válida con historial inconsistente."""

    def __init__(self, issues: list[HistoryIssue]):
        super().__init__("El historial no cumple el contrato.")
        self.issues = issues


def month_index(month: str) -> int:
    """Convierte un mes válido en un número para comparar distancias."""

    year, number = map(int, month.split("-"))

    if not 1 <= year <= 9999 or not 1 <= number <= 12:
        raise ValueError("Mes calendario inválido.")

    return year * 12 + number - 1


def validate_history(request: HistoryRequest) -> None:
    issues: list[HistoryIssue] = []

    def add(field: str, code: DetailCode) -> None:
        issues.append(HistoryIssue(field=field, code=code))

    def finish() -> None:
        if issues:
            raise HistoryValidationError(issues)

    # Pydantic comprueba el formato; aquí comprobamos el año calendario.
    for i, entry in enumerate(request.history):
        try:
            month_index(entry.month)
        except ValueError:
            add(f"history[{i}].month", "INVALID_VALUE")

    for i, category in enumerate(request.categories):
        try:
            month_index(category.available_from_month)
        except ValueError:
            add(
                f"categories[{i}].available_from_month",
                "INVALID_VALUE",
            )

    if isinstance(request, PredictionRequest):
        try:
            month_index(request.target_month)
        except ValueError:
            add("target_month", "INVALID_VALUE")

    # No seguimos comparando fechas que ya son inválidas.
    finish()

    category_ids = [
        category.category_id
        for category in request.categories
    ]

    if len(category_ids) != len(set(category_ids)):
        add("categories", "DUPLICATE_CATEGORY")

    if category_ids != sorted(category_ids):
        add("categories", "INVALID_VALUE")

    months = [entry.month for entry in request.history]
    indices = [month_index(month) for month in months]

    if len(months) != len(set(months)):
        add("history", "DUPLICATE_MONTH")

    if indices != sorted(indices):
        add("history", "UNSORTED_HISTORY")

    # Evitamos errores secundarios si hay duplicados o desorden.
    finish()

    if any(
        current - previous != 1
        for previous, current in zip(indices, indices[1:])
    ):
        add("history", "NON_CONSECUTIVE_MONTHS")

    if not request.history:
        if request.categories:
            add("categories", "INVALID_COVERAGE")

        finish()
        return

    first_month = indices[0]
    last_month = indices[-1]

    coverage = {
        category.category_id: month_index(
            category.available_from_month
        )
        for category in request.categories
    }

    for i, category in enumerate(request.categories):
        start = coverage[category.category_id]

        if not first_month <= start <= last_month:
            add(
                f"categories[{i}].available_from_month",
                "INVALID_COVERAGE",
            )

    finish()

    for i, entry in enumerate(request.history):
        current_month = indices[i]
        totals = entry.category_totals
        ids = [total.category_id for total in totals]
        actual_ids = set(ids)

        if len(ids) != len(actual_ids):
            add(
                f"history[{i}].category_totals",
                "DUPLICATE_CATEGORY",
            )

        if ids != sorted(ids):
            add(
                f"history[{i}].category_totals",
                "INVALID_VALUE",
            )

        for j, total in enumerate(totals):
            field = (
                f"history[{i}].category_totals[{j}].category_id"
            )

            if total.category_id not in coverage:
                add(field, "UNKNOWN_CATEGORY")
            elif current_month < coverage[total.category_id]:
                add(field, "INVALID_COVERAGE")

        expected_ids = {
            category_id
            for category_id, start in coverage.items()
            if start <= current_month
        }

        if expected_ids - actual_ids:
            add(
                f"history[{i}].category_totals",
                "MISSING_CATEGORY_TOTAL",
            )

        monthly_total = sum(
            total.amount_minor
            for total in totals
        )

        if monthly_total > MAX_SAFE_INTEGER:
            add(
                f"history[{i}].category_totals",
                "AMOUNT_OUT_OF_RANGE",
            )

    if isinstance(request, PredictionRequest):
        if month_index(request.target_month) != last_month + 1:
            add("target_month", "INVALID_TARGET_MONTH")

    finish()