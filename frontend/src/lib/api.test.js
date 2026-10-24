import { test } from 'node:test';
import assert from 'node:assert/strict';
import { api, statementApi } from './api.js';

const storage = () => ({ getItem: () => 'synthetic-token', removeItem: () => {} });
const browserStorage = t => {
  const original = Object.getOwnPropertyDescriptor(globalThis, 'localStorage');
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: storage() });
  t.after(() => {
    if (original) Object.defineProperty(globalThis, 'localStorage', original);
    else delete globalThis.localStorage;
  });
};

test('API forwards authentication and preserves structured service errors', async t => {
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(url, '/api/market-intelligence/recommendations');
    assert.equal(options.headers.Authorization, 'Bearer synthetic-token');
    return new Response(JSON.stringify({ detail: 'Complete your production profile first' }), { status: 409 });
  });
  browserStorage(t);
  await assert.rejects(api('/market-intelligence/recommendations'), error =>
    error.status === 409 && error.message === 'Complete your production profile first');
});

test('a stalled request times out with a recoverable error', async t => {
  browserStorage(t);
  t.mock.method(globalThis, 'fetch', async (url, { signal }) => new Promise((resolve, reject) => {
    signal.addEventListener('abort', () => reject(new Error('aborted')), { once: true });
  }));
  await assert.rejects(api('/ai/conversations/test/turn', { timeoutMs: 5 }), /took too long.*try again/i);
});

test('a stalled response body is also bounded by the timeout', async t => {
  browserStorage(t);
  t.mock.method(globalThis, 'fetch', async (url, { signal }) => ({
    text: () => new Promise((resolve, reject) => {
      signal.addEventListener('abort', () => reject(new Error('aborted')), { once: true });
    })
  }));
  await assert.rejects(api('/recommendations/test/quote', { timeoutMs: 5 }), /took too long.*try again/i);
});

test('caller cancellation is forwarded and successful responses are parsed', async t => {
  browserStorage(t);
  t.mock.method(globalThis, 'fetch', async (url, { signal }) => {
    if (signal.aborted) throw new Error('aborted');
    return new Response(JSON.stringify({ response: 'Check soil moisture.' }));
  });
  const controller = new AbortController();
  controller.abort();
  await assert.rejects(api('/ai/chat', { signal: controller.signal }), /cancelled/);
  assert.deepEqual(await api('/ai/chat'), { response: 'Check soil moisture.' });
});

test('statement OCR upload keeps multipart boundaries and outlasts the ordinary request timeout', async t => {
  browserStorage(t);
  t.mock.timers.enable({ apis: ['setTimeout'] });
  let uploadSignal, finish;
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(url, '/api/statements');
    assert.equal(options.headers.Authorization, 'Bearer synthetic-token');
    assert.equal(options.headers['Content-Type'], undefined);
    assert.ok(options.body instanceof FormData);
    assert.equal(options.body.get('consent'), 'true');
    uploadSignal = options.signal;
    return new Promise(resolve => { finish = resolve; });
  });
  const pending = statementApi.upload(new Blob(['synthetic statement']), true, false);
  t.mock.timers.tick(31000);
  assert.equal(uploadSignal.aborted, false);
  finish(new Response(JSON.stringify({ id: 'statement-check' })));
  assert.deepEqual(await pending, { id: 'statement-check' });
});
