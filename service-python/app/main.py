from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.exceptions import RequestValidationError

from app.api.error_handlers import (
    InternalAuthError,
    history_validation_handler,
    internal_auth_error_handler,
    internal_error_handler,
    request_validation_handler,
    views_repository_error_handler,
)
from app.api.ocr import internal_ocr_router
from app.api.predictions import (
    internal_router as internal_predictions_router,
    router as predictions_router,
)
from app.api.request_limits import RequestLimitsMiddleware
from app.config import load_settings
from app.predictors.factory import create_predictor
from app.services.ocr import MockOcrEngine, OcrService, TesseractOcrEngine
from app.services.predictions import PredictionService
from app.services.views_repository import (
    ViewsRepository,
    ViewsRepositoryError,
)
from app.validation.history import HistoryValidationError
from pathlib import Path


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = load_settings()
    predictor = create_predictor(settings)

    ocr_engine = (
        MockOcrEngine()
        if settings.ocr_engine == "mock"
        else TesseractOcrEngine()
    )

    app.state.settings = settings
    app.state.prediction_service = PredictionService(predictor)
    app.state.views_repository = ViewsRepository(settings.database_url)
    app.state.ocr_service = OcrService(
        Path(settings.receipts_storage_path),
        ocr_engine,
    )

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
    InternalAuthError,
    internal_auth_error_handler,
)

app.add_exception_handler(
    ViewsRepositoryError,
    views_repository_error_handler,
)

app.add_exception_handler(
    Exception,
    internal_error_handler,
)

app.include_router(predictions_router)
app.include_router(internal_predictions_router)
app.include_router(internal_ocr_router)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP", "service": "python"}