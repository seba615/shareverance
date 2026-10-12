from uuid import UUID

from fastapi import Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
import logging

from app.schemas.errors import (
    DetailCode,
    ErrorBody,
    ErrorDetail,
    ErrorResponse,
)
from app.validation.history import HistoryValidationError


def get_request_id(body: object) -> UUID | None:
    if not isinstance(body, dict):
        return None

    value = body.get("request_id")

    if not isinstance(value, str):
        return None

    try:
        return UUID(value)
    except ValueError:
        return None


def error_response(
    request_id: UUID | None,
    details: list[ErrorDetail],
) -> JSONResponse:
    response = ErrorResponse(
        contract_version="1",
        request_id=request_id,
        error=ErrorBody(
            code="INVALID_REQUEST",
            message="La solicitud no cumple el contrato.",
            details=details,
        ),
    )

    return JSONResponse(
        status_code=422,
        content=response.model_dump(mode="json"),
    )


def validation_code(error: dict) -> DetailCode:
    error_type = error["type"]
    location = error.get("loc", ())
    field = str(location[-1]) if location else ""

    if error_type == "json_invalid":
        return "INVALID_JSON"

    if error_type == "missing":
        return "REQUIRED_FIELD"

    if error_type == "extra_forbidden":
        return "UNKNOWN_FIELD"

    if (
        field == "contract_version"
        and error_type == "literal_error"
    ):
        return "UNSUPPORTED_CONTRACT_VERSION"

    if error_type.endswith("_type") or error_type.endswith(
        "_parsing"
    ):
        return "INVALID_TYPE"

    if field == "amount_minor" and error_type in {
        "greater_than_equal",
        "less_than_equal",
    }:
        return "AMOUNT_OUT_OF_RANGE"

    return "INVALID_VALUE"


def field_path(location: tuple) -> str:
    path = ""

    for part in location:
        if part == "body" and not path:
            continue

        if isinstance(part, int):
            path += f"[{part}]"
        else:
            path += ("." if path else "") + str(part)

    return path or "body"


async def request_validation_handler(
    request: Request,
    exc: RequestValidationError,
) -> JSONResponse:
    details = [
        ErrorDetail(
            field=field_path(error.get("loc", ())),
            code=validation_code(error),
        )
        for error in exc.errors()
    ]

    return error_response(
        request_id=get_request_id(exc.body),
        details=details,
    )


async def history_validation_handler(
    request: Request,
    exc: HistoryValidationError,
) -> JSONResponse:
    return error_response(
        request_id=get_request_id(await request.json()),
        details=[
            ErrorDetail(field=issue.field, code=issue.code)
            for issue in exc.issues
        ],
    )

logger = logging.getLogger(__name__)


async def internal_error_handler(
    request: Request,
    exc: Exception,
) -> JSONResponse:
    logger.error(
        "Fallo interno al procesar %s",
        request.url.path,
        exc_info=(type(exc), exc, exc.__traceback__),
    )

    request_id = getattr(
        request.state,
        "contract_request_id",
        None,
    )

    response = ErrorResponse(
        contract_version="1",
        request_id=request_id,
        error=ErrorBody(
            code="INTERNAL_ERROR",
            message="No se pudo completar la operación.",
            details=[],
        ),
    )

    return JSONResponse(
        status_code=500,
        content=response.model_dump(mode="json"),
    )