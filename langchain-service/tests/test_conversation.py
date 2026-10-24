import json
import unittest

from app.conversation import catalogue_reply, followup, route_for


class ConversationTests(unittest.TestCase):
    def test_financial_variations_override_previous_topics(self):
        history = [{'role': 'assistant', 'text': 'Which crop do you grow?'}]
        for question in ('what is my recorded profits', 'My profits', 'My income',
                         'My incomes', 'My earnings', 'My revenue', 'My expenses', 'My budgets'):
            self.assertFalse(followup(question, history), question)
            for previous in ('farming', 'produce', 'buying'):
                self.assertEqual(route_for(question, previous, history), 'finance', question)

    def test_seed_shopping_and_typo_route_to_catalogue(self):
        for question in ('Where can i get seeds', 'Where can i get seeda', 'Crops seeds', 'seeds', 'Buy maize seeds'):
            self.assertEqual(route_for(question), 'buying', question)

    def test_recommendation_subject_distinguishes_farming_advice_from_shopping(self):
        for question in ('Recommend crop rotation', 'Recommendations for irrigation',
                         'What soil improvements do you recommend?', 'Recommend a planting schedule'):
            self.assertEqual(route_for(question), 'farming', question)
        for question in ('My recommendations', 'Show me buying opportunities', 'Any recommendations?',
                         'Recommend maize seeds', 'What fertiliser do you recommend?', 'Compare seed prices'):
            self.assertEqual(route_for(question), 'buying', question)

    def test_new_short_topic_does_not_inherit_seed_search(self):
        self.assertFalse(followup('How do i water plants'))
        self.assertEqual(route_for('How do i water plants', 'buying'), 'farming')
        self.assertEqual(route_for('What is germination?', 'buying'), 'farming')
        self.assertEqual(route_for('My expenses', 'buying'), 'finance')

    def test_location_clarification_uses_conversation_context(self):
        history = [{'role': 'user', 'text': 'Where can i get seeds'}]
        self.assertTrue(followup('Which location'))
        self.assertEqual(route_for('Which location', 'buying'), 'buying')
        answer = catalogue_reply('Which location', [], history)
        self.assertIn("town or province where your farm is", answer)
        self.assertNotIn('Start with', answer)
        self.assertIsNone(catalogue_reply('Which location', [], []))

    def test_actual_seed_listing_recommends_only_recorded_supplier_and_price(self):
        sources = [{'id': 'product:1', 'title': 'Maize seed 10kg', 'content': json.dumps(
            {'productName': 'Maize seed 10kg', 'supplierName': 'Verified test supplier', 'listedPriceZar': 450})}]
        answer = catalogue_reply('Where can I get maize seeds?', sources, [])
        self.assertIn('Maize seed 10kg', answer)
        self.assertIn('Verified test supplier', answer)
        self.assertIn('R450.00', answer)
        self.assertIn('not confirmed stock', answer)

    def test_other_crop_non_seed_or_malformed_products_are_not_recommended(self):
        sources = [{'id': 'product:1', 'content': json.dumps(
            {'productName': name, 'supplierName': 'Shop', 'listedPriceZar': 100})}
            for name in ('Tomato seed', 'Maize fertiliser')]
        sources.append({'id': 'product:2', 'content': 'invalid product data'})
        answer = catalogue_reply('Where can I get maize seeds?', sources, [])
        self.assertIn('no matching seed listing', answer)
        self.assertNotIn('R100', answer)
        self.assertNotIn('Which crop', answer)

    def test_bare_seeds_and_shopping_question_have_distinct_useful_answers(self):
        first = catalogue_reply('Where can i get seeds', [], [])
        second = catalogue_reply('Crops seeds', [], [])
        self.assertNotEqual(first, second)
        self.assertIn('Marketplace', first)
        self.assertIn('Which crop', second)
        for answer in (first, second):
            self.assertNotIn('Ask which crop', answer)
            self.assertNotIn('local seed catalogues', answer)

    def test_farming_questions_do_not_receive_shopping_template(self):
        self.assertIsNone(catalogue_reply('How do I water plants?', [], []))
