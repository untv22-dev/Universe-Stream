// Visual regression tests for the poster renderer.
//
// These exist because two bugs shipped that no amount of unit testing on core.mjs could catch:
// a missing beginPath() let the CTA fill re-paint the last channel badge over its own text, and
// the page number was drawn inside the CTA box in a colour that vanished against it. Both are
// invisible to the parser tests and obvious the moment you look at pixels.
//
// Skipped when @napi-rs/canvas is not installed so `node --test tests/core.test.mjs` stays dependency-free.
import test from 'node:test';
import assert from 'node:assert/strict';
import {loadCanvas, load, renderTable, bulkTable} from '../scripts/harness.mjs';
import {sample} from '../dist/core.mjs';

const canvasLib = await loadCanvas();
const env = canvasLib ? await load(canvasLib) : null;
const runner = env ? test : test.skip;

const CTA = {showCta: true, cta: 'اشترك الآن', whatsapp: '+201000000000'};
// Poster coordinates are half of device pixels: the renderer draws at 1024x1536 scaled by 2.
const px = v => v * 2;

const pixels = (canvas, x, y, w, h) => canvas.getContext('2d').getImageData(x, y, w, h).data;

runner('enabling the CTA changes nothing above the CTA band', async () => {
  // The CTA box, its WhatsApp line and the page number all live below y=1250 in poster space.
  // Anything the CTA draws higher up is a stray path — which is exactly how the ghost rectangle
  // that erased the last channel badge showed up.
  const withCta = await renderTable(env, sample, {config: CTA});
  const without = await renderTable(env, sample, {config: {}});
  const band = [0, 0, 2048, px(1250)];
  const a = pixels(withCta.canvas, ...band);
  const b = pixels(without.canvas, ...band);

  let differing = 0;
  for (let i = 0; i < a.length; i += 4) if (a[i] !== b[i] || a[i + 1] !== b[i + 1] || a[i + 2] !== b[i + 2]) differing++;
  assert.equal(differing, 0, `${differing} pixels above the CTA band changed when the CTA was enabled`);
});

runner('page number is drawn on paper, clear of the CTA box', async () => {
  // Old behaviour drew it at x=530 y=1302, inside the CTA rect (310..740 x 1265..1309),
  // in ink (#301067) against purple (#4b187c) — present in the buffer, unreadable on the page.
  const {canvas, prepared} = await renderTable(env, bulkTable(40), {config: CTA, page: 2});
  assert.ok(prepared.pages.length > 1, 'fixture must paginate for this test to mean anything');

  const region = [px(795), px(1272), px(70), px(30)];
  const data = pixels(canvas, ...region);
  let dark = 0;
  let total = 0;
  for (let i = 0; i < data.length; i += 4) {
    total++;
    if (data[i] < 120 && data[i + 1] < 120 && data[i + 2] < 160) dark++;
  }
  assert.ok(dark > 40, `expected glyph pixels for the page number, found ${dark}`);
  assert.ok(dark / total < 0.5, 'region should be mostly paper, not a filled box');

  // Ink (#301067) and CTA purple (#4b187c) are too close to separate by colour once antialiased,
  // so prove the number sits clear of the box instead: toggling the CTA must not touch this region.
  const noCta = await renderTable(env, bulkTable(40), {config: {}, page: 2});
  const plain = pixels(noCta.canvas, ...region);
  let differing = 0;
  for (let i = 0; i < data.length; i += 4) if (data[i] !== plain[i] || data[i + 1] !== plain[i + 1] || data[i + 2] !== plain[i + 2]) differing++;
  assert.equal(differing, 0, 'page number region is disturbed by the CTA box');
});

runner('channel badge grows to contain long channel text', async () => {
  // The badge used to be a fixed 93x34 while the text wrapped unbounded, so long channel
  // names spilled over the column heading and the section border.
  const table =
    'الدوري | الوقت | الفريق الأول | الفريق الثاني | المعلق | القناة | مميز\n' +
    'الدوري الإسباني | 21:30 | ريال مدريد | برشلونة | عصام الشوالي | 1 HD 4K UHD EXTRA | نعم';
  const {canvas} = await renderTable(env, table, {config: CTA});

  // "1 HD 4K UHD EXTRA" wraps to four lines, so the badge must be roughly 4*19+10 = 86px tall
  // rather than the old fixed 34px. Measure the badge fill in the channel column and compare
  // against both: comfortably above a 34px box, close to an 86px one.
  const data = pixels(canvas, px(200), px(347), px(93), px(120));
  let purple = 0;
  for (let i = 0; i < data.length; i += 4) {
    if (Math.abs(data[i] - 73) < 20 && Math.abs(data[i + 1] - 19) < 20 && Math.abs(data[i + 2] - 131) < 20) purple++;
  }
  const area = h => px(93) * px(h);
  assert.ok(purple > area(34) * 1.6, `badge did not grow past its 34px default (fill was ${purple}px, a 34px badge is ~${area(34)}px)`);
  assert.ok(purple < area(120), 'badge fill exceeded the sampled column strip');

  // And the text must be inside it: white glyph pixels on the purple fill.
  let white = 0;
  for (let i = 0; i < data.length; i += 4) if (data[i] > 235 && data[i + 1] > 235 && data[i + 2] > 235) white++;
  assert.ok(white > 200, `expected channel glyphs inside the badge, found ${white}`);
});

runner('pagination keeps every row and repeats the league header', async () => {
  const {prepared} = await renderTable(env, bulkTable(40), {config: CTA});
  const total = prepared.pages.reduce((n, page) => n + page.reduce((m, s) => m + s.rows.length, 0), 0);
  assert.equal(total, 40);
  for (const page of prepared.pages) assert.ok(page.every(s => s.league === 'الدوري الإنجليزي'));
});

runner('unknown clubs warn instead of borrowing another club crest', async () => {
  const table =
    'الدوري | الوقت | الفريق الأول | الفريق الثاني | المعلق | القناة | مميز\n' +
    'الدوري الإسباني | 21:30 | فريق غير موجود إطلاقا | نادي وهمي تماما | معلق | 1 | لا';
  const {prepared} = await renderTable(env, table, {config: CTA});
  assert.deepEqual(prepared.unknown, ['فريق غير موجود إطلاقا', 'نادي وهمي تماما']);
});
