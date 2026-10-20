from collections.abc import Sequence
from decimal import Decimal, ROUND_HALF_UP

from app.predictors.base import Predictor
from app.schemas.requests import MAX_SAFE_INTEGER


class SimpleExponentialSmoothingPredictor(Predictor):
    """
    Predictor real mediante Suavizado Exponencial Simple (SES).
    
    Aplica la fórmula recursiva:
        S_1 = y_1
        S_t = alpha * y_t + (1 - alpha) * S_{t-1}   para t = 2, ..., n
    La estimación para el próximo período es S_n redondeada al centavo más cercano con ROUND_HALF_UP.
    """

    def __init__(self, alpha: float = 0.3, required_months: int = 6):
        if not (0.0 < alpha <= 1.0):
            raise ValueError("El factor de suavizado alpha debe estar en el intervalo (0, 1].")
        if required_months < 1:
            raise ValueError("required_months debe ser al menos 1.")
        self._alpha = Decimal(str(alpha))
        self._required_months = required_months

    @property
    def method(self) -> str:
        return "simple_exponential_smoothing"

    @property
    def model_version(self) -> str:
        return "1"

    @property
    def required_months(self) -> int:
        return self._required_months

    @property
    def simulated(self) -> bool:
        return False

    @property
    def alpha(self) -> float:
        return float(self._alpha)

    def predict(self, amounts: Sequence[int]) -> int:
        if len(amounts) < self._required_months:
            raise ValueError(
                f"El predictor requiere al menos {self._required_months} meses."
            )

        if any(
            type(amount) is not int
            or not 0 <= amount <= MAX_SAFE_INTEGER
            for amount in amounts
        ):
            raise ValueError(
                "Los importes deben ser enteros válidos en centavos."
            )

        one_minus_alpha = Decimal(1) - self._alpha
        s = Decimal(amounts[0])

        for amount in amounts[1:]:
            s = (self._alpha * Decimal(amount)) + (one_minus_alpha * s)

        estimate_decimal = s.quantize(Decimal("1"), rounding=ROUND_HALF_UP)
        estimate = int(estimate_decimal)

        if not 0 <= estimate <= MAX_SAFE_INTEGER:
            raise RuntimeError("Estimación fuera del rango seguro permitido.")

        return estimate

