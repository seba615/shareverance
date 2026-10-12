from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.exceptions import RequestValidationError

from app.api.error_handlers import (
    history_validation_handler,
    internal_error_handler,
    request_validation_handler,
)
from app.api.predictions import router as predictions_router
from app.api.request_limits import RequestLimitsMiddleware
from app.config import load_settings
from app.predictors.factory import create_predictor
from app.services.predictions import PredictionService
from app.validation.history import HistoryValidationError


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = load_settings()
    predictor = create_predictor(settings)

    app.state.prediction_service = PredictionService(predictor)

    yield


app = FastAPI(
    title="Shareverance - Servicio Python",
    version="0.1.0",
    lifespan=lifespan,
)

app.add_middleware(RequestLimitsMiddleware)

app.add_exception_handler(
    RequestValidationError,
    request_validation_handler,
)

app.add_exception_handler(
    HistoryValidationError,
    history_validation_handler,
)

app.add_exception_handler(
    Exception,
    internal_error_handler,
)

app.include_router(predictions_router)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP", "service": "python"}