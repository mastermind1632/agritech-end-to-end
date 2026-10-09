import { test } from 'node:test';
import assert from 'node:assert/strict';
import { summarizeMatches, hasRecordedActivity } from './farmerInsights.js';

test('combines repeated anonymous match explanations without duplicating a farm', () => {
  const matches = [
    { reasons: ['same location', 'same location', 'similar recorded expense totals'] },
    { reasons: ['same location', 'shared products or expense terms: maize'] }
  ];
  assert.deepEqual(summarizeMatches(matches), [
    { label: 'Same recorded area', count: 2 },
    { label: 'Similar recorded expense totals', count: 1 },
    { label: 'Recorded items in common: maize', count: 1 }
  ]);
});

test('handles missing matches and distinguishes empty records from real activity', () => {
  assert.deepEqual(summarizeMatches(), []);
  assert.equal(hasRecordedActivity({ expenseCount: 0, orderCount: 0 }), false);
  assert.equal(hasRecordedActivity({ expenseCount: 1, orderCount: 0 }), true);
});
