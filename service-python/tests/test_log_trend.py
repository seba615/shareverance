from decimal import Decimal

import pytest

from app.predictors.log_trend import LogTrendPredictor
from app.predictors.ses import SimpleExponentialSmoothingPredictor
from app.schemas.requests import MAX_SAFE_INTEGER


def test_exact_exponential_growth_from_single_fit():
    model = LogTrendPredictor()
    # log(1+y) es exactamente lineal; el nivel no es constante.
    history = [2 ** i - 1 for i in range(10, 22)]
    assert model.forecast(history, 6) == [2 ** i - 1 for i in range(22, 28)]
    assert model.predict(history) == model.forecast(history, 6)[0]
    assert not model.simulated


def test_decline_and_nonnegative_values():
    history = [2 ** i - 1 for i in range(22, 10, -1)]
    assert LogTrendPredictor().forecast(history, 6) == [2 ** i - 1 for i in range(10, 4, -1)]


@pytest.mark.parametrize('amount', [0, 12345, MAX_SAFE_INTEGER])
def test_constant_including_zero_and_monetary_limit(amount):
    assert LogTrendPredictor().forecast([amount] * 6, 6) == [amount] * 6


@pytest.mark.parametrize('invalid', [True, -1, 1.5, Decimal(1), MAX_SAFE_INTEGER + 1])
def test_invalid_inputs_rejected(invalid):
    with pytest.raises(ValueError):
        LogTrendPredictor().predict([100] * 5 + [invalid])


def test_history_horizon_and_explosive_extrapolation():
    model = LogTrendPredictor()
    with pytest.raises(ValueError):
        model.predict([1] * 5)
    for h in (0, 7, True):
        with pytest.raises(ValueError):
            model.forecast([100] * 6, h)
    with pytest.raises(RuntimeError):
        model.forecast([0, 0, 0, 0, 0, MAX_SAFE_INTEGER], 6)


def test_legacy_forecast_behavior_unchanged():
    ses = SimpleExponentialSmoothingPredictor()
    history = [100, 150, 200, 250, 300, 350]
    assert ses.forecast(history, 6) == [ses.predict(history)] * 6


def test_factory_and_default_select_trend_without_silent_mock(monkeypatch):
    from app.config import load_settings
    from app.predictors.factory import create_predictor
    monkeypatch.delenv('PREDICTOR_MODE', raising=False)
    assert isinstance(create_predictor(load_settings()), LogTrendPredictor)
    monkeypatch.setenv('PREDICTOR_MODE', 'unknown')
    with pytest.raises(ValueError):
        load_settings()
