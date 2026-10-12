import pytest

from app.predictors.mock import MockPredictor
from app.schemas.requests import PredictionRequest
from app.services.predictions import PredictionService
from app.validation.history import HistoryValidationError


def make_request(
    months: int = 6,
    recent_category: bool = False,
) -> PredictionRequest:
    history = []

    for month in range(1, months + 1):
        totals = [
            {
                "category_id": 1,
                "amount_minor": month * 10000,
            }
        ]

        if recent_category and month >= 5:
            totals.append(
                {
                    "category_id": 2,
                    "amount_minor": 2000,
                }
            )

        history.append(
            {
                "month": f"2026-{month:02d}",
                "category_totals": totals,
            }
        )

    categories = [
        {
            "category_id": 1,
            "available_from_month": "2026-01",
        }
    ]

    if recent_category:
        categories.append(
            {
                "category_id": 2,
                "available_from_month": "2026-05",
            }
        )

    return PredictionRequest.model_validate(
        {
            "contract_version": "1",
            "request_id": "7f69bfaa-465e-4e74-a13f-169447481317",
            "organization_id": 1,
            "currency": "ARS",
            "target_month": f"2026-{months + 1:02d}",
            "categories": categories,
            "history": history,
        }
    )


@pytest.fixture
def service():
    return PredictionService(MockPredictor())


def test_mock_returns_last_amount(service):
    response = service.predict(make_request())

    assert response.status == "OK"
    assert response.simulated is True
    assert response.organization.estimated_amount_minor == 60000
    assert response.organization.months_used == 6
    assert response.organization.method == "mock"
    assert response.categories[0].estimated_amount_minor == 60000


def test_insufficient_history_returns_null(service):
    response = service.predict(make_request(months=2))

    assert response.status == "INSUFFICIENT_HISTORY"
    assert response.organization.estimated_amount_minor is None
    assert response.organization.months_used == 0
    assert response.organization.required_months == 6
    assert response.categories[0].estimated_amount_minor is None


def test_recent_category_produces_partial_response(service):
    response = service.predict(
        make_request(recent_category=True)
    )

    assert response.status == "PARTIAL"

    # El total general incluye ambas categorías.
    assert response.organization.estimated_amount_minor == 62000

    results = {
        result.category_id: result
        for result in response.categories
    }

    assert results[1].status == "OK"
    assert results[1].estimated_amount_minor == 60000
    assert results[2].status == "INSUFFICIENT_HISTORY"
    assert results[2].estimated_amount_minor is None


def test_response_preserves_request_identifiers(service):
    request = make_request()
    response = service.predict(request)

    assert response.request_id == request.request_id
    assert response.organization_id == request.organization_id
    assert response.currency == request.currency
    assert response.target_month == request.target_month
    assert response.contract_version == request.contract_version


def test_zero_history_produces_zero_estimate(service):
    request = make_request()

    for month in request.history:
        for total in month.category_totals:
            total.amount_minor = 0

    response = service.predict(request)

    assert response.status == "OK"
    assert response.organization.estimated_amount_minor == 0
    assert response.categories[0].estimated_amount_minor == 0


def test_empty_history_is_insufficient(service):
    request = make_request()
    request.history = []
    request.categories = []

    response = service.predict(request)

    assert response.status == "INSUFFICIENT_HISTORY"
    assert response.organization.estimated_amount_minor is None
    assert response.categories == []


def test_invalid_history_is_rejected_before_prediction(service):
    request = make_request()
    request.target_month = "2026-09"

    with pytest.raises(HistoryValidationError):
        service.predict(request)


class DifferentEstimatePredictor(MockPredictor):
    """Predictor de prueba para comprobar la independencia."""

    def predict(self, amounts):
        return amounts[-1] + 100


def test_general_prediction_is_independent():
    service = PredictionService(DifferentEstimatePredictor())

    # Ocho meses: ambas categorías tienen historial suficiente.
    request = make_request(months=8, recent_category=True)

    # Ampliamos la cobertura de la segunda categoría a enero.
    request.categories[1].available_from_month = "2026-01"

    for month in request.history[:4]:
        month.category_totals.append(
            type(month.category_totals[0])(
                category_id=2,
                amount_minor=2000,
            )
        )

    response = service.predict(request)

    assert response.status == "OK"
    assert response.organization.estimated_amount_minor == 82100

    category_sum = sum(
        result.estimated_amount_minor
        for result in response.categories
    )

    assert category_sum == 82200
    assert response.organization.estimated_amount_minor != category_sum