"""Auditoría estructural P1; no evalúa modelos ni confirma cobertura real."""
import argparse
from collections import defaultdict
import csv
import hashlib
import io
import json
from math import gcd
from pathlib import Path
import re

COLUMNS = ['organization_id', 'month', 'category_id', 'category', 'amount_minor', 'currency']
EXPECTED = [f'{year}-{month:02}' for year in range(2023, 2026) for month in range(1, 13)]


def integer(value, positive=False):
    if not re.fullmatch(r'[0-9]+', value or ''):
        raise ValueError('ID o importe no entero.')
    result = int(value)
    if result < (1 if positive else 0) or result > 999999999999:
        raise ValueError('ID o importe fuera de límites.')
    return result


def audit(raw):
    reader = csv.DictReader(io.StringIO(raw.decode('utf-8-sig')), delimiter=';')
    if reader.fieldnames != COLUMNS:
        raise ValueError('Columnas inesperadas.')
    series, names, orgs, seen = defaultdict(dict), {}, set(), set()
    zeros = 0
    for line, row in enumerate(reader, 2):
        if None in row or any(value is None for value in row.values()):
            raise ValueError(f'Cantidad de columnas inválida en línea {line}.')
        org = integer(row['organization_id'], True)
        category = integer(row['category_id'], True)
        month = row['month']
        amount = integer(row['amount_minor'])
        if month not in EXPECTED or row['currency'] != 'ARS':
            raise ValueError(f'Mes fuera de 2023–2025 o moneda inválida en línea {line}.')
        key = (org, category)
        name = row['category'].strip()
        if not name or len(name) > 100 or (key in names and names[key] != name):
            raise ValueError(f'Nombre inconsistente en línea {line}.')
        if (key, month) in seen:
            raise ValueError(f'Clave duplicada en línea {line}.')
        seen.add((key, month))
        names[key] = name
        series[key][month] = amount
        orgs.add(org)
        zeros += amount == 0
    if not seen:
        raise ValueError('CSV vacío.')
    manifest = []
    totals = defaultdict(lambda: defaultdict(int))
    for (org, cat), amounts in sorted(series.items()):
        missing = sorted(set(EXPECTED) - amounts.keys())
        manifest.append({'organization_id': org, 'category_id': cat, 'category': names[(org, cat)],
                         'observed_months': len(amounts), 'missing_months': missing,
                         'structurally_complete': not missing, 'coverage_confirmed': None})
        for month, amount in amounts.items():
            totals[org][month] += amount
            if totals[org][month] > 9007199254740991:
                raise ValueError('Total organizacional fuera del límite del contrato Python.')
    # Sólo relaciones exactas de desarrollo; no estimar similitud ni usar reserva.
    exact, proportional = defaultdict(list), defaultdict(list)
    development = EXPECTED[:24]
    for org, amounts in sorted(totals.items()):
        cats = [m for m in manifest if m['organization_id'] == org]
        if any(m['missing_months'] for m in cats):
            continue
        values = tuple(amounts[m] for m in development)
        exact[values].append(org)
        divisor = 0
        for value in values:
            divisor = gcd(divisor, value)
        normalized = tuple(value // divisor for value in values) if divisor else values
        proportional[normalized].append(org)
    counts = {org: sum(m['organization_id'] == org for m in manifest) for org in orgs}
    complete = all(m['structurally_complete'] for m in manifest) and all(count == 8 for count in counts.values())
    return {
        'audit_version': 1, 'dataset_sha256': hashlib.sha256(raw).hexdigest(),
        'row_count': len(seen), 'organization_count': len(orgs), 'category_series_count': len(series),
        'expected_period': {'from': EXPECTED[0], 'to': EXPECTED[-1], 'months': len(EXPECTED)},
        'zero_rows': zeros, 'structurally_complete': complete, 'expected_categories_per_organization': 8,
        'organization_series': [{'organization_id': org, 'category_count': counts[org],
                                 'structurally_complete': counts[org] == 8 and all(m['structurally_complete'] for m in manifest if m['organization_id'] == org),
                                 'coverage_confirmed': None} for org in sorted(orgs)],
        'category_series': manifest,
        'development_relationships': {
            'period': '2023-01/2024-12',
            'identical_organization_totals': [ids for ids in exact.values() if len(ids) > 1],
            'exact_proportional_organization_totals': [ids for ids in proportional.values() if len(ids) > 1],
            'independence_demonstrated': False,
            'note': 'La ausencia de coincidencias exactas no prueba independencia; no detecta escalado con redondeo ni origen común.',
        },
        'final_evaluation_eligible': None,
        'provenance': {
            'source_type': 'synthetic_declared', 'official_calibration_applied': False,
            'generator_available': False, 'nominal_monthly_growth_declared': '4.5%',
            'coverage_scope': 'Totales simulados de ocho categorías por hogar y mes.',
            'reserve_prior_use': 'No hay evidencia disponible de uso para selección; no garantiza ausencia de uso.',
            'real_household_accuracy_supported': False,
        },
        'pending_evidence': ['Generador, fórmula/semilla exactas y correspondencia original/local.',
                             'Formalizar universo sintético elegible y responsables de cobertura.',
                             'Registrar revisión de uso previo de 2025 y congelación de reserva.',
                             'Aceptación manual y congelación del protocolo P2; P2-H para 4–6 meses.'],
        'forecast_metrics_computed': False,
    }


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--csv', type=Path, required=True)
    args = parser.parse_args()
    try:
        report = audit(args.csv.read_bytes())
        print(json.dumps(report, indent=2, ensure_ascii=False))
        if not report['structurally_complete']:
            raise SystemExit('Auditoría incompleta: revisar meses faltantes en el manifiesto.')
    except (ValueError, OSError) as error:
        raise SystemExit(f'Auditoría fallida: {error}') from None
