from typing import Annotated

from fastapi import APIRouter, Depends, Path, Request

from app.schemas.requests import MAX_SAFE_INTEGER, Month, PredictionRequest
from app.schemas.responses import PredictionResponse
from app.services.java_client import JavaServiceClient
from app.services.predictions import PredictionService


router = APIRouter(
    prefix="/api/v1/predictions",
    tags=["Predicciones"],
)


def get_prediction_service(
    request: Request,
) -> PredictionService:
    return request.app.state.prediction_service


def get_java_service_client(request: Request) -> JavaServiceClient:
    return request.app.state.java_service_client


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


@router.get(
    "/organization/{organization_id}/monthly",
    response_model=PredictionResponse,
)
def predict_organization_monthly(
     organization_id: Annotated[
          int,
          Path(gt=0, le=MAX_SAFE_INTEGER),
     ],
    target_month: Month,
    request: Request,
    service: PredictionService = Depends(get_prediction_service),
    java_client: JavaServiceClient = Depends(get_java_service_client),
) -> PredictionResponse:
    payload = java_client.get_prediction_request(organization_id, target_month)
    request.state.contract_request_id = payload.request_id
    return service.predict(payload)