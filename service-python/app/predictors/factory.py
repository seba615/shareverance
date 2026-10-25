from app.config import Settings
from app.predictors.base import Predictor
from app.predictors.mock import MockPredictor
from app.predictors.ses import SimpleExponentialSmoothingPredictor
from app.predictors.log_trend import LogTrendPredictor


def create_predictor(settings: Settings) -> Predictor:
    if settings.predictor_mode == "log_trend":
        return LogTrendPredictor()
    if settings.predictor_mode == "mock":
        return MockPredictor()
    if settings.predictor_mode == "ses":
        return SimpleExponentialSmoothingPredictor(alpha=0.3, required_months=6)

    raise ValueError(f"Modo de predictor no soportado: {settings.predictor_mode}")
