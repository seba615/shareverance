"""P3 inicial: evaluación experimental h=1 sólo en desarrollo, sin promoción."""
import argparse
from collections import defaultdict
import csv
import hashlib
from dataclasses import asdict
from decimal import Decimal, ROUND_HALF_UP
from fractions import Fraction
import io
import json
from pathlib import Path

from audit_predictor_dataset import audit, EXPECTED
from app.predictors.evaluation import calculate_metrics, HistoricalMeanReference
from app.predictors.mock import MockPredictor
from app.predictors.ses import SimpleExponentialSmoothingPredictor


def money(value):
    if isinstance(value, Fraction):
        value = Decimal(value.numerator) / value.denominator
    return float((value / 100).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP))


def evaluate(raw, from_month, to_month, commit, allow_reserve=False):
    development = EXPECTED[:24]
    all_months = EXPECTED
    valid_range = all_months if allow_reserve else development
    if from_month not in valid_range or to_month not in valid_range or from_month > to_month:
        if not allow_reserve and (from_month > '2024-12' or to_month > '2024-12'):
            raise ValueError('El rango objetivo debe ser inclusivo y estar en 2023–2024; reserva 2025 bloqueada.')
        raise ValueError(f'El rango objetivo debe ser inclusivo y estar entre {valid_range[0]} y {valid_range[-1]}.')
    manifest = audit(raw)
    if any(o['category_count'] != 8 for o in manifest['organization_series']):
        raise ValueError('Universo de categorías incompleto: se requieren ocho por organización.')
    values, categories = defaultdict(dict), defaultdict(set)
    for row in csv.DictReader(io.StringIO(raw.decode('utf-8-sig')), delimiter=';'):
        org, cat = int(row['organization_id']), int(row['category_id'])
        categories[org].add(cat)
        if allow_reserve or row['month'] <= '2024-12':
            values[('category', org, cat)][row['month']] = int(row['amount_minor'])
    for org, cats in categories.items():
        for month in (all_months if allow_reserve else development):
            if all(month in values[('category', org, cat)] for cat in cats):
                values[('organization', org, None)][month] = sum(values[('category', org, cat)][month] for cat in cats)
        values[('organization', org, None)]  # Incluir incluso serie organizacional vacía.
    models = {'ses': SimpleExponentialSmoothingPredictor(alpha=0.3),
              'last_month': MockPredictor(), 'historical_mean': HistoricalMeanReference()}
    targets = [m for m in (all_months if allow_reserve else development) if from_month <= m <= to_month]
    details, series_reports = [], []
    level_data = defaultdict(list)
    for (level, org, cat), amounts in sorted(values.items(), key=lambda p: (p[0][0], p[0][1], p[0][2] or 0)):
        errors = defaultdict(list)
        series_details = []
        for target in targets:
            earlier = (all_months if allow_reserve else development)[:(all_months if allow_reserve else development).index(target)]
            history = []
            for month in reversed(earlier):
                if month not in amounts:
                    break
                history.insert(0, amounts[month])
            status = 'MISSING_TARGET' if target not in amounts else ('INSUFFICIENT_HISTORY' if len(history) < 6 else 'OK')
            point = {'level': level, 'organization_id': org, 'category_id': cat, 'target_month': target,
                     'horizon': 1, 'status': status, 'available_history_months': len(history),
                     'actual_minor': amounts.get(target), 'predictions': {}}
            if status == 'OK':
                for name, model in models.items():
                    prediction = model.predict(history)
                    actual = amounts[target]
                    point['predictions'][name] = {'estimated_minor': prediction,
                                                  'signed_error_minor': prediction - actual,
                                                  'absolute_error_minor': abs(prediction - actual)}
                    errors[name].append((actual, prediction))
            series_details.append(point)
        metrics = {}
        for name, pairs in errors.items():
            actuals, predictions = zip(*pairs)
            metrics[name] = {**asdict(calculate_metrics(actuals, predictions)),
                             'mean_signed_error_ars': money(Decimal(sum(p-a for a, p in pairs)) / len(pairs))}
            if len(pairs) == len(targets):
                level_data[(level, name)].append(pairs)
        complete = all(point['status'] == 'OK' for point in series_details)
        series_reports.append({'level': level, 'organization_id': org, 'category_id': cat,
                               'complete_requested_targets': complete, 'metrics': metrics})
        details.extend(series_details)
    summaries = {}
    for level in ('organization', 'category'):
        expected_series = sum(s['level'] == level for s in series_reports)
        eligible = sum(s['level'] == level and s['complete_requested_targets'] for s in series_reports)
        metrics = {}
        for name in models:
            groups = level_data[(level, name)]
            if not groups:
                metrics[name] = None
                continue
            # Agregar desde errores exactos, no desde métricas redondeadas.
            maes = [Fraction(sum(abs(a-p) for a, p in pairs), len(pairs)) for pairs in groups]
            absolute = sum(abs(a-p) for pairs in groups for a, p in pairs)
            actual_total = sum(a for pairs in groups for a, p in pairs)
            exact_mae = sum(maes, Fraction()) / len(maes)
            metrics[name] = {'equal_series_mae_ars': money(exact_mae),
                             'equal_series_mae_minor_fraction': {'numerator': exact_mae.numerator, 'denominator': exact_mae.denominator},
                             'pooled_wape_percentage': float((Decimal(absolute) * 100 / actual_total).quantize(Decimal('0.01'), rounding=ROUND_HALF_UP)) if actual_total else None,
                             'series_count': len(groups), 'samples': sum(map(len, groups))}
        summaries[level] = {'expected_series': expected_series, 'complete_series': eligible,
                            'excluded_series': expected_series - eligible, 'metrics': metrics}
    is_reserve = any(t > '2024-12' for t in targets)
    return {'report_version': 1, 'dataset_sha256': manifest['dataset_sha256'], 'code_commit': commit,
            'evaluator_sha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
            'scope': 'synthetic_reserve_evaluation' if is_reserve else 'synthetic_development_only',
            'coverage_policy': 'synthetic_declared_not_real_coverage',
            'configuration': {'from_month': from_month, 'to_month': to_month, 'horizon': 1,
                              'min_history_months': 6, 'alpha': 0.3, 'training': 'continuous_expanding_history_before_target',
                              'allow_reserve': allow_reserve,
                              'method_versions': {name: model.model_version for name, model in models.items()}},
            'accepted': None, 'model_promoted': False, 'reserve_evaluated': is_reserve,
            'levels': summaries, 'series': series_reports, 'points': details,
            'limitations': manifest['pending_evidence'] + ['No representa precisión sobre hogares reales.',
                           'Exclusiones técnicas no son una selección final del universo de aceptación.']}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--csv', type=Path, required=True)
    parser.add_argument('--from-month', required=True)
    parser.add_argument('--to-month', required=True)
    parser.add_argument('--commit', required=True)
    parser.add_argument('--allow-reserve', action='store_true', default=False)
    args = parser.parse_args()
    try:
        print(json.dumps(evaluate(args.csv.read_bytes(), args.from_month, args.to_month, args.commit, allow_reserve=args.allow_reserve), indent=2, ensure_ascii=False, allow_nan=False))
    except (ValueError, OSError) as error:
        raise SystemExit(f'Evaluación fallida: {error}') from None
