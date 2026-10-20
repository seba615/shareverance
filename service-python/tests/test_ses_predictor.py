import pytest
import json
from pathlib import Path
from app.predictors.ses import SimpleExponentialSmoothingPredictor
from app.predictors.mock import MockPredictor
from app.predictors.evaluation import (
    calculate_metrics,
    rolling_origin_evaluation,
    compare_candidates,
    HistoricalMeanReference,
)
from app.schemas.requests import MAX_SAFE_INTEGER


def test_ses_predictor_metadata():
    predictor = SimpleExponentialSmoothingPredictor(alpha=0.3, required_months=6)
    assert predictor.method == "simple_exponential_smoothing"
    assert predictor.model_version == "1"
    assert predictor.required_months == 6
    assert predictor.simulated is False
    assert predictor.alpha == 0.3


def test_ses_predictor_validates_parameters():
    with pytest.raises(ValueError, match="alpha"):
        SimpleExponentialSmoothingPredictor(alpha=0.0)
    with pytest.raises(ValueError, match="alpha"):
        SimpleExponentialSmoothingPredictor(alpha=1.5)
    with pytest.raises(ValueError, match="required_months"):
        SimpleExponentialSmoothingPredictor(required_months=0)


def test_ses_predictor_requires_minimum_months():
    predictor = SimpleExponentialSmoothingPredictor(alpha=0.3, required_months=6)
    with pytest.raises(ValueError, match="al menos 6 meses"):
        predictor.predict([1000, 2000, 3000, 4000, 5000])


def test_ses_predictor_manual_calculation():
    # alpha = 0.5
    # y = [10000, 12000, 14000, 16000, 18000, 20000]
    # S_1 = 10000
    # S_2 = 0.5 * 12000 + 0.5 * 10000 = 11000
    # S_3 = 0.5 * 14000 + 0.5 * 11000 = 12500
    # S_4 = 0.5 * 16000 + 0.5 * 12500 = 14250
    # S_5 = 0.5 * 18000 + 0.5 * 14250 = 16125
    # S_6 = 0.5 * 20000 + 0.5 * 16125 = 18062.5 -> 18063 (ROUND_HALF_UP)
    predictor = SimpleExponentialSmoothingPredictor(alpha=0.5, required_months=6)
    series = [10000, 12000, 14000, 16000, 18000, 20000]
    assert predictor.predict(series) == 18063


def test_ses_constant_series_preserves_constant_value():
    predictor = SimpleExponentialSmoothingPredictor(alpha=0.3, required_months=6)
    series = [50000] * 12
    assert predictor.predict(series) == 50000


def test_ses_alpha_one_equals_last_observed():
    predictor = SimpleExponentialSmoothingPredictor(alpha=1.0, required_months=6)
    series = [1000, 2000, 3000, 4000, 5000, 6000]
    assert predictor.predict(series) == 6000


def test_ses_rejects_invalid_elements():
    predictor = SimpleExponentialSmoothingPredictor(alpha=0.3, required_months=6)
    with pytest.raises(ValueError, match="enteros válidos"):
        predictor.predict([1000, 2000, 3000, 4000, 5000, -100])
    with pytest.raises(ValueError, match="enteros válidos"):
        predictor.predict([1000, 2000, 3000, 4000, 5000, MAX_SAFE_INTEGER + 1])


def test_calculate_metrics():
    actual = [100, 200, 300]
    predicted = [110, 190, 330]
    # abs errors: 10, 10, 30 -> MAE = 50 / 3 = 16.67
    # sq errors: 100, 100, 900 -> MSE = 1100 / 3 = 366.67 -> RMSE = 19.15
    # pct errors: 10/100=0.1, 10/200=0.05, 30/300=0.1 -> avg = 0.25 / 3 = 8.33%
    metrics = calculate_metrics(actual, predicted)
    assert metrics.mae_ars == 0.17
    assert metrics.rmse_ars == 0.19
    assert metrics.mape_percentage == 8.33
    assert metrics.wape_percentage == 8.33
    assert metrics.total_samples == 3


def test_rolling_origin_evaluation_ses_vs_mock_reference():
    # Fixture sintético de regresión; no es el dataset de aceptación.
    # Expresado en centavos ($10.000 a $22.500).
    dataset = json.loads((Path(__file__).parent / "fixtures/prediction_synthetic.json").read_text())["amounts_minor"]

    ses = SimpleExponentialSmoothingPredictor(alpha=0.3, required_months=6)
    mock = MockPredictor()

    actual_ses, pred_ses, metrics_ses = rolling_origin_evaluation(dataset, ses, min_train_months=6)
    actual_mock, pred_mock, metrics_mock = rolling_origin_evaluation(dataset, mock, min_train_months=6)

    assert actual_ses == actual_mock
    assert len(pred_ses) == 12  # 18 - 6
    assert metrics_ses.total_samples == 12
    assert metrics_mock.total_samples == 12

    comparison = compare_candidates(dataset)
    assert comparison["ses"] == metrics_ses
    assert comparison["last_month"] == metrics_mock
    assert comparison["historical_mean"].total_samples == 12
    assert metrics_ses.mae_ars == 2460.04
    assert metrics_mock.mae_ars == 1075.00
    # Este candidato pierde ante último mes en esta serie: no se promueve.
    assert metrics_ses.mae_ars > metrics_mock.mae_ars


def test_zero_months_contribute_to_wape_and_all_zero_denominator_is_explicit():
    metrics = calculate_metrics([0, 100], [100, 100])
    assert metrics.mae_ars == 0.50
    assert metrics.mape_percentage == 0
    assert metrics.mape_samples == 1
    assert metrics.wape_percentage == 100
    all_zero = calculate_metrics([0, 0], [100, 0])
    assert all_zero.mae_ars == 0.50
    assert all_zero.wape_percentage is None
    assert all_zero.mape_percentage is None
    assert all_zero.mape_samples == 0


def test_historical_mean_uses_half_up_and_only_observed_training_values():
    reference = HistoricalMeanReference()
    assert reference.predict([100, 100, 100, 101, 101, 101]) == 101
    actual, predicted, _ = rolling_origin_evaluation([100] * 6 + [10000, 20000], reference)
    assert actual == [10000, 20000]
    assert predicted == [100, 1514]


@pytest.mark.parametrize('actual,predicted', [([], []), ([100], []), ([True], [1]), ([-1], [0]), ([0], [1.5]), ([0], [MAX_SAFE_INTEGER + 1])])
def test_metrics_reject_invalid_inputs(actual, predicted):
    with pytest.raises(ValueError):
        calculate_metrics(actual, predicted)


@pytest.mark.parametrize('months', [0, -1, 5, True, 6.5])
def test_evaluation_rejects_invalid_training_window(months):
    with pytest.raises(ValueError):
        rolling_origin_evaluation([100] * 10, MockPredictor(), months)


def test_future_targets_never_enter_training_window():
    class RecordingPredictor(MockPredictor):
        def __init__(self): self.windows = []
        def predict(self, amounts):
            self.windows.append(list(amounts))
            return super().predict(amounts)
    predictor = RecordingPredictor()
    rolling_origin_evaluation([1, 2, 3, 4, 5, 6, 1000, 2000], predictor)
    assert predictor.windows == [[1, 2, 3, 4, 5, 6], [1, 2, 3, 4, 5, 6, 1000]]

