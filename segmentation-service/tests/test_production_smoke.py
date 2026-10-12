import io
import json
import unittest
from urllib.error import HTTPError
from unittest.mock import MagicMock, patch

from app import production_smoke


class Response(io.BytesIO):
    def __init__(self, value, status=200):
        super().__init__(json.dumps(value).encode())
        self.status = status


class ProductionSmokeTest(unittest.TestCase):
    def exercise(self, fail=False):
        accounts = []
        store = MagicMock()
        connection = store.connect.return_value.__enter__.return_value
        deleted_conversations = []

        def reply(request, timeout):
            path = request.full_url.split('/api', 1)[1]
            body = json.loads(request.data) if request.data else None
            method = request.get_method()
            if path == '/farmers/register':
                role = 'farmer' if not accounts else 'buyer'
                accounts.append((role, body['contact']))
                return Response({'farmer': {'id': role}, 'token': role + '-token'}, 201)
            if path == '/market-intelligence/profile' and method == 'GET':
                raise HTTPError(request.full_url, 401, '', {}, None)
            if path == '/market-intelligence/profile' and body and 'ownerId' in body:
                raise HTTPError(request.full_url, 422, '', {}, None)
            if path == '/market-intelligence/records' and method == 'POST':
                return Response({'id': 'listing'})
            if path.endswith('/records/listing') and request.get_header('Authorization') == 'Bearer farmer-token':
                raise HTTPError(request.full_url, 404, '', {}, None)
            if path == '/market-intelligence/recommendations':
                if fail:
                    raise HTTPError(request.full_url, 500, '', {}, None)
                return Response({'recommendations': [{'id': 'listing'}], 'impressionId': None})
            if path.startswith('/market-intelligence/records?'):
                return Response({'records': []})
            if path == '/market-intelligence/demand':
                return Response({'rows': [], 'forecasts': []})
            if path == '/recommendations/farmer/farmer':
                return Response([{'id': 'existing-order', 'productName': 'Recorded maize seed'}])
            if path.startswith('/recommendations/existing-order/quote'):
                return Response({'quote': {'supplierDiscountConfirmed': False}})
            if path == '/ai/conversations':
                return Response({'conversationId': 'synthetic-thread'})
            if path == '/ai/conversations/synthetic-thread' and method == 'DELETE':
                deleted_conversations.append(path)
            if path.endswith('/turn'):
                question = body['prompt']
                if question == 'How do i water plants':
                    return Response({'route': 'farming', 'response': 'Check soil moisture and water the roots.', 'sources': [{'id': 'guide:water'}]})
                if question == 'What is my recorded profit?':
                    return Response({'route': 'finance', 'response': 'Recorded profit R0.00'})
                if question == 'Find buyers for my produce':
                    return Response({'route': 'produce', 'response': 'Your farm group: Small tomato growers'})
                if question == 'Show my buying opportunities':
                    return Response({'route': 'buying', 'response': 'Recorded maize seed'})
                if body.get('action'):
                    return Response({'approval': {'interruptId': 'cancel-test'}, 'actionReady': None})
                return Response({'route': 'buying', 'response': 'Marketplace: check seeds.' if question != 'Which location'
                                 else 'I mean the town or province where your farm is.'})
            if path.endswith('/resume'):
                return Response({'approval': None, 'actionReady': None})
            return Response({})

        with patch.object(production_smoke, 'Store', return_value=store), patch.object(production_smoke, 'urlopen', side_effect=reply):
            if fail:
                with self.assertRaises(RuntimeError):
                    production_smoke.main()
            else:
                production_smoke.main()
        self.assertEqual(connection.execute.call_count, 2)
        self.assertEqual(deleted_conversations, [] if fail else ['/ai/conversations/synthetic-thread'])
        for call, account in zip(connection.execute.call_args_list, accounts):
            self.assertEqual(call.args[0], 'DELETE FROM public.farmers WHERE id=%s AND contact=%s')
            self.assertEqual(call.args[1], account)

    def test_synthetic_accounts_are_deleted_by_id_and_contact(self):
        self.exercise()

    def test_failed_smoke_check_still_deletes_only_its_accounts(self):
        self.exercise(fail=True)
