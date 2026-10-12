"""Exercise authenticated production contracts; remove only this run's synthetic accounts."""
import json
import secrets
from datetime import date, timedelta
from urllib.error import HTTPError
from urllib.request import Request, urlopen
from uuid import uuid4

from app.store import Store


def main():
    base = 'http://spring-app:8080/api'
    accounts = []
    conversations = []
    store = Store()

    def call(path, method='GET', body=None, token=None, expected=200):
        headers = {'Content-Type': 'application/json'}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        request = Request(base + path, method=method, headers=headers,
                          data=json.dumps(body).encode() if body is not None else None)
        try:
            with urlopen(request, timeout=120) as response:
                assert response.status == expected
                return json.load(response)
        except HTTPError as error:
            if error.code != expected:
                raise RuntimeError(f'{method} {path} returned {error.code}, expected {expected}') from error

    try:
        for role in ('farmer', 'buyer'):
            contact = 'release-check-' + str(uuid4()) + '@example.invalid'
            account = call('/farmers/register', 'POST', {'name': 'Release test ' + role, 'location': 'Polokwane',
                           'contact': contact, 'password': secrets.token_urlsafe(24)}, expected=201)
            accounts.append((account, contact))
        farmer, buyer = [row[0] for row in accounts]
        profile = {'crop': 'tomato', 'province': 'Limpopo', 'district': 'Capricorn', 'latitude': -23.9,
                   'longitude': 29.45, 'hectares': 2, 'capacityKg': 1000, 'plantingMonth': 7,
                   'harvestMonth': date.today().month, 'deliveryRadiusKm': 100, 'businessRegistered': False,
                   'consistentProduction': False, 'analyticsConsent': False, 'expenseConsent': False}
        call('/market-intelligence/profile', 'PUT', profile, farmer['token'])
        call('/market-intelligence/profile', 'PUT', {**profile, 'ownerId': buyer['farmer']['id']}, farmer['token'], 422)
        listing = call('/market-intelligence/records', 'POST', {'kind': 'demand', 'crop': 'tomato',
                       'province': 'Limpopo', 'district': 'Capricorn', 'latitude': -23.91, 'longitude': 29.46,
                       'quantityKg': 500, 'pricePerKg': 10, 'availableFrom': date.today().isoformat(),
                       'availableUntil': (date.today() + timedelta(days=2)).isoformat(),
                       'displayName': 'Synthetic release verification; removed immediately', 'publicContact': ''}, buyer['token'])
        matches = call('/market-intelligence/recommendations', token=farmer['token'])
        assert any(row['id'] == listing['id'] for row in matches['recommendations'])
        assert matches['impressionId'] is None
        for row in matches['recommendations']:
            assert 'ownerId' not in row and 'latitude' not in row and 'longitude' not in row
        call('/market-intelligence/records/' + listing['id'], 'DELETE', token=farmer['token'], expected=404)
        call('/market-intelligence/records/' + listing['id'], 'DELETE', token=buyer['token'])
        call('/market-intelligence/profile', expected=401)
        call('/market-intelligence/options', token=farmer['token'])
        call('/market-intelligence/demand', token=farmer['token'])
        print('Production identity, profile, matching, privacy, ownership and consent contracts passed.')
        conversation = call('/ai/conversations', 'POST', {}, farmer['token'])['conversationId']
        conversations.append((conversation, farmer['token']))
        replies = []
        for question in ('Where can i get seeds', 'Crops seeds', 'Which location'):
            reply = call(f'/ai/conversations/{conversation}/turn', 'POST', {'prompt': question}, farmer['token'])
            assert reply['route'] == 'buying', 'Seed shopping must use catalogue grounding'
            assert 'Ask which crop' not in reply['response'], 'Source instructions leaked into the answer'
            replies.append(reply['response'])
        assert 'Marketplace' in replies[0]
        assert 'town or province where your farm is' in replies[-1]
        assert replies[-1] != replies[0], 'Clarification repeated the previous answer'
        water = call(f'/ai/conversations/{conversation}/turn', 'POST', {'prompt': 'How do i water plants'}, farmer['token'])
        assert water['route'] == 'farming'
        assert any(word in water['response'].lower() for word in ('soil', 'root', 'moisture')), 'General watering guidance missing'
        assert 'seed' not in water['response'].lower(), 'Previous seed topic contaminated a new watering question'
        print('Reported seed conversation, location clarification and real generated watering advice passed.')
    finally:
        try:
            for conversation, token in conversations:
                call('/ai/conversations/' + conversation, 'DELETE', token=token)
        finally:
            with store.connect() as connection:
                for account, contact in accounts:
                    # Both identifiers must match; production users and their records cannot be selected.
                    connection.execute('DELETE FROM public.farmers WHERE id=%s AND contact=%s',
                                       (account['farmer']['id'], contact))
        print('Only synthetic release-check accounts and their cascading records were removed.')


if __name__ == '__main__':
    main()
