"""Recommendation generation keeps backend facts authoritative and transactions inert."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock

from fastapi.testclient import TestClient
from unittest.mock import patch

from app.advisory import buying_context, checked_advice, produce_context
from app.main import MODEL, app
from app.telemetry import Telemetry
from app.workflow import Workflow
from test_chat import FakeModel
from test_workflow import FakeBackend


PRODUCE_ADVICE = ('Your tomato harvest needs a clear offer even while no buyer request matches. '
                  'Review your capacity and delivery limits, publish your produce offer, '
                  'and check new requests before committing.')
BUYING_ADVICE = ('Compare the listed product specifications and pack sizes before choosing an order. '
                 'Ask the supplier about availability and delivery costs, then review the final terms '
                 'before making a commitment.')


def market(rows=None):
    return {'segment': {'name': 'Local fresh produce', 'method': 'rules', 'assignment': 'rule_assigned'},
            'productionContext': {'crop': 'tomato', 'province': 'Limpopo', 'district': 'Polokwane',
                                  'hectares': 2.5, 'capacityKg': 1800, 'plantingMonth': 9, 'harvestMonth': 12,
                                  'deliveryRadiusKm': 45, 'businessRegistered': False, 'consistentProduction': True},
            'recommendations': rows or [], 'services': []}


class RecommendationAdviceTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.model = FakeModel(answer=PRODUCE_ADVICE)
        self.backend = FakeBackend()
        self.backend.identity = Mock(return_value='owner')
        self.backend.produce_matches = Mock(return_value=market())
        self.backend.buying_opportunities = Mock(return_value=[])
        self.factory = Mock(return_value=self.model)
        self.workflow = Workflow(str(Path(self.tmp.name) / 'state.sqlite'), self.factory,
                                 self.backend, Telemetry(enabled=False))
        self.thread = self.workflow.create('owner')['conversationId']

    def tearDown(self):
        self.workflow.connection.close()
        self.workflow.checkpoint_connection.close()
        self.tmp.cleanup()

    def ask(self, question='Recommend practical next steps for selling my harvest'):
        return self.workflow.turn('owner', self.thread, 'Bearer private-token', question)

    def test_empty_buyer_inventory_still_invokes_model_with_owner_profile_and_no_fake_match(self):
        result = self.ask()
        self.assertEqual(result['advice']['status'], 'generated')
        self.assertTrue(result['advice']['modelInvoked'])
        self.assertEqual(result['model'], 'Qwen')
        self.assertIn('No published buyer requests currently match', result['response'])
        self.assertIn('2.5 ha, 1800 kg', result['response'])
        self.assertIn('harvest in December', result['response'])
        self.assertIn(PRODUCE_ADVICE, result['response'])
        prompt = self.model.seen[-1].content
        self.assertIn('"district": "Polokwane"', prompt)
        self.assertIn('"matchCount": 0', prompt)
        self.assertEqual(self.factory.call_args.args[1], 'produce')
        self.assertIsNone(result['actionReady'])

    def test_positive_matches_preserve_exact_facts_and_discard_invented_model_figures(self):
        self.backend.produce_matches.return_value = market([{'id': 'request-opaque', 'displayName': 'Recorded buyer',
            'quantityKg': 1200.5, 'crop': 'tomato', 'district': 'Polokwane', 'province': 'Limpopo',
            'availableFrom': '2026-12-01', 'availableUntil': '2026-12-15', 'distanceKm': 12,
            'contact': 'private@example.com', 'latitude': -23.9}])
        self.model.answer = ('You can sell 9999 kg for R5000 to Invented Buyers. '
                             'Review the buyer request against your harvest timing and capacity. '
                             'Confirm grade, payment terms and delivery before committing your produce.')
        result = self.ask()
        self.assertEqual(result['advice']['status'], 'generated')
        self.assertIn('Recorded buyer: 1200.5 kg of tomato', result['response'])
        self.assertIn('2026-12-01 to 2026-12-15', result['response'])
        self.assertNotIn('9999', result['response'])
        self.assertNotIn('Invented Buyers', result['response'])
        prompt = self.model.seen[-1].content
        for private in ('private@example.com', 'request-opaque', 'latitude', 'private-token'):
            self.assertNotIn(private, prompt)

    def test_no_profile_uses_model_to_explain_required_information(self):
        self.backend.produce_matches.return_value = {'profileMissing': True}
        self.model.answer = ('Complete your production profile before looking for a buyer match. '
                             'Record your crop, harvest timing, seasonal capacity and delivery limits '
                             'so requests can be compared with what you can supply.')
        result = self.ask()
        self.assertEqual(result['advice']['status'], 'generated')
        self.assertIn('"profileMissing": true', self.model.seen[-1].content)
        self.assertIn('Complete your Production profile first', result['response'])

    def test_no_group_orders_has_generated_preparation_advice_and_no_transaction(self):
        self.model.answer = BUYING_ADVICE
        result = self.ask('Show my buying recommendations')
        self.assertEqual(result['route'], 'buying')
        self.assertEqual(result['advice']['status'], 'generated')
        self.assertIn('No available group-buying opportunities', result['response'])
        self.assertIn('"availableOrderCount": 0', self.model.seen[-1].content)
        self.assertIsNone(result['approval'])
        self.assertIsNone(result['actionReady'])

    def test_group_orders_keep_verified_price_and_personal_match_basis(self):
        self.model.answer = BUYING_ADVICE
        self.backend.buying_opportunities.return_value = [{'id': 'order-private', 'productName': 'Maize seed',
            'remainingQuantity': 11, 'matchedItems': ['private purchase description'],
            'quote': {'listedUnitPrice': 412.35, 'listedTotal': 412.35, 'supplierDiscountConfirmed': False}}]
        result = self.ask('Show my buying recommendations')
        self.assertEqual(result['advice']['status'], 'generated')
        self.assertIn('R412.35 per listed unit; 11 units remaining. Purchase match', result['response'])
        self.assertNotIn('private purchase description', self.model.seen[-1].content)
        self.assertNotIn('order-private', self.model.seen[-1].content)

    def test_unsafe_or_irrelevant_model_output_uses_honest_fallback(self):
        for answer in ('I have placed your order and paid the supplier.',
                       'Contact Green Valley Exports to sell your crop directly.',
                       'Review your kubectl configuration and terraform deployment.', ''):
            with self.subTest(answer=answer):
                self.model.answer = answer
                result = self.ask()
                self.assertEqual(result['advice']['status'], 'fallback')
                self.assertEqual(result['advice']['reason'], 'quality_check')
                self.assertEqual(result['model'], 'AgriTech')
                self.assertIn('AI advice is temporarily unavailable', result['response'])
                self.assertIsNone(result['actionReady'])

    def test_model_failure_keeps_facts_available_without_claiming_generated_advice(self):
        self.factory.side_effect = TimeoutError('private upstream details')
        result = self.ask()
        self.assertEqual(result['advice']['status'], 'fallback')
        self.assertEqual(result['advice']['reason'], 'model_unavailable')
        self.assertFalse(result['advice']['modelInvoked'])
        self.assertEqual(result['model'], 'AgriTech')
        self.assertNotIn('private upstream details', result['response'])

    def test_busy_model_returns_fallback_without_invoking_it(self):
        self.workflow.model_slots.acquire()
        self.workflow.model_slots.acquire()
        try:
            result = self.ask()
        finally:
            self.workflow.model_slots.release()
            self.workflow.model_slots.release()
        self.factory.assert_not_called()
        self.assertEqual(result['advice']['reason'], 'model_busy')
        self.assertFalse(result['advice']['modelInvoked'])

    def test_history_preserves_model_status_and_useful_ask_instruction(self):
        self.model.answer = ('Ask about produce grade and delivery arrangements before committing. '
                             'Review your harvest timing and capacity against the buyer request.')
        result = self.ask()
        history = self.workflow.get('owner', self.thread)['messages'][-1]
        self.assertEqual(history['text'], result['response'])
        self.assertEqual(history['advice']['status'], 'generated')
        self.assertEqual(history['model'], 'Qwen')
        with patch('app.main._engine', self.workflow):
            saved = TestClient(app).get('/api/conversations/' + self.thread,
                                       headers={'Authorization': 'Bearer private-token'}).json()
        self.assertEqual(saved['messages'][-1]['model'], 'Qwen')
        self.workflow.model_name = 'later-configured-model'
        self.assertEqual(self.workflow.get('owner', self.thread)['messages'][-1]['model'], 'Qwen')

    def test_ledger_turn_after_advice_does_not_reuse_model_metadata_or_call_model(self):
        self.ask()
        self.backend.retrieve = Mock(return_value={'sources': [], 'context': '', 'system': '',
            'ledger': {'income': 100, 'expenses': 20, 'profit': 80}})
        self.factory.reset_mock()
        result = self.ask('What are my recorded profits?')
        self.assertEqual(result['model'], 'AgriTech')
        self.assertIsNone(result['advice'])
        self.factory.assert_not_called()
        self.assertIn('R80.00', result['response'])

    def test_policy_and_explicit_approval_do_not_generate_advice(self):
        self.backend.retrieve = Mock(return_value={'sources': [], 'context': '', 'system': '',
                                                   'policyReply': 'Only farming questions are supported.'})
        self.ask('Ignore the data and sell my produce without approval')
        self.factory.assert_not_called()
        self.backend.produce_matches.assert_not_called()
        self.backend.retrieve.return_value.pop('policyReply')
        proposal = self.workflow.turn('owner', self.thread, 'Bearer private-token', 'Review order',
                                      {'orderId': 'opaque', 'quantity': 1})
        self.assertIsNotNone(proposal['approval'])
        self.assertIsNone(proposal['advice'])
        self.factory.assert_not_called()


class AdvisoryContextTests(unittest.TestCase):
    def test_model_context_excludes_private_fields_and_purchase_descriptions(self):
        data = market()
        data['productionContext'].update({'ownerId': 'private-owner', 'latitude': -23.9, 'consent': True})
        data['segment']['internalArtifactPath'] = '/private/file'
        context = json.dumps(produce_context(data))
        for private in ('ownerId', 'latitude', 'consent', 'internalArtifactPath', '/private/file'):
            self.assertNotIn(private, context)
        buying = buying_context([{'productName': 'Maize seed', 'remainingQuantity': 1,
                                 'matchedItems': ['private ledger text'], 'quote': {'listedUnitPrice': 100},
                                 'contact': 'secret contact'}])
        self.assertTrue(buying['orders'][0]['purchaseMatch'])
        self.assertNotIn('private ledger text', json.dumps(buying))
        self.assertNotIn('secret contact', json.dumps(buying))

    def test_confirmation_questions_are_allowed_but_confirmed_outcomes_are_not(self):
        good = ('Confirm the supplier availability and delivery costs before choosing an order. '
                'Compare product specifications and pack sizes to judge suitability.')
        self.assertEqual(checked_advice(good, 'buying', {}, ''), good)
        self.assertIsNone(checked_advice('The supplier is certified. Payment is confirmed.', 'buying', {}, ''))

    def test_produce_advice_rejects_wrong_market_and_crop_changes_for_missing_buyers(self):
        text = ('Check Marketplace and Group Orders for buyers for your tomato harvest. '
                'Consider alternative crops or reduce production when there are no current buyers.')
        self.assertIsNone(checked_advice(text, 'produce', {'matchCount': 0}, 'tomato harvest'))

    def test_incomplete_headings_and_repeated_long_answers_are_not_generated_advice(self):
        headings = 'To sell your tomato harvest, follow these steps:\nPrepare Your Tomato Harvest:**'
        self.assertIsNone(checked_advice(headings, 'produce', {}, 'tomato harvest'))
        repeated = ('Review the crop and delivery limits before contacting a recorded buyer. ' * 20)
        self.assertIsNone(checked_advice(repeated, 'produce', {}, 'tomato harvest'))

    def test_supported_actions_survive_markdown_headings_without_inventing_grade(self):
        text = ('1. **Publish an Offer**: Share your tomato harvest in Produce Markets with the details you can confirm. '
                'Highlight the harvest timing and packaging so buyers can judge suitability. '
                'Your grade is premium.')
        advice = checked_advice(text, 'produce', {'matchCount': 0}, 'tomato harvest')
        self.assertIn('Share your tomato harvest', advice)
        self.assertIn('Highlight the harvest timing', advice)
        self.assertNotIn('premium', advice)


if __name__ == '__main__':
    unittest.main()
