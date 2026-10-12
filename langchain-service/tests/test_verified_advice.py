import unittest
from app.verified_advice import buying_reply, ledger_reply, produce_reply


class VerifiedAdviceTests(unittest.TestCase):
    def test_ledger_uses_supplied_authenticated_totals_not_invented_bank_balance(self):
        reply = ledger_reply('What is my profit?', {'income': 1000, 'expenses': 200, 'profit': 800})
        self.assertIn('R800.00', reply)
        self.assertIn('not your bank balance', reply)
        self.assertIsNone(ledger_reply('How do I improve profit?', {'income': 0, 'expenses': 0, 'profit': 0}))
        self.assertIsNone(ledger_reply('My expenses', None))

    def test_missing_profile_and_no_buyers_are_explained(self):
        self.assertIn('Production profile first', produce_reply({'profileMissing': True}))
        reply = produce_reply({'segment': {'name': 'Small tomato growers'}, 'recommendations': [], 'services': []})
        self.assertIn('No published buyer requests', reply)

    def test_buyer_reply_contains_only_the_recorded_match(self):
        reply = produce_reply({'segment': {'name': 'Small tomato growers'}, 'services': [], 'recommendations': [
            {'displayName': 'Recorded Buyer', 'quantityKg': 500, 'crop': 'tomato', 'district': 'Capricorn',
             'province': 'Limpopo', 'availableFrom': '2026-10-12', 'availableUntil': '2026-10-20'}]})
        self.assertIn('Recorded Buyer: 500 kg of tomato', reply)
        self.assertIn('not confirmed orders', reply)

    def test_group_order_discovery_does_not_claim_a_personal_match(self):
        reply = buying_reply([{'productName': 'Maize seed', 'matchedItems': [], 'remainingQuantity': 8,
                               'quote': {'listedUnitPrice': 450}}])
        self.assertIn('R450.00', reply)
        self.assertIn('not a purchase match', reply)
        self.assertIn('unconfirmed', reply)
        self.assertIn('No available', buying_reply([]))
