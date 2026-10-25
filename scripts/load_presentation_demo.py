"""Una organización de presentación, cargada por Java en el proyecto demo aislado."""
import argparse
import calendar
from datetime import date, datetime
from decimal import Decimal
import hashlib
import json
import os
from pathlib import Path
import urllib.error
import urllib.request
import uuid
from zoneinfo import ZoneInfo
from load_predictor_demo import Client, BASE, dataset, only

PREFIX = 'Presentación V1 | '


def shift_month(month, delta):
    year, number = map(int, month.split('-'))
    value = year * 12 + number - 1 + delta
    return f'{value // 12:04}-{value % 12 + 1:02}'


def parts(cents, member_ids):
    ids = sorted(member_ids)
    if not ids or len(set(ids)) != len(ids) or cents < len(ids):
        raise ValueError('Participantes/importe inválidos.')
    return {mid: Decimal(cents // len(ids) + (i < cents % len(ids))) / 100 for i, mid in enumerate(ids)}


def history_plan(raw, cutoff):
    rows, names = dataset(raw)
    year, month = map(int, cutoff.split('-'))
    delta = (year - 2024) * 12 + month - 12
    result = []
    for row in rows:
        shifted = shift_month(row['month'], delta)
        y, m = map(int, shifted.split('-'))
        kind = ('equal', 'admin', 'user')[int(row['category']) % 3]
        result.append({'marker': PREFIX + f"CSV org=1 mes={row['month']} cat={row['category']}",
            'date': f'{shifted}-{calendar.monthrange(y,m)[1]:02}', 'category': row['category'],
            'cents': row['amount'], 'kind': kind, 'annulled': False})
    return result, names, delta


class PresentationClient(Client):
    def account_named(self, email, name, check, allow_create=True):
        password = os.environ.get('PRESENTATION_PASSWORD', '')
        if not password:
            raise ValueError('Falta PRESENTATION_PASSWORD en la configuración local no versionada.')
        try:
            return self.call('POST', '/auth/login', {'email': email, 'password': password})['user']
        except urllib.error.HTTPError as error:
            if error.code not in (400,401) or check or not allow_create:
                raise
        return self.call('POST', '/auth/register', {'email': email, 'fullName': name, 'password': password})['user']

    def upload(self, org_id, path):
        boundary = 'presentation-' + uuid.uuid4().hex
        body = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{path.name}"\r\n'
                'Content-Type: image/png\r\n\r\n').encode() + path.read_bytes() + f'\r\n--{boundary}--\r\n'.encode()
        request = urllib.request.Request(BASE + f'/organizations/{org_id}/receipts', data=body,
            headers={'Content-Type': 'multipart/form-data; boundary='+boundary, 'X-XSRF-TOKEN': self.csrf}, method='POST')
        with self.opener.open(request, timeout=30) as response:
            return json.loads(response.read())


def run(csv_path, assets, check=False):
    if os.environ.get('POSTGRES_DB') != 'shareverance_predictor_demo':
        raise ValueError('La presentación sólo puede prepararse en la base demo aislada.')
    config = json.loads((assets/'config.json').read_text(encoding='utf-8'))
    today = datetime.now(ZoneInfo('America/Argentina/Buenos_Aires')).date()
    cutoff = config['cutoff']
    if cutoff >= today.strftime('%Y-%m') or date.fromisoformat(config['receipt_date']) > today:
        raise ValueError('El corte debe estar cerrado y el comprobante no puede ser futuro.')
    raw = csv_path.read_bytes()
    plan, names, delta = history_plan(raw, cutoff)
    admin, user, invited, outsider = [PresentationClient() for _ in range(4)]
    actors = [(admin,'admin_email','Admin presentación'),(user,'user_email','Usuario presentación'),
              (invited,'invite_email','Invitado presentación'),(outsider,'outsider_email','Ajeno presentación')]
    identities = {key: client.account_named(config[key], name, check, allow_create=key!='admin_email') for client,key,name in actors}
    orgs = [o for o in admin.call('GET','/organizations') if o['name'] == config['name']]
    if not orgs and not check:
        orgs = [admin.call('POST','/organizations',{'name':config['name'],'type':'HOUSEHOLD','currency':'ARS'})]
    org = only(orgs, 'Organización principal ausente o ambigua.')
    org_id = org['id']
    if not org['isActive'] or org['currency'] != 'ARS':
        raise ValueError('Organización principal divergente.')
    members = admin.call('GET',f'/memberships/organization/{org_id}')
    if not any(m['userId'] == identities['user_email']['id'] for m in members) and not check:
        admin.call('POST','/memberships',{'organizationId':org_id,'email':config['user_email'],'role':'USER'})
        members = admin.call('GET',f'/memberships/organization/{org_id}')
    owner = only([m for m in members if m['userId'] == identities['admin_email']['id']], 'Falta ADMIN.')
    member = only([m for m in members if m['userId'] == identities['user_email']['id']], 'Falta USER.')
    if not owner['isActive'] or owner['role'] != 'ADMIN' or not member['isActive'] or member['role'] != 'USER':
        raise ValueError('Roles de referencia alterados; no se sobrescriben.')
    ids = [owner['id'], member['id']]
    categories = admin.call('GET',f'/categories/organization/{org_id}')
    names = dict(names, shared='Repartos de prueba', live='Comprobantes de prueba')
    mapping = {}
    for key,name in names.items():
        matches = [c for c in categories if c['description'] == PREFIX+'categoría '+key]
        if not matches and not check:
            matches = [admin.call('POST',f'/categories/organization/{org_id}',{'name':name,
                'description':PREFIX+'categoría '+key,'color':'#3B82F6'})]
        cat = only(matches,'Categoría de referencia ausente o duplicada.')
        if cat['name'] != name:
            raise ValueError('Categoría de referencia renombrada; no se sobrescribe.')
        mapping[key] = cat['id']
    historical = max(k for k in mapping if k.isdigit())
    current = shift_month(cutoff, 1)
    for label,cents,kind,day,annulled in [('Iguales con centavo',1001,'equal',1,False),
            ('Iguales exactos',10000,'equal',5,False),('Individual USER',2000,'user',10,False),
            ('Anulado',999,'equal',12,True),('OCR sintético',123456,'user',15,False)]:
        plan.append({'marker':PREFIX+label,'cents':cents,'kind':kind,'date':f'{current}-{day:02}',
            'category':'live' if label=='OCR sintético' else 'shared','annulled':annulled})
    if plan[-1]['date'] != config['receipt_date']:
        raise ValueError('Fecha OCR y corte de configuración inconsistentes.')
    existing = admin.call('GET',f'/expenses/organization/{org_id}')
    indexed = {}
    for detail in existing:
        marker = detail['expense']['description']
        if marker in indexed and marker.startswith(PREFIX):
            raise ValueError('Marcador de referencia duplicado.')
        indexed[marker] = detail
    receipt_marker = PREFIX+'OCR sintético'
    receipt_id = indexed.get(receipt_marker,{}).get('expense',{}).get('receiptUrl')
    if not receipt_id and not check:
        receipts = admin.call('GET',f'/organizations/{org_id}/receipts')
        candidates = [r for r in receipts if r['originalFilename']=='comprobante-sintetico.png' and r.get('expenseId') is None]
        receipt_id = candidates[0]['receiptId'] if len(candidates)==1 else admin.upload(org_id,assets/'comprobante-sintetico.png')['receiptId']
    if not receipt_id:
        raise ValueError('Falta comprobante sintético; ejecutar prepare.')
    ocr = admin.call('POST',f'/organizations/{org_id}/receipts/{receipt_id}/ocr')
    if ocr.get('date') != config['receipt_date'] or Decimal(str(ocr.get('amount'))) != Decimal('1234.56'):
        raise ValueError('OCR no reconoce fecha/importe esperados; revisar antes de presentar.')
    created = 0
    for item in plan:
        participants = ids if item['kind']=='equal' else [owner['id'] if item['kind']=='admin' else member['id']]
        expected = parts(item['cents'],participants)
        detail = indexed.get(item['marker'])
        holder = owner['id'] if item['kind']!='user' else member['id']
        if detail is None:
            if check:
                raise ValueError('Faltan gastos de referencia; ejecutar prepare.')
            detail = admin.call('POST','/expenses',{'organizationId':org_id,'paidByMembershipId':holder,
                'categoryId':mapping[item['category']],'amount':str(Decimal(item['cents'])/100),
                'expenseDate':item['date'],'description':item['marker'],
                'splitMode':'SPECIFIC_GROUP' if item['kind']=='equal' else 'INDIVIDUAL',
                'splitMemberIds':ids if item['kind']=='equal' else None,
                'receiptUrl':receipt_id if item['marker']==receipt_marker else None})
            if item['annulled']:
                admin.call('DELETE',f"/expenses/{detail['expense']['id']}")
                detail = admin.call('GET',f"/expenses/{detail['expense']['id']}")
            created += 1
        expense = detail['expense']
        actual_parts = {p['userOrganizationId']:Decimal(str(p['amount'])) for p in detail['splits']}
        if (expense['organizationId']!=org_id or expense['createdByUserId']!=identities['admin_email']['id']
                or expense['categoryId']!=mapping[item['category']]
                or expense['paidByMembershipId']!=holder or expense['expenseDate']!=item['date']
                or Decimal(str(expense['amount']))*100!=item['cents'] or expense['isActive']==item['annulled']
                or actual_parts!=expected or len(detail['splits'])!=len(expected)
                or expense['splitType']!=('EQUAL' if item['kind']=='equal' else 'INDIVIDUAL')):
            raise ValueError('Gasto/partes de referencia divergentes; no se sobrescriben: '+item['marker'])
        if item['marker']==receipt_marker and expense['receiptUrl']!=receipt_id:
            raise ValueError('Comprobante no vinculado al gasto de referencia.')
    # Conservar una categoría histórica inactiva después de registrar el historial.
    cat = next(c for c in admin.call('GET',f'/categories/organization/{org_id}') if c['id']==mapping[historical])
    if cat['isActive']:
        if check:
            raise ValueError('Falta categoría histórica inactiva.')
        admin.call('DELETE',f"/categories/{cat['id']}")
    months = sorted({p['date'][:7] for p in plan if p['marker'].startswith(PREFIX+'CSV')})
    coverage = admin.call('GET',f'/organizations/{org_id}/analytics/coverage')
    confirmed = {c['month'] for c in coverage if c['confirmed']}
    for month in months:
        if month not in confirmed:
            if check:
                raise ValueError('Falta cobertura de referencia.')
            admin.call('PUT',f'/organizations/{org_id}/analytics/coverage/{month}')
    analytics = admin.call('GET',f'/organizations/{org_id}/analytics/monthly?fromMonth={months[0]}&toMonth={cutoff}')
    history = []
    for row in analytics['evolution']:
        expected = sum(p['cents'] for p in plan if p['date'][:7]==row['month'] and not p['annulled'])
        if not row['covered'] or row['total_amount_minor'] != expected:
            raise ValueError('Analítica histórica no concilia; no certificar meses alterados.')
        history.append(expected)
    if len(history)!=24:
        raise ValueError('Se requieren 24 meses históricos.')
    from app.config import load_settings
    from app.predictors.factory import create_predictor
    model = create_predictor(load_settings())
    horizons=[]
    for horizon in (4,5,6):
        forecast = admin.call('GET',f'/organizations/{org_id}/predictions/forecast?originMonth={cutoff}&horizonMonths={horizon}')
        amounts = [p['estimated_amount_minor'] for p in forecast['organization']['forecasts']]
        if forecast['organization']['status']!='OK' or amounts!=model.forecast(history,horizon) or forecast['simulated']:
            raise ValueError('Previsión no concilia con el historial.')
        horizons.append(horizon)
    summary_path = f'/organizations/{org_id}/expenses/my-summary'
    own = admin.call('GET',summary_path)
    other = admin.call('GET',summary_path+f"?membershipId={member['id']}")
    if user.call('GET',summary_path)!=other:
        raise ValueError('Resumen USER no coincide con ADMIN seleccionando el miembro.')
    final = admin.call('GET',f'/expenses/organization/{org_id}')
    for mid,summary in [(owner['id'],own),(member['id'],other)]:
        amounts = [Decimal(str(p['amount'])) for d in final if d['expense']['isActive']
                   for p in d['splits'] if p['userOrganizationId']==mid]
        if summary['totalAmount']!=sum(amounts,Decimal(0)) or summary['expenseCount']!=len(amounts):
            raise ValueError('Resumen no concilia con partes vigentes.')
    for client,path in [(user,summary_path+f"?membershipId={owner['id']}"),
                        (outsider,summary_path), (outsider,f'/organizations/{org_id}/predictions/monthly')]:
        try:
            client.call('GET',path)
        except urllib.error.HTTPError as error:
            if error.code!=403:
                raise
        else:
            raise ValueError('Acceso indebido de demostración.')
    return {'organization_id':org_id,'organization_name':config['name'],'created_expenses':created,
        'reference_expenses':len(plan),'csv_sha256':hashlib.sha256(raw).hexdigest(),'date_shift_months':delta,
        'history_from':months[0],'cutoff':cutoff,'covered_months':len(history),'forecast_horizons':horizons,
        'history_total_ars':str(Decimal(sum(history))/100),'current_month':current,
        'admin_total':str(own['totalAmount']),'user_total':str(other['totalAmount']),
        'admin_expenses':own['expenseCount'],'user_expenses':other['expenseCount'],
        'receipt_id':receipt_id,'ocr_date':ocr['date'],'ocr_amount':str(ocr['amount']),
        'user_other_status':403,'outsider_status':403,'synthetic':True}


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--csv',type=Path,required=True)
    parser.add_argument('--assets',type=Path,required=True)
    parser.add_argument('--check-only',action='store_true')
    args=parser.parse_args()
    print(json.dumps(run(args.csv,args.assets,args.check_only),ensure_ascii=False,indent=2))
