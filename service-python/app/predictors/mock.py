from collections.abc import Sequence

from app.predictors.base import Predictor
from app.schemas.requests import MAX_SAFE_INTEGER


class MockPredictor(Predictor):
    """Simulador: devuelve el último importe observado."""

    @property
    def method(self) -> str:
        return "mock"

    @property
    def model_version(self) -> str:
        return "1"

    @property
    def required_months(self) -> int:
        return 6

    @property
    def simulated(self) -> bool:
        return True

    def predict(self, amounts: Sequence[int]) -> int:
        if len(amounts) < self.required_months:
            raise ValueError(
                "El predictor requiere al menos seis meses."
            )

        if any(
            type(amount) is not int
            or not 0 <= amount <= MAX_SAFE_INTEGER
            for amount in amounts
        ):
            raise ValueError(
                "Los importes deben ser enteros válidos en centavos."
            )

        return amounts[-1]