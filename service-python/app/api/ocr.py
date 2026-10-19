from fastapi import APIRouter, Depends, Request

from app.api.predictions import verify_internal_service_auth
from app.schemas.requests import InternalOcrRequest
from app.schemas.responses import InternalOcrResponse
from app.services.ocr import OcrService

internal_ocr_router = APIRouter(
    prefix="/internal/v1/ocr",
    tags=["OCR Interno"],
    dependencies=[Depends(verify_internal_service_auth)],
)


def get_ocr_service(request: Request) -> OcrService:
    return request.app.state.ocr_service


@internal_ocr_router.post(
    "",
    response_model=InternalOcrResponse,
)
def process_ocr(
    payload: InternalOcrRequest,
    request: Request,
    service: OcrService = Depends(get_ocr_service),
) -> InternalOcrResponse:
    request.state.contract_request_id = payload.request_id
    return service.process_ocr(payload)
