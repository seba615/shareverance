import logging
from uuid import UUID

from fastapi import Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.schemas.errors import (
    DetailCode,
    ErrorBody,
    ErrorDetail,
    ErrorResponse,
)
from app.services.views_repository import ViewsRepositoryError
from app.validation.history import HistoryValidationError


class InternalAuthError(Exception):
    """Error de autenticación o autorización en el canal interno Java -> Python."""

    def __init__(self, status_code: int, code: str, message: str):
        super().__init__(message)
        self.status_code = status_code
        self.code = code
        self.message = message


def _contract_version(request: Request) -> str:
    return "2" if request.url.path.startswith("/internal/v2/") else "1"


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
    request: Request,
    request_id: UUID | None,
    details: list[ErrorDetail],
) -> JSONResponse:
    response = ErrorResponse(
        contract_version=_contract_version(request),
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
        request=request,
        request_id=get_request_id(exc.body),
        details=details,
    )


async def history_validation_handler(
    request: Request,
    exc: HistoryValidationError,
) -> JSONResponse:
    request_id = getattr(request.state, "contract_request_id", None)
    if request_id is None:
        try:
            request_id = get_request_id(await request.json())
        except Exception:
            request_id = None

    return error_response(
        request=request,
        request_id=request_id,
        details=[
            ErrorDetail(field=issue.field, code=issue.code)
            for issue in exc.issues
        ],
    )


async def internal_auth_error_handler(
    request: Request,
    exc: InternalAuthError,
) -> JSONResponse:
    response = ErrorResponse(
        contract_version=_contract_version(request),
        request_id=getattr(request.state, "contract_request_id", None),
        error=ErrorBody(
            code=exc.code,
            message=exc.message,
            details=[],
        ),
    )

    return JSONResponse(
        status_code=exc.status_code,
        content=response.model_dump(mode="json"),
    )


async def views_repository_error_handler(
    request: Request,
    _exc: ViewsRepositoryError,
) -> JSONResponse:
    response = ErrorResponse(
        contract_version=_contract_version(request),
        request_id=getattr(request.state, "contract_request_id", None),
        error=ErrorBody(
            code="SERVICE_UNAVAILABLE",
            message="No se pudo consultar las vistas analíticas de PostgreSQL.",
            details=[],
        ),
    )

    return JSONResponse(
        status_code=503,
        content=response.model_dump(mode="json"),
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
        contract_version=_contract_version(request),
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