from fastapi.responses import JSONResponse
from starlette.types import ASGIApp, Receive, Scope, Send

from app.schemas.errors import ErrorBody, ErrorResponse


MAX_BODY_BYTES = 1024 * 1024

CONTRACT_PATHS = {
    "/api/v1/predictions/monthly",
    "/api/v1/analytics/monthly",
    "/internal/v2/predictions/monthly",
}


def _contract_version_for_path(path: str) -> str:
    return "2" if path.startswith("/internal/v2/") else "1"


class RequestLimitsMiddleware:
    """Controla solicitudes POST de las operaciones del contrato."""

    def __init__(self, app: ASGIApp):
        self.app = app

    async def reject(
        self,
        scope: Scope,
        receive: Receive,
        send: Send,
        status_code: int,
        code: str,
        message: str,
    ) -> None:
        body = ErrorResponse(
            contract_version=_contract_version_for_path(scope["path"]),
            request_id=None,
            error=ErrorBody(
                code=code,
                message=message,
                details=[],
            ),
        )

        response = JSONResponse(
            status_code=status_code,
            content=body.model_dump(mode="json"),
        )

        await response(scope, receive, send)

    async def __call__(
        self,
        scope: Scope,
        receive: Receive,
        send: Send,
    ) -> None:
        if (
            scope["type"] != "http"
            or scope["method"] != "POST"
            or scope["path"] not in CONTRACT_PATHS
        ):
            await self.app(scope, receive, send)
            return

        headers = dict(scope["headers"])

        content_type = (
            headers.get(b"content-type", b"")
            .decode("latin-1")
            .split(";", 1)[0]
            .strip()
            .lower()
        )

        if content_type != "application/json":
            await self.reject(
                scope,
                receive,
                send,
                415,
                "UNSUPPORTED_MEDIA_TYPE",
                "El cuerpo debe enviarse como application/json.",
            )
            return

        chunks: list[bytes] = []
        size = 0

        # Comprobamos los bytes recibidos, sin confiar solamente
        # en el encabezado Content-Length.
        while True:
            message = await receive()

            if message["type"] == "http.disconnect":
                return

            chunk = message.get("body", b"")
            size += len(chunk)

            if size > MAX_BODY_BYTES:
                await self.reject(
                    scope,
                    receive,
                    send,
                    413,
                    "PAYLOAD_TOO_LARGE",
                    "El cuerpo supera el límite de 1 MiB.",
                )
                return

            chunks.append(chunk)

            if not message.get("more_body", False):
                break

        body = b"".join(chunks)
        delivered = False

        async def replay_receive():
            nonlocal delivered

            if not delivered:
                delivered = True
                return {
                    "type": "http.request",
                    "body": body,
                    "more_body": False,
                }

            return await receive()

        await self.app(scope, replay_receive, send)