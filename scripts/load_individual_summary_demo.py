"""Casos HTTP sintéticos de resumen; exclusivamente en el proyecto demo aislado."""
from decimal import Decimal
import json
import os
from load_predictor_demo import Client, EMAIL, only

NAME = 'Demo resumen individual - repartos'
MEMBER_EMAIL = 'resumen.miembro@example.test'
CASES = [('Reparto con centavo', '10.01', 'ALL_MEMBERS', False),
         ('Reparto exacto', '100.00', 'ALL_MEMBERS', False),
         ('Individual del miembro', '20.00', 'INDIVIDUAL', False),
         ('Anulado excluido', '9.99', 'ALL_MEMBERS', True)]


def run():
    if os.environ.get('POSTGRES_DB') != 'shareverance_predictor_demo':
        raise ValueError('Sólo se permite el entorno demo aislado.')
    admin = Client()
    admin_user = admin.account(EMAIL, False)
    member_client = Client()
    member_user = member_client.account(MEMBER_EMAIL, False)
    matches = [o for o in admin.call('GET', '/organizations') if o['name'] == NAME]
    org = only(matches, 'Organización demo ambigua.') if matches else admin.call('POST', '/organizations', {
        'name': NAME, 'type': 'HOUSEHOLD', 'currency': 'ARS'})
    if not org['isActive'] or org['currency'] != 'ARS':
        raise ValueError('Organización demo divergente.')
    org_id = org['id']
    members = admin.call('GET', f'/memberships/organization/{org_id}')
    if not any(m['userId'] == member_user['id'] for m in members):
        admin.call('POST', '/memberships', {'organizationId': org_id, 'email': MEMBER_EMAIL, 'role': 'USER'})
        members = admin.call('GET', f'/memberships/organization/{org_id}')
    if len(members) != 2 or any(not m['isActive'] for m in members):
        raise ValueError('Se necesitan exactamente dos miembros activos; no se modifica la composición.')
    owner = only([m for m in members if m['userId'] == admin_user['id'] and m['role'] == 'ADMIN'], 'ADMIN divergente.')
    member = only([m for m in members if m['userId'] == member_user['id'] and m['role'] == 'USER'], 'USER divergente.')
    cats = admin.call('GET', f'/categories/organization/{org_id}')
    if not cats:
        cats = [admin.call('POST', f'/categories/organization/{org_id}', {'name': 'Pruebas de reparto',
            'description': 'Fixture sintético #95', 'color': '#3B82F6'})]
    category = only(cats, 'Categorías demo divergentes.')
    if category['name'] != 'Pruebas de reparto' or not category['isActive']:
        raise ValueError('Categoría demo divergente.')
    existing = admin.call('GET', f'/expenses/organization/{org_id}')
    if len({d['expense']['description'] for d in existing}) != len(existing):
        raise ValueError('Marcadores duplicados.')
    markers = {f'Demo resumen #95 | {name}' for name, *_ in CASES}
    if any(d['expense']['description'] not in markers for d in existing):
        raise ValueError('Hay gastos ajenos a esta demostración; no se sobrescriben.')
    indexed = {d['expense']['description']: d for d in existing}
    created = 0
    for name, amount, mode, annulled in CASES:
        marker = f'Demo resumen #95 | {name}'
        holder = member['id'] if mode == 'INDIVIDUAL' else owner['id']
        detail = indexed.get(marker)
        if detail is None:
            detail = admin.call('POST', '/expenses', {'organizationId': org_id, 'paidByMembershipId': holder,
                'categoryId': category['id'], 'amount': amount, 'expenseDate': '2024-12-15',
                'description': marker, 'splitMode': mode})
            if annulled:
                admin.call('DELETE', f"/expenses/{detail['expense']['id']}")
                detail = admin.call('GET', f"/expenses/{detail['expense']['id']}")
            created += 1
        expense = detail['expense']
        if (expense['organizationId'] != org_id or expense['categoryId'] != category['id']
                or expense['paidByMembershipId'] != holder or expense['createdByUserId'] != admin_user['id']
                or Decimal(str(expense['amount'])) != Decimal(amount) or expense['splitType'] != ('INDIVIDUAL' if mode == 'INDIVIDUAL' else 'EQUAL')
                or expense['expenseDate'] != '2024-12-15' or expense['isActive'] == annulled):
            raise ValueError('Gasto divergente; no se sobrescribe.')
        cents = int(Decimal(amount) * 100)
        ids = [member['id']] if mode == 'INDIVIDUAL' else sorted([owner['id'], member['id']])
        expected = {mid: Decimal(cents // len(ids) + (i < cents % len(ids))) / 100 for i, mid in enumerate(ids)}
        actual = {p['userOrganizationId']: Decimal(str(p['amount'])) for p in detail['splits']}
        if actual != expected or len(detail['splits']) != len(expected):
            raise ValueError('Partes divergentes.')
    path = f'/organizations/{org_id}/expenses/my-summary'
    own = admin.call('GET', path)
    other = admin.call('GET', path + f"?membershipId={member['id']}")
    user_own = member_client.call('GET', path)
    first = owner['id'] < member['id']
    expected_owner = Decimal('55.01') if first else Decimal('55.00')
    expected_member = Decimal('75.00') if first else Decimal('75.01')
    if (own['totalAmount'] != expected_owner or own['expenseCount'] != 2
            or other['totalAmount'] != expected_member or other['expenseCount'] != 3
            or user_own != other):
        raise ValueError('Resumen no conciliado con los casos de prueba.')
    import urllib.error
    try:
        member_client.call('GET', path + f"?membershipId={owner['id']}")
    except urllib.error.HTTPError as error:
        if error.code != 403:
            raise
    else:
        raise ValueError('USER pudo consultar otro miembro.')
    return {'organization_id': org_id, 'organization_name': NAME, 'created_expenses': created,
            'admin_membership_id': owner['id'], 'user_membership_id': member['id'],
            'admin_total': own['totalAmount'], 'user_total': other['totalAmount'],
            'admin_expense_count': own['expenseCount'], 'user_expense_count': other['expenseCount'],
            'user_other_status': 403, 'annulled_excluded': True}


if __name__ == '__main__':
    print(json.dumps(run(), ensure_ascii=False, indent=2, default=str))
