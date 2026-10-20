from collections.abc import Sequence
from dataclasses import dataclass
from decimal import Decimal
import math

from app.predictors.base import Predictor
from app.predictors.mock import MockPredictor
from app.predictors.ses import SimpleExponentialSmoothingPredictor


@dataclass(frozen=True)
class EvaluationMetrics:
    mae: float
    rmse: float
    mape: float | None
    total_samples: int


def calculate_metrics(actual: Sequence[int], predicted: Sequence[int]) -> EvaluationMetrics:
    if len(actual) != len(predicted):
        raise ValueError("Las secuencias de valores reales y predichos deben tener la misma longitud.")
    if len(actual) == 0:
        raise ValueError("No se pueden calcular métricas sobre secuencias vacías.")

    n = len(actual)
    abs_errors = [abs(a - p) for a, p in zip(actual, predicted)]
    sq_errors = [(a - p) ** 2 for a, p in zip(actual, predicted)]

    mae = sum(abs_errors) / n
    rmse = math.sqrt(sum(sq_errors) / n)

    non_zero_actuals = [(a, p) for a, p in zip(actual, predicted) if a > 0]
    if non_zero_actuals:
        mape = (sum(abs(a - p) / a for a, p in non_zero_actuals) / len(non_zero_actuals)) * 100.0
    else:
        mape = None

    return EvaluationMetrics(
        mae=round(mae, 2),
        rmse=round(rmse, 2),
        mape=round(mape, 2) if mape is not None else None,
        total_samples=n,
    )


def rolling_origin_evaluation(
    series: Sequence[int],
    predictor: Predictor,
    min_train_months: int = 6,
) -> tuple[list[int], list[int], EvaluationMetrics]:
    """
    Evaluación temporal hacia adelante (rolling-origin / walk-forward).
    Para cada paso t desde min_train_months hasta len(series) - 1:
      - Entrena/alimenta con series[:t]
      - Predice el valor en t (series[t])
      - Compara contra el valor real series[t]
    """
    if len(series) <= min_train_months:
        raise ValueError(f"La serie debe tener más de {min_train_months} meses para evaluación temporal.")

    actuals: list[int] = []
    predictions: list[int] = []

    for t in range(min_train_months, len(series)):
        train_window = series[:t]
        y_true = series[t]
        y_pred = predictor.predict(train_window)
        actuals.append(y_true)
        predictions.append(y_pred)

    metrics = calculate_metrics(actuals, predictions)
    return actuals, predictions, metrics

