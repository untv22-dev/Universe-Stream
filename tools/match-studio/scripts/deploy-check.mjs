// Measures how well the deployed worker actually reads a real screenshot.
//
// This is the only check that exercises the model, so it is the only one that can tell you what
// the accuracy is. Everything else in this repo stubs the endpoint. Run it against your own
// screenshots before you trust the image path, and put the number in the README instead of a claim.
//
//   node scripts/deploy-check.mjs <endpoint> <token> <image.png> [expected.json]
//
// expected.json is optional and is an array of rows in the same shape the worker returns; when
// present, the script reports per-cell accuracy instead of just printing what came back.
import fs from 'node:fs/promises';
import path from 'node:path';

const [endpoint, token, imagePath, expectedPath] = process.argv.slice(2);
if (!endpoint || !token || !imagePath) {
  console.error('usage: node scripts/deploy-check.mjs <endpoint> <token> <image.png> [expected.json]');
  process.exit(2);
}

const MEDIA = {'.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.webp': 'image/webp'};
const ext = path.extname(imagePath).toLowerCase();
if (!MEDIA[ext]) {
  console.error(`unsupported image type: ${ext}`);
  process.exit(2);
}

const bytes = await fs.readFile(imagePath);
const started = Date.now();
const response = await fetch(endpoint, {
  method: 'POST',
  headers: {'content-type': 'application/json', 'x-studio-token': token},
  body: JSON.stringify({image: `data:${MEDIA[ext]};base64,${bytes.toString('base64')}`}),
});
const elapsed = Date.now() - started;
const body = await response.json().catch(() => ({}));

if (!response.ok) {
  console.error(`HTTP ${response.status}: ${body.error ?? '(no message)'}`);
  process.exit(1);
}

const got = body.matches ?? [];
console.log(`${got.length} rows in ${(elapsed / 1000).toFixed(1)}s`);
if (body.notes) console.log(`notes: ${body.notes}`);
const unsure = got.filter(r => r.confidence === 'low');
if (unsure.length) console.log(`${unsure.length} row(s) marked low confidence`);
console.log();

const FIELDS = ['league', 'time', 'home', 'away', 'commentator', 'channel', 'featured'];

if (!expectedPath) {
  for (const [i, r] of got.entries()) {
    console.log(`${String(i + 1).padStart(2)}. ${r.time || '--:--'} | ${r.home} × ${r.away} | ${r.league} | ${r.commentator || '-'} | ${r.channel || '-'}${r.featured ? ' ★' : ''}${r.confidence === 'low' ? '  (low)' : ''}`);
  }
  console.log('\nNo expected.json given, so this printed the reading rather than scoring it.');
  process.exit(0);
}

const expected = JSON.parse(await fs.readFile(expectedPath, 'utf8'));
let correct = 0;
let total = 0;
const wrong = [];
for (let i = 0; i < Math.max(expected.length, got.length); i++) {
  for (const f of FIELDS) {
    total++;
    const want = expected[i]?.[f] ?? '';
    const have = got[i]?.[f] ?? '';
    if (String(want).trim() === String(have).trim()) correct++;
    else wrong.push(`row ${i + 1} ${f}: expected ${JSON.stringify(want)}, got ${JSON.stringify(have)}`);
  }
}

console.log(`row count: expected ${expected.length}, got ${got.length}`);
console.log(`cell accuracy: ${correct}/${total} (${(correct / total * 100).toFixed(1)}%)`);
if (wrong.length) {
  console.log('\nmismatches:');
  for (const w of wrong.slice(0, 40)) console.log('  ' + w);
  if (wrong.length > 40) console.log(`  … and ${wrong.length - 40} more`);
}
process.exit(got.length === expected.length && !wrong.length ? 0 : 1);
