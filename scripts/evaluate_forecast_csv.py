"""P3-H: evaluador offline multihorizonte (h=1..6) desde corte común en desarrollo."""
import argparse
from collections import defaultdict
import csv
from dataclasses import asdict
from decimal import Decimal, ROUND_HALF_UP
from fractions import Fraction
import hashlib
import io
import json
from pathlib import Path

from audit_predictor_dataset import audit, EXPECTED
from app.predictors.evaluation import calculate_metrics, HistoricalMeanReference
from app.predictors.mock import MockPredictor
from app.predictors.ses import SimpleExponentialSmoothingPredictor

DEVELOPMENT_ORIGINS_DEFAULT = ['2023-12', '2024-01', '2024-02', '2024-03', '2024-04', '2024-05', '2024-06']


def money(value):
    if isinstance(value, Fraction):
        value = Decimal(value.numerator) / value.denominator
    return float((value / 100).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP))


def evaluate_forecast(raw, from_origin='2023-12', to_origin='2024-06', max_horizon=6, commit=''):
    development = EXPECTED[:24]
    if max_horizon not in (4, 5, 6):
        raise ValueError('max_horizon debe ser 4, 5 o 6.')
    if from_origin not in development or to_origin not in development or from_origin > to_origin:
        raise ValueError('Los orígenes deben estar comprendidos en el período de desarrollo 2023–2024.')
    
    # Validar que para todos los orígenes del rango, origin + max_horizon <= 2024-12 (sin tocar reserva 2025)
    last_target_idx = development.index(to_origin) + max_horizon
    if last_target_idx >= len(development):
        raise ValueError(f'El origen final {to_origin} con horizonte {max_horizon} excede el período de desarrollo 2024-12.')

    manifest = audit(raw)
    if any(o['category_count'] != 8 for o in manifest['organization_series']):
        raise ValueError('Universo de categorías incompleto: se requieren ocho por organización.')

    values, categories = defaultdict(dict), defaultdict(set)
    for row in csv.DictReader(io.StringIO(raw.decode('utf-8-sig')), delimiter=';'):
        org, cat = int(row['organization_id']), int(row['category_id'])
        categories[org].add(cat)
        if row['month'] <= '2024-12':
            values[('category', org, cat)][row['month']] = int(row['amount_minor'])

    for org, cats in categories.items():
        for month in development:
            if all(month in values[('category', org, cat)] for cat in cats):
                values[('organization', org, None)][month] = sum(values[('category', org, cat)][month] for cat in cats)
        values[('organization', org, None)]

    models = {
        'ses': SimpleExponentialSmoothingPredictor(alpha=0.3),
        'last_month': MockPredictor(),
        'historical_mean': HistoricalMeanReference()
    }

    origins = [m for m in development if from_origin <= m <= to_origin]
    details = []
    series_reports = []

    # Estructura para acumular errores por (level, model_name, horizon)
    level_horizon_data = defaultdict(lambda: defaultdict(list))

    for (level, org, cat), amounts in sorted(values.items(), key=lambda p: (p[0][0], p[0][1], p[0][2] or 0)):
        series_points = []
        series_horizon_errors = defaultdict(lambda: defaultdict(list))

        for origin in origins:
            origin_idx = development.index(origin)
            earlier = development[:origin_idx + 1]
            history = []
            for month in reversed(earlier):
                if month not in amounts:
                    break
                history.insert(0, amounts[month])

            # Insuficiencia si el origen tiene menos de 6 meses de historia continua
            status_origin = 'INSUFFICIENT_HISTORY' if len(history) < 6 else 'OK'

            for h in range(1, max_horizon + 1):
                target_month = development[origin_idx + h]
                actual = amounts.get(target_month)
                status = 'MISSING_TARGET' if actual is None else status_origin

                point = {
                    'level': level,
                    'organization_id': org,
                    'category_id': cat,
                    'origin_month': origin,
                    'horizon': h,
                    'target_month': target_month,
                    'status': status,
                    'available_history_months': len(history),
                    'actual_minor': actual,
                    'predictions': {}
                }

                if status == 'OK':
                    # Previsión recursiva/directa desde origen común sin incorporar futuros
                    for name, model in models.items():
                        # Para los modelos actuales univariados (last_month, SES, mean),
                        # la proyección recursiva produce un valor constante para h=1..max_horizon
                        prediction = model.predict(history)
                        signed_err = prediction - actual
                        abs_err = abs(signed_err)
                        point['predictions'][name] = {
                            'estimated_minor': prediction,
                            'signed_error_minor': signed_err,
                            'absolute_error_minor': abs_err
                        }
                        series_horizon_errors[name][h].append((actual, prediction))
                series_points.append(point)

        # Métricas de la serie por horizonte y modelo
        series_metrics_by_h = {}
        for h in range(1, max_horizon + 1):
            h_metrics = {}
            for name in models:
                pairs = series_horizon_errors[name][h]
                if pairs and len(pairs) == len(origins):
                    actuals, preds = zip(*pairs)
                    h_metrics[name] = {
                        **asdict(calculate_metrics(actuals, preds)),
                        'mean_signed_error_ars': money(Decimal(sum(p - a for a, p in pairs)) / len(pairs))
                    }
                    level_horizon_data[level][(name, h)].append(pairs)
                else:
                    h_metrics[name] = None
            series_metrics_by_h[str(h)] = h_metrics

        complete = all(pt['status'] == 'OK' for pt in series_points)
        series_reports.append({
            'level': level,
            'organization_id': org,
            'category_id': cat,
            'complete_all_horizons': complete,
            'metrics_by_horizon': series_metrics_by_h
        })
        details.extend(series_points)

    # Resumen por nivel y por horizonte
    level_summaries = {}
    for level in ('organization', 'category'):
        expected_series = sum(s['level'] == level for s in series_reports)
        eligible = sum(s['level'] == level and s['complete_all_horizons'] for s in series_reports)
        horizon_summaries = {}

        for h in range(1, max_horizon + 1):
            h_metrics = {}
            for name in models:
                groups = level_horizon_data[level][(name, h)]
                if not groups:
                    h_metrics[name] = None
                    continue
                maes = [Fraction(sum(abs(a - p) for a, p in pairs), len(pairs)) for pairs in groups]
                absolute = sum(abs(a - p) for pairs in groups for a, p in pairs)
                actual_total = sum(a for pairs in groups for a, p in pairs)
                exact_mae = sum(maes, Fraction()) / len(maes)
                h_metrics[name] = {
                    'equal_series_mae_ars': money(exact_mae),
                    'equal_series_mae_minor_fraction': {'numerator': exact_mae.numerator, 'denominator': exact_mae.denominator},
                    'pooled_wape_percentage': float((Decimal(absolute) * 100 / actual_total).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)) if actual_total else None,
                    'series_count': len(groups),
                    'samples': sum(map(len, groups))
                }
            horizon_summaries[str(h)] = h_metrics

        level_summaries[level] = {
            'expected_series': expected_series,
            'complete_series': eligible,
            'excluded_series': expected_series - eligible,
            'horizons': horizon_summaries
        }

    return {
        'report_version': 1,
        'dataset_sha256': manifest['dataset_sha256'],
        'code_commit': commit,
        'evaluator_sha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        'scope': 'synthetic_development_multihorizon',
        'coverage_policy': 'synthetic_declared_not_real_coverage',
        'configuration': {
            'from_origin': from_origin,
            'to_origin': to_origin,
            'origins_count': len(origins),
            'max_horizon': max_horizon,
            'min_history_months': 6,
            'alpha': 0.3,
            'training': 'common_origin_recursive_forecast_without_future_data',
            'method_versions': {name: model.model_version for name, model in models.items()}
        },
        'accepted': None,
        'model_promoted': False,
        'reserve_evaluated': False,
        'levels': level_summaries,
        'series': series_reports,
        'points': details,
        'limitations': manifest['pending_evidence'] + [
            'No representa precisión sobre hogares reales.',
            'Horizontes 4 a 6 meses con modelos sin tendencia producen proyecciones planas.',
            'Ventanas solapadas entre orígenes no constituyen observaciones independientes.'
        ]
    }


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--csv', type=Path, required=True)
    parser.add_argument('--from-origin', default='2023-12')
    parser.add_argument('--to-origin', default='2024-06')
    parser.add_argument('--max-horizon', type=int, default=6)
    parser.add_argument('--commit', required=True)
    args = parser.parse_args()
    try:
        print(json.dumps(evaluate_forecast(
            args.csv.read_bytes(),
            from_origin=args.from_origin,
            to_origin=args.to_origin,
            max_horizon=args.max_horizon,
            commit=args.commit
        ), indent=2, ensure_ascii=False, allow_nan=False))
    except (ValueError, OSError) as error:
        raise SystemExit(f'Evaluación multihorizonte fallida: {error}') from None

