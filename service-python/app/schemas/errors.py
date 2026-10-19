from typing import Literal
from uuid import UUID

from pydantic import Field

from app.schemas.requests import ContractModel


ErrorCode = Literal[
    "UNAUTHORIZED",
    "FORBIDDEN",
    "PAYLOAD_TOO_LARGE",
    "UNSUPPORTED_MEDIA_TYPE",
    "INVALID_REQUEST",
    "INTERNAL_ERROR",
    "SERVICE_UNAVAILABLE",
]

DetailCode = Literal[
    "REQUIRED_FIELD",
    "UNKNOWN_FIELD",
    "INVALID_TYPE",
    "INVALID_VALUE",
    "UNSUPPORTED_CONTRACT_VERSION",
    "DUPLICATE_MONTH",
    "NON_CONSECUTIVE_MONTHS",
    "UNSORTED_HISTORY",
    "DUPLICATE_CATEGORY",
    "UNKNOWN_CATEGORY",
    "INVALID_COVERAGE",
    "MISSING_CATEGORY_TOTAL",
    "INVALID_TARGET_MONTH",
    "AMOUNT_OUT_OF_RANGE",
    "INVALID_JSON",
]


class ErrorDetail(ContractModel):
    field: str = Field(strict=True, min_length=1)
    code: DetailCode


class ErrorBody(ContractModel):
    code: ErrorCode
    message: str = Field(strict=True, min_length=1)
    details: list[ErrorDetail]


class ErrorResponse(ContractModel):
    contract_version: Literal["1", "2"]
    request_id: UUID | None
    error: ErrorBody