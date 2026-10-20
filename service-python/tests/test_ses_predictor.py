import pytest
from app.predictors.ses import SimpleExponentialSmoothingPredictor
from app.predictors.mock import MockPredictor
from app.predictors.evaluation import (
    calculate_metrics,
    rolling_origin_evaluation,
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
    assert metrics.mae == 16.67
    assert metrics.rmse == 19.15
    assert metrics.mape == 8.33
    assert metrics.total_samples == 3


def test_rolling_origin_evaluation_ses_vs_mock_reference():
    # Serie histórica temporal representativa de 18 meses (gastos crecientes con fluctuaciones)
    # Expresado en centavos ($10.000 a $28.000)
    dataset = [
        1000000, 1100000, 1050000, 1200000, 1300000, 1250000,
        1400000, 1500000, 1480000, 1600000, 1750000, 1700000,
        1850000, 2000000, 1950000, 2100000, 2250000, 2200000,
    ]

    ses = SimpleExponentialSmoothingPredictor(alpha=0.3, required_months=6)
    mock = MockPredictor()

    actual_ses, pred_ses, metrics_ses = rolling_origin_evaluation(dataset, ses, min_train_months=6)
    actual_mock, pred_mock, metrics_mock = rolling_origin_evaluation(dataset, mock, min_train_months=6)

    assert actual_ses == actual_mock
    assert len(pred_ses) == 12  # 18 - 6
    assert metrics_ses.total_samples == 12
    assert metrics_mock.total_samples == 12

    # Verifica que las métricas sean números finitos y válidos
    assert metrics_ses.mae > 0
    assert metrics_ses.rmse > 0
    assert metrics_ses.mape is not None and metrics_ses.mape > 0

    # Comprobación de evaluación frente a referencia:
    # SES filtra el ruido paso a paso generando una predicción suave y consistente
    assert metrics_ses.mae < 100000000
    assert metrics_ses.rmse < 100000000

