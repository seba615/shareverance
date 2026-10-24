"""P4-H: comparación descriptiva en desarrollo; no acepta ni promueve modelos."""
import argparse
from collections import defaultdict
from decimal import Decimal, ROUND_HALF_UP
from fractions import Fraction
import hashlib
import json
from pathlib import Path

from evaluate_forecast_csv import evaluate_forecast, money

METHODS = ('last_month', 'historical_mean', 'ses')


def exact(value):
    return {'numerator': value.numerator, 'denominator': value.denominator}


def percentage(value):
    return float((Decimal(value.numerator) * 100 / value.denominator).quantize(
        Decimal('0.01'), rounding=ROUND_HALF_UP))


def comparison(points):
    """Una serie, un conjunto común de puntos; decisiones con fracciones exactas."""
    maes = {name: Fraction(sum(p['predictions'][name]['absolute_error_minor']
                              for p in points), len(points)) for name in METHODS}
    best = min(maes['last_month'], maes['historical_mean'])
    delta = maes['ses'] - best
    return {
        'samples': len(points),
        'mae_minor_fractions': {name: exact(mae) for name, mae in maes.items()},
        'best_references': [name for name in METHODS[:2] if maes[name] == best],
        'ses_delta_minor_fraction': exact(delta),
        'ses_delta_ars': money(delta),
        'ses_relative_delta_percentage': percentage(delta / best) if best else None,
        'reference_zero_with_positive_ses_error': best == 0 and maes['ses'] > 0,
        'ses_relation': 'worse' if delta > 0 else 'better' if delta < 0 else 'tie',
    }


def summarize(groups):
    if not groups:
        return {'status': 'NO_ELIGIBLE_SERIES', 'series_count': 0, 'samples': 0,
                'methods': {name: None for name in METHODS}, 'ses_stability': None}
    comparisons = [comparison(points) for points in groups]
    methods = {}
    for name in METHODS:
        maes = [Fraction(**c['mae_minor_fractions'][name]) for c in comparisons]
        mean = sum(maes, Fraction()) / len(maes)
        error = sum(p['predictions'][name]['absolute_error_minor'] for g in groups for p in g)
        actual = sum(p['actual_minor'] for g in groups for p in g)
        methods[name] = {'equal_series_mae_ars': money(mean),
                         'equal_series_mae_minor_fraction': exact(mean),
                         'pooled_wape_percentage': percentage(Fraction(error, actual)) if actual else None}
    deltas = [Fraction(**c['ses_delta_minor_fraction']) for c in comparisons]
    worse = sum(c['ses_relation'] == 'worse' for c in comparisons)
    return {
        'status': 'OK', 'series_count': len(groups), 'samples': sum(map(len, groups)),
        'methods': methods,
        'ses_stability': {
            'comparison': 'best_of_last_month_and_historical_mean_per_series',
            'worse_series': worse,
            'better_series': sum(c['ses_relation'] == 'better' for c in comparisons),
            'tied_series': sum(c['ses_relation'] == 'tie' for c in comparisons),
            'worse_percentage': percentage(Fraction(worse, len(groups))),
            'max_positive_delta_minor_fraction': exact(max(Fraction(), *deltas)),
            'max_positive_delta_ars': money(max(Fraction(), *deltas)),
            'reference_zero_with_positive_ses_error_series': sum(
                c['reference_zero_with_positive_ses_error'] for c in comparisons),
        },
    }


def compare_forecast(raw, from_origin='2023-12', to_origin='2024-06', commit=''):
    # Siempre seis horizontes: también las ventanas 4/5 usan esta misma cohorte.
    source = evaluate_forecast(raw, from_origin, to_origin, 6, commit)
    grouped = defaultdict(list)
    for point in source['points']:
        grouped[(point['level'], point['organization_id'], point['category_id'])].append(point)
    series = []
    eligible = defaultdict(list)
    for (level, org, cat), points in sorted(grouped.items(), key=lambda item: (
            item[0][0], item[0][1], item[0][2] or 0)):
        failures = [{key: p[key] for key in ('origin_month', 'horizon', 'target_month', 'status')}
                    for p in points if p['status'] != 'OK']
        row = {'level': level, 'organization_id': org, 'category_id': cat,
               'eligible': not failures, 'excluded_points': failures}
        if not failures:
            eligible[level].append(points)
            row['horizons'] = {str(h): comparison([p for p in points if p['horizon'] == h])
                               for h in range(1, 7)}
            row['windows'] = {str(h): comparison([p for p in points if p['horizon'] <= h])
                              for h in (4, 5, 6)}
        series.append(row)
    levels = {}
    for level in ('organization', 'category'):
        groups = eligible[level]
        expected = sum(s['level'] == level for s in series)
        levels[level] = {
            'expected_series': expected, 'eligible_series': len(groups),
            'excluded_series': expected - len(groups),
            'horizons': {str(h): summarize([[p for p in g if p['horizon'] == h] for g in groups])
                         for h in range(1, 7)},
            'windows': {str(h): summarize([[p for p in g if p['horizon'] <= h] for g in groups])
                        for h in (4, 5, 6)},
        }
    return {
        'report_version': 1, 'scope': 'synthetic_development_multihorizon_comparison',
        'dataset_sha256': source['dataset_sha256'], 'code_commit': commit,
        'comparison_sha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
        'evaluator_sha256': source['evaluator_sha256'],
        'predictor_source_sha256': {name: hashlib.sha256(Path(path).read_bytes()).hexdigest()
                                    for name, path in _predictor_paths().items()},
        'configuration': source['configuration'],
        'cohort_policy': 'complete_all_origins_and_six_horizons_for_all_methods_and_windows',
        'window_policy': 'equal_weight_per_series_then_equal_weight_per_origin_and_horizon',
        'accepted': None, 'model_promoted': False, 'reserve_evaluated': False,
        'final_evaluation_accredited': False, 'thresholds': None,
        'levels': levels, 'series': series,
        'limitations': [
            'Datos sintéticos: no acreditan precisión sobre hogares reales.',
            '2025 ya fue consultado en P4; no es una reserva intacta y no se reevalúa aquí.',
            'Ventanas y orígenes solapados no son observaciones independientes.',
            'Comparación descriptiva sin intervalos de confianza ni umbrales acordados.',
            'Configuración registrada, no congelación previa para una evaluación final.',
            'Último mes es referencia offline; mock sigue siendo simulación en el producto.',
        ],
    }


def _predictor_paths():
    from inspect import getfile
    from app.predictors.mock import MockPredictor
    from app.predictors.ses import SimpleExponentialSmoothingPredictor
    from app.predictors.evaluation import HistoricalMeanReference
    return {name: getfile(cls) for name, cls in zip(METHODS, (
        MockPredictor, HistoricalMeanReference, SimpleExponentialSmoothingPredictor))}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--csv', type=Path, required=True)
    parser.add_argument('--from-origin', default='2023-12')
    parser.add_argument('--to-origin', default='2024-06')
    parser.add_argument('--commit', required=True)
    args = parser.parse_args()
    try:
        print(json.dumps(compare_forecast(args.csv.read_bytes(), args.from_origin,
                                          args.to_origin, args.commit),
                         indent=2, ensure_ascii=False, allow_nan=False))
    except (ValueError, OSError) as error:
        raise SystemExit(f'Comparación P4-H fallida: {error}') from None
