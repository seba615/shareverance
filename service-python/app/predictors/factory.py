from app.config import Settings
from app.predictors.base import Predictor
from app.predictors.mock import MockPredictor


def create_predictor(settings: Settings) -> Predictor:
    if settings.predictor_mode == "mock":
        return MockPredictor()

    raise ValueError("Modo de predictor no soportado.")