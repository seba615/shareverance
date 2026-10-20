from fastapi import APIRouter, Depends, Request

from app.api.predictions import (
    get_views_repository,
    verify_internal_service_auth,
)
from app.schemas.requests import InternalAnalyticsRequest
from app.schemas.responses import AnalyticsResponse
from app.services.analytics import AnalyticsService
from app.services.views_repository import ViewsRepository

internal_analytics_router = APIRouter(
    prefix="/internal/v2/analytics",
    tags=["Analítica Interna"],
)

_service = AnalyticsService()


@internal_analytics_router.post(
    "/monthly",
    response_model=AnalyticsResponse,
    dependencies=[Depends(verify_internal_service_auth)],
)
def analyze_internal_monthly(
    payload: InternalAnalyticsRequest,
    request: Request,
    views_repository: ViewsRepository = Depends(get_views_repository),
) -> AnalyticsResponse:
    request.state.contract_request_id = payload.request_id
    _service.window(payload)
    covered_months, expense_rows = views_repository.fetch_organization_views(
        payload.organization_id
    )
    return _service.analyze(payload, covered_months, expense_rows)

