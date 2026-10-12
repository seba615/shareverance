from copy import deepcopy

import pytest
from pydantic import ValidationError

from app.schemas.requests import PredictionRequest
from app.validation.history import (
    HistoryValidationError,
    validate_history,
)


@pytest.fixture
def valid_payload():
    """Historial pequeño: válido aunque insuficiente para predecir."""

    return {
        "contract_version": "1",
        "request_id": "7f69bfaa-465e-4e74-a13f-169447481317",
        "organization_id": 1,
        "currency": "ARS",
        "target_month": "2026-03",
        "categories": [
            {
                "category_id": 1,
                "available_from_month": "2026-01",
            }
        ],
        "history": [
            {
                "month": "2026-01",
                "category_totals": [
                    {"category_id": 1, "amount_minor": 10000}
                ],
            },
            {
                "month": "2026-02",
                "category_totals": [
                    {"category_id": 1, "amount_minor": 12000}
                ],
            },
        ],
    }


def assert_history_error(payload, expected_code):
    request = PredictionRequest.model_validate(payload)

    with pytest.raises(HistoryValidationError) as exc:
        validate_history(request)

    codes = {issue.code for issue in exc.value.issues}
    assert expected_code in codes


def test_valid_history_is_accepted(valid_payload):
    request = PredictionRequest.model_validate(valid_payload)

    validate_history(request)


def test_zero_amount_is_accepted(valid_payload):
    valid_payload["history"][0]["category_totals"][0][
        "amount_minor"
    ] = 0

    request = PredictionRequest.model_validate(valid_payload)
    validate_history(request)


@pytest.mark.parametrize("amount", [-1, 100.0, "100", True])
def test_invalid_amount_type_or_value_is_rejected(
    valid_payload,
    amount,
):
    valid_payload["history"][0]["category_totals"][0][
        "amount_minor"
    ] = amount

    with pytest.raises(ValidationError):
        PredictionRequest.model_validate(valid_payload)


def test_category_name_is_rejected(valid_payload):
    valid_payload["categories"][0]["name"] = "Alimentos"

    with pytest.raises(ValidationError):
        PredictionRequest.model_validate(valid_payload)


def test_duplicate_month_is_rejected(valid_payload):
    valid_payload["history"][1]["month"] = "2026-01"

    assert_history_error(valid_payload, "DUPLICATE_MONTH")


def test_missing_month_is_rejected(valid_payload):
    valid_payload["history"][1]["month"] = "2026-03"
    valid_payload["target_month"] = "2026-04"

    assert_history_error(
        valid_payload,
        "NON_CONSECUTIVE_MONTHS",
    )


def test_unsorted_history_is_rejected(valid_payload):
    valid_payload["history"].reverse()

    assert_history_error(valid_payload, "UNSORTED_HISTORY")


def test_missing_category_total_is_rejected(valid_payload):
    valid_payload["history"][1]["category_totals"] = []

    assert_history_error(
        valid_payload,
        "MISSING_CATEGORY_TOTAL",
    )


def test_unknown_category_is_rejected(valid_payload):
    valid_payload["history"][1]["category_totals"][0][
        "category_id"
    ] = 99

    assert_history_error(valid_payload, "UNKNOWN_CATEGORY")


def test_duplicate_category_total_is_rejected(valid_payload):
    totals = valid_payload["history"][0]["category_totals"]
    totals.append(deepcopy(totals[0]))

    assert_history_error(valid_payload, "DUPLICATE_CATEGORY")


def test_category_before_coverage_is_rejected(valid_payload):
    valid_payload["categories"][0][
        "available_from_month"
    ] = "2026-02"

    assert_history_error(valid_payload, "INVALID_COVERAGE")


def test_wrong_target_month_is_rejected(valid_payload):
    valid_payload["target_month"] = "2026-04"

    assert_history_error(valid_payload, "INVALID_TARGET_MONTH")


def test_year_zero_is_rejected(valid_payload):
    valid_payload["history"][0]["month"] = "0000-01"

    assert_history_error(valid_payload, "INVALID_VALUE")


def test_empty_history_is_accepted(valid_payload):
    valid_payload["history"] = []
    valid_payload["categories"] = []

    request = PredictionRequest.model_validate(valid_payload)
    validate_history(request)
    