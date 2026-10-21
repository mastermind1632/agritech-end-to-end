import { test } from 'node:test';
import assert from 'node:assert/strict';
import { niceMax, compactMoney, titleCase, monthLabel } from './format.js';

test('chart axis maximum rounds up to a readable number', () => {
  assert.equal(niceMax(8734), 10000);
  assert.equal(niceMax(1500), 2000);
  assert.equal(niceMax(420), 500);
  assert.equal(niceMax(0), 1);
});

test('compact money for tiles', () => {
  assert.equal(compactMoney(2_500_000), 'R2.5m');
  assert.equal(compactMoney(45_400), 'R45k');
});

test('codes become readable labels', () => {
  assert.equal(titleCase('NOT_APPLICABLE'), 'Not Applicable');
  assert.match(monthLabel('2026-03'), /Mar/);
});
