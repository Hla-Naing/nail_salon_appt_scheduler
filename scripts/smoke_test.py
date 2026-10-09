#!/usr/bin/env python3
"""Curl smoke checks. Run ONLY against a disposable app/database; creates demo history."""
import argparse
import datetime as dt
import json
import re
import subprocess
import tempfile
from pathlib import Path
from zoneinfo import ZoneInfo

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--base-url', default='http://localhost:18080')
parser.add_argument('--disposable-database', action='store_true', required=True,
                    help='Confirm this app uses a disposable database, not your development records')
args = parser.parse_args()
checks = 0

with tempfile.TemporaryDirectory(prefix='salon-smoke-') as folder:
    def call(method, path, expected=200, user=None, body=None):
        global checks
        command = ['curl', '--silent', '--show-error', '--max-time', '15', '-X', method,
                   '-w', '\n%{http_code}', args.base_url + path]
        if user:
            jar = str(Path(folder) / (user + '.cookies'))
            command += ['-b', jar, '-c', jar]
        if body is not None:
            command += ['-H', 'Content-Type: application/json', '--data', json.dumps(body)]
        result = subprocess.run(command, check=True, capture_output=True, text=True).stdout
        text, code = result.rsplit('\n', 1)
        assert int(code) == expected, f'{method} {path}: expected {expected}, got {code}: {text}'
        checks += 1
        print(f'PASS {code} {method} {path}')
        return json.loads(text) if text.lstrip().startswith(('{', '[')) else text

    call('GET', '/web')
    html = call('GET', '/web/slots')
    call('GET', '/web/login')
    # Read stable service/provider choices rendered by the server; no generated-ID assumptions.
    def choice(field, label):
        select = re.search(r'<select[^>]*id="' + field + r'"[^>]*>(.*?)</select>', html, re.S).group(1)
        return int(re.search(r'<option[^>]*value="(\d+)"[^>]*>' + re.escape(label) + '</option>', select).group(1))
    service = choice('serviceId', 'Basic Manicure')
    provider = choice('providerId', 'Anna Lee')
    call('GET', '/customer/appointments', 401)
    call('POST', '/auth/login', 401, 'maya', {'username': 'maya', 'password': 'wrong'})
    call('POST', '/auth/login', 400, body={'username': '', 'password': ''})
    for user in ('maya', 'lily', 'anna', 'sofia'):
        call('POST', '/auth/login', user=user, body={'username': user, 'password': 'TestPassword123!'})
        result = call('GET', '/auth/me', user=user)
        assert 'passwordHash' not in result
    call('GET', '/provider/appointments', 403, 'maya')
    call('GET', '/customer/appointments', 403, 'anna')
    call('GET', '/slots?page=-1', 400)
    call('GET', '/slots?size=0', 400)
    call('GET', '/slots?size=51', 400)
    call('GET', '/slots?date=invalid', 400)
    call('POST', '/customer/appointments', 400, 'maya', {'slotId': None})
    call('POST', '/provider/slots', 400, 'anna', {})
    start = dt.datetime.now(dt.timezone.utc).replace(microsecond=0) + dt.timedelta(days=2)
    def create(at):
        return call('POST', '/provider/slots', 201, 'anna', {
            'serviceId': service, 'startAt': at.isoformat(),
            'endAt': (at + dt.timedelta(minutes=30)).isoformat()})['slotId']
    slot = create(start)
    free = create(start + dt.timedelta(hours=2))
    call('POST', '/provider/slots', 400, 'anna', {
        'serviceId': service, 'startAt': start.isoformat(), 'endAt': (start-dt.timedelta(hours=1)).isoformat()})
    call('GET', '/slots?page=0&size=1')
    assert len(call('GET', '/slots?page=1&size=1')) == 1
    assert all(s['providerName'] == 'Anna Lee' for s in call('GET', f'/slots?providerId={provider}'))
    assert all(s['serviceName'] == 'Basic Manicure' for s in call('GET', f'/slots?serviceId={service}'))
    date = start.astimezone(ZoneInfo('America/Los_Angeles')).date()
    assert any(s['slotId'] == slot for s in call('GET', f'/slots?date={date}'))
    booked = call('POST', '/customer/appointments', 201, 'maya', {'slotId': slot})['appointmentId']
    call('POST', '/customer/appointments', 409, 'lily', {'slotId': slot})
    assert any(a['appointmentId'] == booked for a in call('GET', '/customer/appointments', user='maya'))
    assert not any(a['appointmentId'] == booked for a in call('GET', '/customer/appointments', user='lily'))
    assert any(a['appointmentId'] == booked for a in call('GET', '/provider/appointments', user='anna'))
    assert not any(a['appointmentId'] == booked for a in call('GET', '/provider/appointments', user='sofia'))
    call('DELETE', f'/customer/appointments/{booked}', 404, 'lily')
    call('DELETE', f'/provider/slots/{slot}', 409, 'anna')
    call('DELETE', f'/provider/slots/{free}', 404, 'sofia')
    call('DELETE', f'/provider/slots/{free}', user='anna')
    call('GET', f'/web/confirmation/{booked}', user='maya')
    call('GET', '/web/appointments', user='maya')
    call('GET', '/web/provider', user='anna')
    cancelled = call('DELETE', f'/customer/appointments/{booked}', user='maya')
    assert cancelled['feeCharged'] == 0
    call('DELETE', f'/provider/slots/{slot}', user='anna')
    assert any(a['appointmentId'] == booked and a['status'] == 'CANCELLED'
               for a in call('GET', '/customer/appointments', user='maya'))
    late_slot = create(dt.datetime.now(dt.timezone.utc) + dt.timedelta(hours=2))
    late_appt = call('POST', '/customer/appointments', 201, 'maya', {'slotId': late_slot})['appointmentId']
    assert call('DELETE', f'/customer/appointments/{late_appt}', user='maya')['feeCharged'] == 10
    call('DELETE', f'/provider/slots/{late_slot}', user='anna')
    call('POST', '/auth/logout', user='maya')
    call('GET', '/auth/me', 401, 'maya')
print(f'{checks} HTTP smoke checks passed. Temporary cookie jars removed.')
