import json
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

from pydantic import ValidationError

from app.schemas.requests import PredictionRequest


class JavaServiceError(RuntimeError):
    pass


class JavaServiceClient:
    def __init__(
        self,
        base_url: str,
        client_id: str,
        client_secret: str,
        timeout_seconds: float = 5,
    ) -> None:
        self.base_url = base_url.rstrip("/")
        self.client_id = client_id
        self.client_secret = client_secret
        self.timeout_seconds = timeout_seconds
        self._access_token: str | None = None
        self._token_expires_at = 0.0

    def get_prediction_request(
        self,
        organization_id: int,
        target_month: str,
    ) -> PredictionRequest:
        query = urlencode({"targetMonth": target_month})
        url = (
            f"{self.base_url}/api/v1/expenses/organization/"
            f"{organization_id}/history?{query}"
        )

        for attempt in range(2):
            token = self._get_access_token()
            request = Request(
                url,
                headers={"Authorization": f"Bearer {token}"},
            )
            try:
                body = self._read_json(request)
                try:
                    return PredictionRequest.model_validate(body)
                except ValidationError as error:
                    raise JavaServiceError(
                        "Java devolvió un historial que no cumple el contrato"
                    ) from error
            except HTTPError as error:
                if error.code == 401 and attempt == 0:
                    self._access_token = None
                    continue
                raise JavaServiceError("Java rechazó la solicitud de historial") from error

        raise JavaServiceError("Java rechazó el token de acceso")

    def _get_access_token(self) -> str:
        if self._access_token and time.monotonic() < self._token_expires_at:
            return self._access_token

        if not self.client_id or not self.client_secret:
            raise JavaServiceError("Faltan credenciales OAuth para Java")

        request = Request(
            f"{self.base_url}/api/v1/auth/token",
            data=urlencode(
                {
                    "grant_type": "client_credentials",
                    "client_id": self.client_id,
                    "client_secret": self.client_secret,
                    "scope": "analytics:read",
                }
            ).encode("ascii"),
            headers={"Content-Type": "application/x-www-form-urlencoded"},
            method="POST",
        )
        try:
            response = self._read_json(request)
        except HTTPError as error:
            raise JavaServiceError("Java rechazó las credenciales OAuth") from error

        access_token = response.get("access_token")
        expires_in = response.get("expires_in")
        token_type = response.get("token_type")
        scope = response.get("scope", "")
        if (
            not isinstance(access_token, str)
            or not isinstance(token_type, str)
            or token_type.lower() != "bearer"
            or type(expires_in) is not int
            or expires_in <= 0
            or not isinstance(scope, str)
            or "analytics:read" not in scope.split()
        ):
            raise JavaServiceError("Java devolvió una respuesta OAuth inválida")

        self._access_token = access_token
        self._token_expires_at = time.monotonic() + max(expires_in - 30, 0)
        return access_token

    def _read_json(self, request: Request) -> dict:
        try:
            with urlopen(request, timeout=self.timeout_seconds) as response:
                body = json.loads(response.read())
        except HTTPError:
            raise
        except (URLError, TimeoutError, json.JSONDecodeError) as error:
            raise JavaServiceError("Java no está disponible o devolvió JSON inválido") from error

        if not isinstance(body, dict):
            raise JavaServiceError("Java devolvió una respuesta inválida")
        return body