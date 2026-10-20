from typing import Annotated, Literal
from uuid import UUID

from pydantic import Field

from app.schemas.requests import (
    AmountMinor,
    ContractModel,
    Month,
    PositiveId,
)


ResultStatus = Literal["OK", "INSUFFICIENT_HISTORY"]

OcrStatus = Literal["OK", "PARTIAL", "FAILED"]

OverallStatus = Literal[
    "OK",
    "PARTIAL",
    "INSUFFICIENT_HISTORY",
]

Direction = Literal[
    "INCREASING",
    "DECREASING",
    "UNCHANGED",
]

SignedAmountMinor = Annotated[
    int,
    Field(
        strict=True,
        ge=-9_007_199_254_740_991,
        le=9_007_199_254_740_991,
    ),
]

MonthCount = Annotated[
    int,
    Field(strict=True, ge=0, le=60),
]


class ResponseBase(ContractModel):
    contract_version: Literal["1", "2"]
    request_id: UUID
    organization_id: PositiveId
    currency: Literal["ARS"]
    status: OverallStatus
    simulated: bool = Field(strict=True)


class ComparisonPeriod(ContractModel):
    previous_month: Month
    current_month: Month


class AnalyticsResult(ContractModel):
    status: ResultStatus
    previous_amount_minor: AmountMinor | None
    current_amount_minor: AmountMinor | None
    change_amount_minor: SignedAmountMinor | None
    change_percentage: float | None = Field(allow_inf_nan=False)
    direction: Direction | None


class CategoryAnalyticsResult(AnalyticsResult):
    category_id: PositiveId


class EvolutionCategoryTotal(ContractModel):
    category_id: PositiveId
    amount_minor: AmountMinor


class MonthlyEvolution(ContractModel):
    """Total conciliado de un mes de la ventana; covered=false indica mes sin cobertura confirmada."""

    month: Month
    covered: bool = Field(strict=True)
    total_amount_minor: AmountMinor | None
    category_totals: list[EvolutionCategoryTotal] = Field(max_length=100)


class AnalyticsResponse(ResponseBase):
    comparison: ComparisonPeriod | None
    organization: AnalyticsResult
    categories: list[CategoryAnalyticsResult] = Field(max_length=100)
    evolution: list[MonthlyEvolution] | None = Field(default=None, max_length=60)


class PredictionResult(ContractModel):
    status: ResultStatus
    estimated_amount_minor: AmountMinor | None
    months_used: MonthCount
    required_months: Annotated[
        int,
        Field(strict=True, ge=1, le=60),
    ]
    method: str = Field(strict=True, min_length=1, max_length=100)
    model_version: str = Field(strict=True, min_length=1, max_length=100)


class CategoryPredictionResult(PredictionResult):
    category_id: PositiveId


class PredictionResponse(ResponseBase):
    target_month: Month
    organization: PredictionResult
    categories: list[CategoryPredictionResult] = Field(max_length=100)


class InternalOcrResponse(ContractModel):
    contract_version: Literal["1"]
    request_id: UUID
    organization_id: PositiveId
    receipt_id: UUID
    currency: Literal["ARS"]
    status: OcrStatus
    date: str | None = None
    amount: float | None = None
    description: str | None = None
    raw_text: str | None = None
    warnings: list[str] = Field(default_factory=list)
