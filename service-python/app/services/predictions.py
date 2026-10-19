from app.predictors.base import Predictor
from app.schemas.requests import (
    MAX_SAFE_INTEGER,
    PredictionRequest,
)
from app.schemas.responses import (
    CategoryPredictionResult,
    OverallStatus,
    PredictionResponse,
    PredictionResult,
)
from app.validation.history import validate_history


class PredictionService:
    """Coordina las predicciones generales y por categoría."""

    def __init__(self, predictor: Predictor):
        self.predictor = predictor

    def _predict_series(
        self,
        amounts: list[int],
    ) -> PredictionResult:
        required = self.predictor.required_months

        if len(amounts) < required:
            return PredictionResult(
                status="INSUFFICIENT_HISTORY",
                estimated_amount_minor=None,
                months_used=0,
                required_months=required,
                method=self.predictor.method,
                model_version=self.predictor.model_version,
            )

        estimate = self.predictor.predict(amounts)

        # Un resultado inválido es un fallo del predictor,
        # no falta de historial.
        if (
            type(estimate) is not int
            or not 0 <= estimate <= MAX_SAFE_INTEGER
        ):
            raise RuntimeError(
                "El predictor devolvió una estimación inválida."
            )

        return PredictionResult(
            status="OK",
            estimated_amount_minor=estimate,
            months_used=len(amounts),
            required_months=required,
            method=self.predictor.method,
            model_version=self.predictor.model_version,
        )

    @staticmethod
    def _overall_status(
        results: list[PredictionResult],
    ) -> OverallStatus:
        successful = sum(
            result.status == "OK"
            for result in results
        )

        if successful == len(results):
            return "OK"

        if successful == 0:
            return "INSUFFICIENT_HISTORY"

        return "PARTIAL"

    def predict(
        self,
        request: PredictionRequest,
        contract_version: str | None = None,
    ) -> PredictionResponse:
        validate_history(request)

        organization_amounts: list[int] = []

        category_amounts: dict[int, list[int]] = {
            category.category_id: []
            for category in request.categories
        }

        # El historial ya está validado y ordenado.
        # Las categorías aparecen desde su cobertura.
        for month in request.history:
            organization_amounts.append(
                sum(
                    total.amount_minor
                    for total in month.category_totals
                )
            )

            for total in month.category_totals:
                category_amounts[total.category_id].append(
                    total.amount_minor
                )

        organization_result = self._predict_series(
            organization_amounts
        )

        category_results: list[CategoryPredictionResult] = []

        for category in request.categories:
            result = self._predict_series(
                category_amounts[category.category_id]
            )

            category_results.append(
                CategoryPredictionResult(
                    category_id=category.category_id,
                    **result.model_dump(),
                )
            )

        all_results: list[PredictionResult] = [
            organization_result,
            *category_results,
        ]

        return PredictionResponse(
            contract_version=contract_version or request.contract_version,
            request_id=request.request_id,
            organization_id=request.organization_id,
            currency=request.currency,
            target_month=request.target_month,
            status=self._overall_status(all_results),
            simulated=self.predictor.simulated,
            organization=organization_result,
            categories=category_results,
        )