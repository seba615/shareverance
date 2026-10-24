import hmac

from fastapi import APIRouter, Depends, Request

from app.api.error_handlers import InternalAuthError
from app.config import Settings, load_settings
from app.schemas.requests import (
    ForecastRequest,
    InternalForecastRequest,
    InternalPredictionRequest,
    PredictionRequest,
)
from app.schemas.responses import (
    ForecastResponse,
    PredictionResponse,
)
from app.services.predictions import PredictionService
from app.services.views_repository import ViewsRepository


router = APIRouter(
    prefix="/api/v1/predictions",
    tags=["Predicciones"],
)

internal_router = APIRouter(
    prefix="/internal/v2/predictions",
    tags=["Predicciones Internas"],
)


def get_settings(request: Request) -> Settings:
    settings = getattr(request.app.state, "settings", None)
    if settings is None:
        settings = load_settings()
        request.app.state.settings = settings
    return settings


def get_prediction_service(
    request: Request,
    settings: Settings = Depends(get_settings),
) -> PredictionService:
    service = getattr(request.app.state, "prediction_service", None)
    if service is None or getattr(service, "_settings_mode", None) != settings.predictor_mode:
        from app.predictors.factory import create_predictor
        service = PredictionService(create_predictor(settings))
        service._settings_mode = settings.predictor_mode
        request.app.state.prediction_service = service
    return service


def get_views_repository(
    request: Request,
) -> ViewsRepository:
    return request.app.state.views_repository


def verify_internal_service_auth(
    request: Request,
    settings: Settings = Depends(get_settings),
) -> None:
    auth_header = request.headers.get("authorization", "")
    if not auth_header.startswith("Bearer "):
        raise InternalAuthError(
            status_code=401,
            code="UNAUTHORIZED",
            message="Falta token de autenticación de servicio interno.",
        )

    token = auth_header[7:].strip()
    if not token:
        raise InternalAuthError(
            status_code=401,
            code="UNAUTHORIZED",
            message="Falta token de autenticación de servicio interno.",
        )

    valid_tokens = [
        secret
        for secret in (
            settings.internal_service_token,
            settings.internal_service_token_previous,
        )
        if secret
    ]

    if not valid_tokens or not any(
        hmac.compare_digest(token.encode("utf-8"), secret.encode("utf-8"))
        for secret in valid_tokens
    ):
        raise InternalAuthError(
            status_code=403,
            code="FORBIDDEN",
            message="Token de servicio interno inválido.",
        )


@router.post(
    "/monthly",
    response_model=PredictionResponse,
)
def predict_monthly(
    payload: PredictionRequest,
    request: Request,
    service: PredictionService = Depends(get_prediction_service),
) -> PredictionResponse:
    request.state.contract_request_id = payload.request_id

    return service.predict(payload)


@internal_router.post(
    "/monthly",
    response_model=PredictionResponse,
    dependencies=[Depends(verify_internal_service_auth)],
)
def predict_internal_monthly(
    payload: InternalPredictionRequest,
    request: Request,
    service: PredictionService = Depends(get_prediction_service),
    views_repository: ViewsRepository = Depends(get_views_repository),
) -> PredictionResponse:
    request.state.contract_request_id = payload.request_id
    prediction_request = views_repository.build_prediction_request(payload)
    return service.predict(prediction_request, contract_version="2")

@router.post(
    "/forecast",
    response_model=ForecastResponse,
)
def predict_forecast(
    payload: ForecastRequest,
    request: Request,
    service: PredictionService = Depends(get_prediction_service),
) -> ForecastResponse:
    request.state.contract_request_id = payload.request_id
    return service.predict_forecast(payload)


@internal_router.post(
    "/forecast",
    response_model=ForecastResponse,
    dependencies=[Depends(verify_internal_service_auth)],
)
def predict_internal_forecast(
    payload: InternalForecastRequest,
    request: Request,
    service: PredictionService = Depends(get_prediction_service),
    views_repository: ViewsRepository = Depends(get_views_repository),
) -> ForecastResponse:
    request.state.contract_request_id = payload.request_id
    forecast_request = views_repository.build_forecast_request(payload)
    return service.predict_forecast(forecast_request, contract_version="2")
