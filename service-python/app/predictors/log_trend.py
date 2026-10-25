"""Regresión de log(1 + centavos) contra tiempo; previsión directa desde un corte."""
from collections.abc import Sequence
from decimal import Decimal, ROUND_HALF_UP, localcontext

from app.predictors.base import Predictor
from app.schemas.requests import MAX_SAFE_INTEGER


class LogTrendPredictor(Predictor):
    @property
    def method(self) -> str:
        return "log_linear_trend"

    @property
    def model_version(self) -> str:
        return "1"

    @property
    def required_months(self) -> int:
        return 6

    @property
    def simulated(self) -> bool:
        return False

    def predict(self, amounts: Sequence[int]) -> int:
        return self.forecast(amounts, 1)[0]

    def forecast(self, amounts: Sequence[int], horizon: int) -> list[int]:
        if type(horizon) is not int or not 1 <= horizon <= 6:
            raise ValueError("El horizonte debe estar entre 1 y 6.")
        if len(amounts) < self.required_months:
            raise ValueError("El predictor requiere al menos seis meses.")
        if any(type(a) is not int or not 0 <= a <= MAX_SAFE_INTEGER for a in amounts):
            raise ValueError("Los importes deben ser enteros válidos en centavos.")
        if all(a == amounts[0] for a in amounts):
            return [amounts[0]] * horizon
        # El repositorio entrega como máximo 60 meses confirmados. El ajuste se
        # realiza una vez sobre esas observaciones; no se reentrena con pronósticos.
        with localcontext() as context:
            context.prec = 40
            n = len(amounts)
            mean_x = Decimal(n - 1) / 2
            logs = [(Decimal(a) + 1).ln() for a in amounts]
            mean_y = sum(logs) / n
            variance = sum((Decimal(i) - mean_x) ** 2 for i in range(n))
            slope = sum((Decimal(i) - mean_x) * (y - mean_y)
                        for i, y in enumerate(logs)) / variance
            result = []
            maximum_log = (Decimal(MAX_SAFE_INTEGER) + 1).ln()
            for h in range(1, horizon + 1):
                estimate_log = mean_y + slope * (Decimal(n - 1 + h) - mean_x)
                if estimate_log > maximum_log:
                    raise RuntimeError("Estimación de tendencia fuera del rango seguro permitido.")
                amount = max(Decimal(0), estimate_log.exp() - 1)
                result.append(int(amount.quantize(Decimal(1), rounding=ROUND_HALF_UP)))
            return result
