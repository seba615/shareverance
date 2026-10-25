"""Validación HTTP de previsiones del modelo configurado sólo en la demo aislada P1-D."""
import argparse
import json
from pathlib import Path
import urllib.error

from load_predictor_demo import Client, EMAIL, OUTSIDER, MONTHS, dataset, run


def verify_forecast(response, org_id, origin, horizon, expected, method='mock', simulated=True):
    if (response['organization_id'] != org_id or response['origin_month'] != origin
            or response['horizon_months'] != horizon or response['currency'] != 'ARS'
            or response['contract_version'] != '2' or response['simulated'] is not simulated):
        raise ValueError('Identidad, corte, horizonte o modo divergentes.')
    year, month = map(int, origin.split('-'))
    first = year * 12 + month - 1
    for series in [response['organization'], *response['categories']]:
        points = series['forecasts']
        insufficient = series['status'] == 'INSUFFICIENT_HISTORY'
        if len(points) != horizon or series['method'] != method:
            raise ValueError('Cantidad de puntos o método divergente.')
        amount = None if insufficient else expected.get(series.get('category_id'))
        if not insufficient and amount is None:
            raise ValueError('Serie ajena al conjunto esperado.')
        for h, point in enumerate(points, 1):
            y, m = divmod(first + h, 12)
            expected_amount = amount[h - 1] if isinstance(amount, list) else amount
            if (point['horizon'] != h or point['target_month'] != f'{y:04}-{m + 1:02}'
                    or point['estimated_amount_minor'] != expected_amount
                    or point['quality'] != {'status': 'NOT_EVALUATED', 'evaluation_id': None,
                                             'mae_minor': None, 'wape_percentage': None, 'samples': None}):
                raise ValueError('Trayectoria o calidad divergente; no se certifica la demo.')


def expected_http(client, path, status):
    try:
        client.call('GET', path)
    except urllib.error.HTTPError as error:
        if error.code == status:
            return
        raise
    raise ValueError(f'La consulta no devolvió el HTTP {status} esperado.')


def check(csv_path):
    from app.predictors.factory import create_predictor
    from app.config import load_settings
    model = create_predictor(load_settings())
    baseline = run(csv_path, check=True)
    rows, _ = dataset(csv_path.read_bytes())
    client = Client()
    client.account(EMAIL, True)
    org_id = baseline['organization_id']
    expected = {}
    for ext, internal in baseline['category_ids'].items():
        history = [next(row['amount'] for row in rows if row['category'] == ext and row['month'] == month)
                   for month in MONTHS]
        expected[internal] = model.forecast(history, 6)
    expected[None] = model.forecast([sum(row['amount'] for row in rows if row['month'] == month)
                                     for month in MONTHS], 6)
    base = f'/organizations/{org_id}/predictions/forecast'
    expenses_before = client.call('GET', f'/expenses/organization/{org_id}')
    coverage_before = client.call('GET', f'/organizations/{org_id}/analytics/coverage')
    checked = []
    for h in (4, 5, 6):
        response = client.call('GET', base + f'?originMonth=2024-12&horizonMonths={h}')
        verify_forecast(response, org_id, '2024-12', h, expected, model.method, model.simulated)
        if response['status'] != 'OK' or response['organization']['months_used'] != 24:
            raise ValueError('Historial suficiente no reconocido.')
        if {c['category_id'] for c in response['categories']} != set(expected) - {None}:
            raise ValueError('Categorías incompletas o ajenas.')
        if response['organization']['forecasts'][0]['estimated_amount_minor'] != baseline['prediction_minor']:
            raise ValueError('h=1 no equivale a monthly desde el mismo corte.')
        checked.append({'horizon': h, 'points': len(response['organization']['forecasts']),
                        'last_target': response['organization']['forecasts'][-1]['target_month']})
    automatic = client.call('GET', base)
    verify_forecast(automatic, org_id, '2024-12', 4, expected, model.method, model.simulated)
    for origin in ('2023-03', '2022-12'):
        response = client.call('GET', base + f'?originMonth={origin}&horizonMonths=6')
        verify_forecast(response, org_id, origin, 6, {}, model.method, model.simulated)
        if response['status'] != 'INSUFFICIENT_HISTORY' or response['organization']['months_used'] != 0:
            raise ValueError('Insuficiencia no reconocida.')
    for query in ('horizonMonths=3', 'horizonMonths=7', 'originMonth=2024-13', 'originMonth=9999-12'):
        expected_http(client, base + '?' + query, 400)
    outsider = Client()
    outsider.account(OUTSIDER, True)
    expected_http(outsider, base + '?horizonMonths=6', 403)
    if (expenses_before != client.call('GET', f'/expenses/organization/{org_id}')
            or coverage_before != client.call('GET', f'/organizations/{org_id}/analytics/coverage')):
        raise ValueError('Consultar previsiones alteró gastos o cobertura.')
    return {'scope': 'synthetic_demo_' + model.method, 'csv_sha256': baseline['csv_sha256'],
            'method': model.method, 'simulated': model.simulated,
            'organization_forecasts_minor': expected[None],
            'organization_id': org_id, 'cutoff': '2024-12', 'expense_count': baseline['expense_count'],
            'horizons_checked': checked, 'default_horizon': 4,
            'monthly_equivalence': True, 'insufficient_origins': ['2023-03', '2022-12'],
            'outsider_http_status': 403, 'invalid_parameters_http_status': 400,
            'expenses_and_coverage_unchanged': True, 'quality': 'NOT_EVALUATED'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--csv', type=Path, required=True)
    args = parser.parse_args()
    try:
        print(json.dumps(check(args.csv), indent=2))
    except (ValueError, OSError, KeyError) as error:
        raise SystemExit(f'Validación multihorizonte fallida: {error}') from None
