"""Evaluación offline de candidatos; no selecciona ni activa modelos operativos."""

from collections.abc import Sequence
from dataclasses import dataclass
from decimal import Decimal, ROUND_HALF_UP

from app.predictors.base import Predictor
from app.predictors.mock import MockPredictor
from app.predictors.ses import SimpleExponentialSmoothingPredictor
from app.schemas.requests import MAX_SAFE_INTEGER


@dataclass(frozen=True)
class EvaluationMetrics:
    mae_ars: float
    rmse_ars: float
    mape_percentage: float | None
    wape_percentage: float | None
    total_samples: int
    mape_samples: int


def _validate_amounts(values: Sequence[int]) -> None:
    if any(type(value) is not int or not 0 <= value <= MAX_SAFE_INTEGER for value in values):
        raise ValueError("Los importes deben ser enteros válidos en centavos.")


def _rounded(value: Decimal) -> float:
    return float(value.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP))


def calculate_metrics(actual: Sequence[int], predicted: Sequence[int]) -> EvaluationMetrics:
    """Entradas en centavos, errores monetarios en ARS y relativos en porcentaje.

    WAPE incluye errores de meses cero; sin gasto real total su denominador es
    cero y devuelve None. MAPE es auxiliar y declara cuántos meses no cero usa.
    """
    if len(actual) != len(predicted) or not actual:
        raise ValueError("Se requieren secuencias no vacías de la misma longitud.")
    _validate_amounts(actual)
    _validate_amounts(predicted)
    errors = [abs(a - p) for a, p in zip(actual, predicted)]
    n = Decimal(len(actual))
    mae = Decimal(sum(errors)) / n / 100
    rmse = (Decimal(sum(error ** 2 for error in errors)) / n).sqrt() / 100
    nonzero = [(a, p) for a, p in zip(actual, predicted) if a > 0]
    mape = (sum(Decimal(abs(a - p)) / a for a, p in nonzero) * 100 / len(nonzero)) if nonzero else None
    wape = Decimal(sum(errors)) * 100 / sum(actual) if sum(actual) else None
    return EvaluationMetrics(
        mae_ars=_rounded(mae), rmse_ars=_rounded(rmse),
        mape_percentage=_rounded(mape) if mape is not None else None,
        wape_percentage=_rounded(wape) if wape is not None else None,
        total_samples=len(actual), mape_samples=len(nonzero),
    )


class HistoricalMeanReference(MockPredictor):
    """Referencia offline, no disponible como modo de la API."""

    @property
    def method(self) -> str:
        return "historical_mean"

    def predict(self, amounts: Sequence[int]) -> int:
        if len(amounts) < self.required_months:
            raise ValueError("La referencia requiere al menos seis meses.")
        _validate_amounts(amounts)
        return int((Decimal(sum(amounts)) / len(amounts)).quantize(Decimal("1"), rounding=ROUND_HALF_UP))


def rolling_origin_evaluation(
    series: Sequence[int], predictor: Predictor, min_train_months: int = 6,
) -> tuple[list[int], list[int], EvaluationMetrics]:
    """Cada objetivo t se estima sólo con series[:t], sin ajustar con el futuro."""
    if type(min_train_months) is not int or min_train_months < predictor.required_months:
        raise ValueError("La ventana inicial debe respetar los meses requeridos por el predictor.")
    if len(series) <= min_train_months:
        raise ValueError(f"La serie debe tener más de {min_train_months} meses para evaluación temporal.")
    _validate_amounts(series)
    actuals, predictions = [], []
    for t in range(min_train_months, len(series)):
        predictions.append(predictor.predict(series[:t]))
        actuals.append(series[t])
    return actuals, predictions, calculate_metrics(actuals, predictions)


def compare_candidates(series: Sequence[int], alpha: float = 0.3) -> dict[str, EvaluationMetrics]:
    """Compara sobre los mismos orígenes; no declara un ganador ni aceptación."""
    predictors = {
        "ses": SimpleExponentialSmoothingPredictor(alpha=alpha, required_months=6),
        "last_month": MockPredictor(),
        "historical_mean": HistoricalMeanReference(),
    }
    return {name: rolling_origin_evaluation(series, model)[2] for name, model in predictors.items()}


if __name__ == "__main__":
    import argparse
    from dataclasses import asdict
    import hashlib
    import json
    from pathlib import Path

    parser = argparse.ArgumentParser(description="Informe offline, sin aceptación ni activación de modelos.")
    parser.add_argument("dataset", type=Path, help="JSON con amounts_minor, meses consecutivos y cobertura verificada externamente")
    args = parser.parse_args()
    raw = args.dataset.read_bytes()
    series = json.loads(raw)["amounts_minor"]
    print(json.dumps({
        "dataset_sha256": hashlib.sha256(raw).hexdigest(),
        "alpha": 0.3, "min_train_months": 6, "series_months": len(series),
        "accepted": None,
        "metrics": {name: asdict(result) for name, result in compare_candidates(series).items()},
    }, indent=2, allow_nan=False))
