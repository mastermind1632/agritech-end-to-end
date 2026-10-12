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
