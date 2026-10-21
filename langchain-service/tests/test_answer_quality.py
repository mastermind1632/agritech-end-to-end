import unittest
from app.answer_quality import clean_answer, relevant_sources


class AnswerQualityTests(unittest.TestCase):
    def test_removes_editorial_instructions_without_removing_practical_guidance(self):
        text = ('Water around the roots. Avoid waterlogging. Ask about the crop, stage and location; '
                'do not invent rainfall. Ask your local extension officer about crop-specific needs.')
        result = clean_answer(text)
        self.assertNotIn('Ask about', result)
        self.assertNotIn('do not invent', result)
        self.assertIn('Avoid waterlogging', result)
        self.assertIn('Ask your local extension officer', result)

    def test_preserves_price_decimals_and_readable_list_lines(self):
        text = 'Recorded listings:\n- Maize seed: R450.00.\n- Tomato seed: R30.00.'
        self.assertEqual(clean_answer(text), text)

    def test_water_references_do_not_include_rotation_or_ledger(self):
        sources = [{'id': id} for id in ('guide:water', 'source:fao-water', 'guide:rotation', 'guide:ledger')]
        self.assertEqual([s['id'] for s in relevant_sources('How do I water plants?', 'farming', sources)],
                         ['guide:water', 'source:fao-water'])

    def test_other_topics_keep_their_relevant_references(self):
        sources = [{'id': id} for id in ('guide:ledger', 'guide:rotation', 'source:fao-conservation', 'product:1')]
        self.assertEqual([s['id'] for s in relevant_sources('Crop rotation', 'farming', sources)], ['guide:rotation', 'source:fao-conservation'])
        self.assertEqual([s['id'] for s in relevant_sources('My profit', 'finance', sources)], ['guide:ledger'])

    def test_fertiliser_comparison_excludes_seed_and_generic_order_guidance(self):
        sources = [{'id': key} for key in ('guide:buying', 'guide:seed', 'guide:fertiliser', 'guide:ledger')]
        for spelling in ('fertiliser', 'fertilizer', 'fertilisers', 'fertilizers'):
            actual = relevant_sources(f'What should I compare when buying {spelling}?', 'buying', sources)
            self.assertEqual([s['id'] for s in actual], ['guide:fertiliser'])

    def test_mixed_product_comparisons_preserve_both_specific_guides_and_recorded_facts(self):
        sources = [{'id': key} for key in ('order:1', 'guide:buying', 'guide:fertiliser', 'product:2', 'guide:seed')]
        actual = relevant_sources('Compare seed and fertiliser packs', 'buying', sources)
        self.assertEqual([s['id'] for s in actual], ['guide:seed', 'guide:fertiliser', 'order:1', 'product:2'])
        self.assertIs(actual[-1], sources[3])

    def test_product_order_questions_keep_order_terms_and_facts_after_product_guidance(self):
        sources = [{'id': key} for key in ('guide:buying', 'order:1', 'guide:seed', 'product:2', 'guide:fertiliser')]
        actual = relevant_sources('Compare fertiliser group-order discounts and delivery', 'buying', sources)
        self.assertEqual([s['id'] for s in actual], ['guide:fertiliser', 'guide:buying', 'order:1', 'product:2'])

    def test_seed_comparisons_keep_seed_guidance_without_fertiliser_distraction(self):
        sources = [{'id': key} for key in ('guide:fertiliser', 'product:1', 'guide:seed', 'guide:buying')]
        self.assertEqual([s['id'] for s in relevant_sources('Compare maize seed packets', 'buying', sources)],
                         ['guide:seed', 'product:1'])
        self.assertIn('guide:buying', [s['id'] for s in relevant_sources('Are seeds in stock for delivery?', 'buying', sources)])

    def test_missing_product_guide_preserves_general_buying_fallback_without_wrong_product_guide(self):
        sources = [{'id': key} for key in ('guide:seed', 'guide:buying', 'order:1')]
        self.assertEqual([s['id'] for s in relevant_sources('Compare fertiliser', 'buying', sources)], ['guide:buying', 'order:1'])

    def test_generic_buying_questions_preserve_available_comparison_sources(self):
        sources = [{'id': key} for key in ('guide:seed', 'guide:buying', 'guide:fertiliser', 'product:1', 'order:2')]
        self.assertEqual(relevant_sources('How do I compare buying opportunities?', 'buying', sources), sources)
