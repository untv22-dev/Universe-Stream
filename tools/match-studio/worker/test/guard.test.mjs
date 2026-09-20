// Covers every path through the worker that rejects a request *before* it reaches the model.
//
// Those are the ones that matter for spend: the endpoint holds a real API key, so the origin
// allowlist, the shared token, the daily cap and the size/type limits are the only things standing
// between it and anyone who finds the URL. The model call itself needs a key and network, so it is
// exercised in the deploy smoke test, not here.
import test from 'node:test';
import assert from 'node:assert/strict';
import worker from '../src/index.js';

const ORIGIN = 'https://studio.example';

// Minimal stand-in for the KV binding, with the same get/put surface the worker uses.
const kv = () => {
  const store = new Map();
  return {
    get: async k => store.get(k) ?? null,
    put: async (k, v) => void store.set(k, v),
    _store: store,
  };
};

const env = (over = {}) => ({
  ANTHROPIC_API_KEY: 'test-key',
  CLIENT_TOKEN: 'secret-token',
  ALLOWED_ORIGINS: `${ORIGIN},http://localhost:8080`,
  DAILY_LIMIT: '3',
  RATE: kv(),
  ...over,
});

const post = (body, {origin = ORIGIN, token = 'secret-token', path = '/extract'} = {}) =>
  new Request(`https://worker.example${path}`, {
    method: 'POST',
    headers: {
      'content-type': 'application/json',
      ...(origin ? {origin} : {}),
      ...(token ? {'x-studio-token': token} : {}),
    },
    body: JSON.stringify(body),
  });

// A 1x1 PNG, large enough to be a real image and small enough to inline.
const PNG = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==';

test('rejects a non-allowlisted origin', async () => {
  const res = await worker.fetch(post({image: PNG}, {origin: 'https://evil.example'}), env());
  assert.equal(res.status, 403);
  assert.equal(res.headers.get('access-control-allow-origin'), null, 'must not hand CORS to a stranger');
});

test('rejects a wrong or missing client token', async () => {
  assert.equal((await worker.fetch(post({image: PNG}, {token: 'wrong'}), env())).status, 401);
  assert.equal((await worker.fetch(post({image: PNG}, {token: null}), env())).status, 401);
});

test('rejects anything but POST /extract', async () => {
  const get = new Request('https://worker.example/extract', {method: 'GET', headers: {origin: ORIGIN}});
  assert.equal((await worker.fetch(get, env())).status, 405);
  assert.equal((await worker.fetch(post({image: PNG}, {path: '/'}), env())).status, 404);
});

test('answers preflight with the allowlisted origin only', async () => {
  const pre = o => new Request('https://worker.example/extract', {method: 'OPTIONS', headers: {origin: o}});
  const ok = await worker.fetch(pre(ORIGIN), env());
  assert.equal(ok.status, 204);
  assert.equal(ok.headers.get('access-control-allow-origin'), ORIGIN);
  const bad = await worker.fetch(pre('https://evil.example'), env());
  assert.equal(bad.headers.get('access-control-allow-origin'), null);
});

test('rejects a missing or malformed image before spending anything', async () => {
  assert.equal((await worker.fetch(post({}), env())).status, 400);
  assert.equal((await worker.fetch(post({image: 'not-a-data-url'}), env())).status, 400);
});

test('rejects an unsupported media type', async () => {
  const svg = 'data:image/svg+xml;base64,' + Buffer.from('<svg/>').toString('base64');
  const res = await worker.fetch(post({image: svg}), env());
  assert.equal(res.status, 415);
});

test('rejects an image over the size cap', async () => {
  const huge = 'data:image/png;base64,' + 'A'.repeat(8 * 1024 * 1024);
  const res = await worker.fetch(post({image: huge}), env());
  assert.equal(res.status, 413);
});

test('enforces the daily cap per client', async () => {
  const e = env();
  // DAILY_LIMIT is 3. The first three get past the cap and fail later, at the model call.
  const seen = [];
  for (let i = 0; i < 4; i++) seen.push((await worker.fetch(post({image: PNG}), e)).status);
  assert.equal(seen[3], 429, `fourth request should be capped, got ${seen.join(',')}`);
  assert.ok(seen.slice(0, 3).every(s => s !== 429), 'first three must not be capped');
});

test('refuses to run unconfigured rather than failing open', async () => {
  const res = await worker.fetch(post({image: PNG}), env({ANTHROPIC_API_KEY: ''}));
  assert.equal(res.status, 500);
});

test('never returns the upstream error text', async () => {
  // A valid request with a bogus key reaches the model call and fails there.
  const res = await worker.fetch(post({image: PNG}), env());
  const body = await res.json();
  assert.ok(res.status >= 400);
  assert.ok(!/api[_-]?key|sk-ant|anthropic\.com/i.test(JSON.stringify(body)), `leaked upstream detail: ${JSON.stringify(body)}`);
});
