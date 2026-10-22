"""P1-D: carga HTTP exclusiva del entorno Compose de demostración, sin SQL."""
import argparse
import calendar
import csv
from decimal import Decimal
import hashlib
import http.cookiejar
import io
import json
from pathlib import Path
import urllib.error
import urllib.request

BASE = 'http://java-service:8080/api/v1'
EMAIL = 'predictor.admin@example.test'
OUTSIDER = 'predictor.sinacceso@example.test'
PASSWORD = 'DemoPredictor123!'
NAME = 'Demo CSV 1 - desarrollo 2023-2024'
MONTHS = [f'{year}-{month:02}' for year in (2023, 2024) for month in range(1, 13)]


def dataset(raw):
    """Valida la selección completa antes de realizar cualquier escritura HTTP."""
    reader = csv.DictReader(io.StringIO(raw.decode('utf-8-sig')), delimiter=';')
    if reader.fieldnames != ['organization_id', 'month', 'category_id', 'category', 'amount_minor', 'currency']:
        raise ValueError('Columnas CSV inesperadas.')
    selected, seen, names = [], set(), {}
    for row in reader:
        if row['organization_id'] != '1' or row['month'] not in MONTHS:
            continue
        category = row['category_id']
        if not category.isascii() or not category.isdigit() or int(category) <= 0:
            raise ValueError('ID de categoría inválido.')
        amount = row['amount_minor']
        if row['currency'] != 'ARS' or not amount.isascii() or not amount.isdigit() or not 0 < int(amount) <= 999999999999:
            raise ValueError('Importe o moneda inválidos para un gasto.')
        key = (row['month'], category)
        if key in seen:
            raise ValueError('Clave mes/categoría duplicada.')
        seen.add(key)
        name = row['category'].strip()
        if not name or (category in names and names[category] != name):
            raise ValueError('Nombre de categoría inconsistente.')
        names[category] = name
        selected.append(dict(month=row['month'], category=category, amount=int(amount)))
    if len(names) != 8 or len(set(names.values())) != 8 or len(selected) != 192:
        raise ValueError('Se requieren ocho categorías y 192 filas de desarrollo.')
    if seen != {(month, category) for month in MONTHS for category in names}:
        raise ValueError('La selección tiene meses o categorías incompletos.')
    return sorted(selected, key=lambda r: (r['month'], int(r['category']))), names


class Client:
    def __init__(self):
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.csrf = self.call('GET', '/auth/csrf')['csrfToken']

    def call(self, method, path, body=None):
        data = json.dumps(body).encode() if body is not None else (b'' if method in ('POST', 'PUT') else None)
        headers = {'Content-Type': 'application/json', 'X-XSRF-TOKEN': getattr(self, 'csrf', '')}
        request = urllib.request.Request(BASE + path, data=data, headers=headers, method=method)
        with self.opener.open(request, timeout=30) as response:
            return json.loads(response.read(), parse_float=Decimal)

    def account(self, email, check):
        try:
            return self.call('POST', '/auth/login', {'email': email, 'password': PASSWORD})['user']
        except urllib.error.HTTPError as error:
            if error.code not in (400, 401) or check:
                raise
        return self.call('POST', '/auth/register', {'fullName': 'Usuario demo predictor', 'email': email, 'password': PASSWORD})['user']


def only(items, message):
    if len(items) != 1:
        raise ValueError(message)
    return items[0]


def marker(row, digest):
    return f"P1-D total mensual demo | org=1 | cat={row['category']} | mes={row['month']} | sha={digest}"


def verify_expense(detail, row, category_id, member_id, org_id, user_id):
    expense = detail['expense']
    year, month = map(int, row['month'].split('-'))
    day = f'{row["month"]}-{calendar.monthrange(year, month)[1]:02}'
    if (expense['organizationId'] != org_id or expense['categoryId'] != category_id
            or expense['paidByMembershipId'] != member_id or expense['createdByUserId'] != user_id
            or Decimal(str(expense['amount'])) * 100 != row['amount']
            or expense['expenseDate'] != day or not expense['isActive']
            or expense['splitType'] != 'INDIVIDUAL' or expense.get('receiptUrl')):
        raise ValueError('Gasto demo divergente; no se sobrescribe.')
    splits = detail['splits']
    if (len(splits) != 1 or splits[0]['userOrganizationId'] != member_id
            or Decimal(str(splits[0]['amount'])) * 100 != row['amount']):
        raise ValueError('Partes del gasto demo divergentes.')


def run(path, check=False):
    raw = path.read_bytes()
    rows, names = dataset(raw)
    digest = hashlib.sha256(raw).hexdigest()
    client = Client()
    user = client.account(EMAIL, check)
    orgs = [o for o in client.call('GET', '/organizations') if o['name'] == NAME]
    if not orgs and not check:
        orgs = [client.call('POST', '/organizations', {'name': NAME, 'type': 'HOUSEHOLD', 'currency': 'ARS'})]
    org = only(orgs, 'Organización demo ausente o ambigua.')
    org_id = org['id']
    if not org['isActive'] or org['currency'] != 'ARS':
        raise ValueError('Organización demo no operable.')
    members = client.call('GET', f'/memberships/organization/{org_id}')
    member = only([m for m in members if m['userId'] == user['id'] and m['isActive'] and m['role'] == 'ADMIN'], 'Falta ADMIN demo activo.')
    categories = client.call('GET', f'/categories/organization/{org_id}')
    mapping = {}
    for ext, name in names.items():
        description = f'P1-D categoría CSV {ext}; datos públicos adaptados de demostración'
        matches = [c for c in categories if c['name'] == name]
        if not matches and not check:
            matches = [client.call('POST', f'/categories/organization/{org_id}', {'name': name, 'description': description, 'color': '#3B82F6'})]
        category = only(matches, 'Categoría demo ausente o ambigua.')
        if not category['isActive'] or category['description'] != description:
            raise ValueError('Categoría demo divergente.')
        mapping[ext] = category['id']
    existing = client.call('GET', f'/expenses/organization/{org_id}')
    expected = {marker(row, digest) for row in rows}
    if any(d['expense']['description'] not in expected for d in existing):
        raise ValueError('Hay gastos ajenos o de otro CSV/corte en la organización demo; no se certifica cobertura.')
    indexed = {}
    for detail in existing:
        key = detail['expense']['description']
        if key in indexed:
            raise ValueError('Gastos demo duplicados.')
        indexed[key] = detail
    created = 0
    for row in rows:
        description = marker(row, digest)
        detail = indexed.get(description)
        if detail is None:
            if check:
                raise ValueError('Carga incompleta.')
            year, month = map(int, row['month'].split('-'))
            detail = client.call('POST', '/expenses', {
                'organizationId': org_id, 'paidByMembershipId': member['id'], 'categoryId': mapping[row['category']],
                'amount': f"{row['amount'] // 100}.{row['amount'] % 100:02}",
                'expenseDate': f'{row["month"]}-{calendar.monthrange(year, month)[1]:02}',
                'description': description, 'splitMode': 'INDIVIDUAL',
            })
            created += 1
        verify_expense(detail, row, mapping[row['category']], member['id'], org_id, user['id'])
    # Verificar nuevamente desde servidor antes de confirmar cobertura.
    final = client.call('GET', f'/expenses/organization/{org_id}')
    if len(final) != len(rows) or {d['expense']['description'] for d in final} != expected:
        raise ValueError('Conteo o identidad de gastos divergente tras la carga.')
    lookup = {marker(row, digest): row for row in rows}
    for detail in final:
        row = lookup[detail['expense']['description']]
        verify_expense(detail, row, mapping[row['category']], member['id'], org_id, user['id'])
    coverage = client.call('GET', f'/organizations/{org_id}/analytics/coverage')
    confirmed = {c['month'][:7] for c in coverage if c['confirmed']}
    if confirmed - set(MONTHS):
        raise ValueError('Cobertura fuera del corte de demostración.')
    for month in MONTHS:
        if month not in confirmed:
            if check:
                raise ValueError('Cobertura incompleta.')
            client.call('PUT', f'/organizations/{org_id}/analytics/coverage/{month}')
    coverage = client.call('GET', f'/organizations/{org_id}/analytics/coverage')
    if {c['month'] for c in coverage if c['confirmed']} != set(MONTHS):
        raise ValueError('Cobertura final no coincide con el corte.')
    analytics = client.call('GET', f'/organizations/{org_id}/analytics/monthly?fromMonth=2023-01&toMonth=2024-12')
    evolution = analytics['evolution']
    if len(evolution) != 24 or {m['month'] for m in evolution} != set(MONTHS):
        raise ValueError('Analítica no contiene los meses esperados.')
    for month in evolution:
        actual = {mapping[row['category']]: row['amount'] for row in rows if row['month'] == month['month']}
        reported = {c['category_id']: c['amount_minor'] for c in month['category_totals']}
        if not month['covered'] or reported != actual or month['total_amount_minor'] != sum(actual.values()):
            raise ValueError('Analítica Python no concilia con CSV.')
    # Acceso humano habitual; conservar el usuario técnico como autor de la carga.
    human_members = [m for m in members if m['userEmail'] == 'admin@shareverance.com']
    if not human_members and not check:
        human_members = [client.call('POST', '/memberships', {
            'organizationId': org_id, 'email': 'admin@shareverance.com', 'role': 'ADMIN',
        })]
    human_member = only(human_members, 'Falta acceso del ADMIN habitual; ejecutar load.')
    if not human_member['isActive'] or human_member['role'] != 'ADMIN':
        raise ValueError('El ADMIN habitual no tiene membresía ADMIN activa; no se altera automáticamente.')
    prediction = client.call('GET', f'/organizations/{org_id}/predictions/monthly')
    last_total = sum(row['amount'] for row in rows if row['month'] == '2024-12')
    if (prediction['status'] != 'OK' or prediction['target_month'] != '2025-01'
            or prediction['simulated'] is not True or prediction['organization']['estimated_amount_minor'] != last_total):
        raise ValueError('Predicción mock no concilia con el último mes cargado.')
    outsider = Client()
    outsider.account(OUTSIDER, check)
    try:
        outsider.call('GET', f'/organizations/{org_id}/predictions/monthly')
    except urllib.error.HTTPError as error:
        if error.code != 403:
            raise
    else:
        raise ValueError('Usuario ajeno pudo consultar la predicción.')
    return {'csv_sha256': digest, 'csv_organization_id': 1, 'organization_id': org_id,
            'organization_name': NAME, 'category_ids': mapping, 'expense_count': len(final),
            'created_this_run': created, 'covered_months': len(MONTHS), 'cutoff': '2024-12',
            'total_minor': sum(row['amount'] for row in rows), 'prediction_month': prediction['target_month'],
            'prediction_minor': last_total, 'analytics_months_verified': len(evolution), 'outsider_http_status': 403}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--csv', type=Path, required=True)
    parser.add_argument('--check-only', action='store_true')
    args = parser.parse_args()
    try:
        print(json.dumps(run(args.csv, args.check_only), indent=2))
    except urllib.error.HTTPError as error:
        raise SystemExit(f'Error HTTP {error.code}; carga detenida, sin reintento automático.') from None
    except (ValueError, OSError, KeyError) as error:
        raise SystemExit(f'Verificación fallida: {error}') from None
