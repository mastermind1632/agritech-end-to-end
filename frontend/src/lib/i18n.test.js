import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { translate, DICTIONARIES, LANGUAGES, PHRASES } from './i18n.js';
import { LANG_COLUMNS } from '../locales/index.js';

const SRC = join(dirname(fileURLToPath(import.meta.url)), '..');
const placeholders = (s) => [...new Set(String(s).match(/\{\w+\}/g) || [])].sort();

test('every language has a dictionary', () => {
  for (const [code] of LANGUAGES) assert.ok(DICTIONARIES[code], code);
});

test('translations fall back to English, then to the key', () => {
  assert.equal(translate('af', 'overview'), 'Oorsig');
  assert.equal(translate('xx', 'overview'), 'Overview');
  assert.equal(translate('af', 'no-such-key'), 'no-such-key');
});

test('phrases are translated and fill in variables', () => {
  assert.equal(translate('af', 'Upload statement'), 'Laai staat op');
  assert.equal(translate('zu', 'Upload statement'), 'Layisha isitatimende');
  assert.equal(translate('af', '{done} of {total} steps done', { done: 3, total: 9 }), '3 van 9 stappe klaar');
  assert.equal(translate('en', '{done} of {total} steps done', { done: 3, total: 9 }), '3 of 9 steps done');
});

test('no language is missing a key that English has', () => {
  const keys = Object.keys(DICTIONARIES.en);
  for (const [code] of LANGUAGES) {
    const missing = keys.filter((k) => !DICTIONARIES[code][k]);
    assert.deepEqual(missing, [], `${code} is missing ${missing.join(', ')}`);
  }
});

test('every phrase row has all languages and the same {placeholders}', () => {
  const cols = Object.keys(LANG_COLUMNS).length;
  for (const [en, row] of Object.entries(PHRASES)) {
    assert.equal(row.length, cols, `row for ${en}`);
    row.forEach((t) => {
      assert.ok(t && t.trim(), `empty translation for ${en}`);
      assert.deepEqual(placeholders(t), placeholders(en), `placeholders differ for ${en}: ${t}`);
    });
  }
});

// Scans the source for t('...'), tr('...') and T('...') calls with literal text and checks each has a translation,
// so new screens cannot quietly ship in English only.
test('every literal string passed to the translator has a translation', () => {
  const files = [];
  const walk = (dir) => readdirSync(dir).forEach((f) => {
    const p = join(dir, f);
    if (statSync(p).isDirectory()) { if (f !== 'locales') walk(p); } else if (/\.(jsx?|tsx?)$/.test(f) && !f.endsWith('.test.js')) files.push(p);
  });
  walk(SRC);
  const call = /\b(?:t|tr|T)\(\s*(['"])((?:\\.|(?!\1).)*)\1/g;
  const missing = new Set();
  for (const f of files) {
    const src = readFileSync(f, 'utf8');
    for (const m of src.matchAll(call)) {
      const key = m[2].replace(/\\(['"\\])/g, '$1').replace(/\\n/g, '\n');
      if (!key.trim() || /^[a-z]+[A-Z]?\w*$/.test(key) && DICTIONARIES.en[key]) continue;
      if (!(key in PHRASES) && !(key in DICTIONARIES.en)) missing.add(`${key}  (${f.slice(SRC.length + 1)})`);
    }
  }
  assert.deepEqual([...missing], [], `untranslated:\n${[...missing].join('\n')}`);
});
