from fastapi import APIRouter, Depends, Request

from app.schemas.requests import PredictionRequest
from app.schemas.responses import PredictionResponse
from app.services.predictions import PredictionService


router = APIRouter(
    prefix="/api/v1/predictions",
    tags=["Predicciones"],
)


def get_prediction_service(
    request: Request,
) -> PredictionService:
    return request.app.state.prediction_service


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