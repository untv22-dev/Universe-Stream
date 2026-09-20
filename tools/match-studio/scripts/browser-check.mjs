// Drives the real page in a real browser.
//
// The unit and render tests run the layout code under Node; they cannot tell you whether the file
// picker fires, whether localStorage survives a reload, or whether canvas.toBlob() actually yields
// a PNG. This does, and it is the check the project previously had no way to run.
//
//   npm run serve &            # or any static server on dist/
//   node scripts/browser-check.mjs [http://127.0.0.1:8080]
//
// Needs playwright (`npm i -D playwright`) and a Chromium. Set CHROMIUM_PATH if Playwright's own
// download is not present, e.g. CHROMIUM_PATH=/opt/pw-browsers/chromium-1194/chrome-linux/chrome
import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const BASE = process.argv[2] ?? 'http://127.0.0.1:8080';
const LOGO = path.join(ROOT, 'dist/assets/clubs/Real_Madrid.png');

let chromium;
try {
  ({chromium} = await import('playwright'));
} catch {
  console.error('playwright is not installed. Run: npm i -D playwright');
  process.exit(1);
}

const launch = {};
if (process.env.CHROMIUM_PATH) launch.executablePath = process.env.CHROMIUM_PATH;

const results = [];
const check = (name, pass, detail = '') => {
  results.push({name, pass, detail});
  console.log(`${pass ? 'ok  ' : 'FAIL'} ${name}${detail ? ' — ' + detail : ''}`);
};

const browser = await chromium.launch(launch);
const context = await browser.newContext({viewport: {width: 1440, height: 1000}, acceptDownloads: true});
const page = await context.newPage();

const problems = [];
page.on('console', m => m.type() === 'error' && problems.push('console: ' + m.text()));
page.on('pageerror', e => problems.push('pageerror: ' + e.message));
page.on('requestfailed', r => problems.push('requestfailed: ' + r.url()));

const ready = () => page.waitForFunction(() => !document.getElementById('download').disabled, {timeout: 30000});
// The editor debounces re-rendering by 220ms, and a logo upload has to wait on FileReader first,
// so waiting only for "enabled" can observe the *previous* render and miss the change entirely.
const settle = async () => {
  await page.waitForTimeout(500);
  await ready();
};
// A cheap fingerprint of the rendered poster, enough to prove a setting reached the canvas.
const fingerprint = () => page.evaluate(() => {
  const url = document.getElementById('poster').toDataURL();
  return url.length + ':' + url.slice(-96);
});

try {
  await page.goto(`${BASE}/index.html`, {waitUntil: 'networkidle'});
  await ready();

  const initial = await page.evaluate(() => ({
    count: document.getElementById('count').textContent,
    size: [document.getElementById('poster').width, document.getElementById('poster').height],
    missing: ['name', 'tag', 'site', 'whatsapp', 'sticky', 'notes', 'footnote', 'watchOn', 'cta', 'showCta', 'logo']
      .filter(k => !document.getElementById('brand-' + k)),
  }));
  check('sample renders at 2048x3072', initial.size[0] === 2048 && initial.size[1] === 3072, initial.count);
  check('every brand control is present', initial.missing.length === 0, initial.missing.join(', '));

  const base = await fingerprint();
  await page.fill('#brand-name', 'كورة بلس');
  await page.fill('#brand-site', 'https://kooraplus.tv');
  await page.fill('#brand-notes', 'جودة 4K ..\nبدون تقطيع ..');
  await settle();
  const textChanged = await fingerprint();
  check('brand text reaches the poster', base !== textChanged);

  await page.setInputFiles('#brand-logo', LOGO);
  await settle();
  const logoChanged = await fingerprint();
  check('uploaded logo reaches the poster', textChanged !== logoChanged);
  check('logo preview appears', (await page.locator('#brand-logo-preview img').count()) === 1);

  await page.reload({waitUntil: 'networkidle'});
  await ready();
  const restored = await page.evaluate(() => ({
    name: document.getElementById('brand-name').value,
    logos: document.getElementById('brand-logo-preview').children.length,
  }));
  check('brand survives a reload', restored.name === 'كورة بلس' && restored.logos === 1, JSON.stringify(restored));
  check('reloaded poster is identical', (await fingerprint()) === logoChanged);

  await page.uncheck('#brand-showCta');
  await settle();
  check('CTA toggle reaches the poster', (await fingerprint()) !== logoChanged);
  await page.check('#brand-showCta');
  await settle();

  await page.click('#brand-reset');
  await settle();
  check('reset restores the defaults', (await page.inputValue('#brand-name')) === 'Universe IPTV');

  const [download] = await Promise.all([page.waitForEvent('download'), page.click('#download')]);
  const file = path.join('/tmp', download.suggestedFilename());
  await download.saveAs(file);
  const bytes = await fs.readFile(file);
  check('download is a real PNG', bytes.subarray(0, 8).toString('hex') === '89504e470d0a1a0a', `${download.suggestedFilename()}, ${bytes.length} bytes`);
  await fs.rm(file, {force: true});

  await page.fill('#paste', 'الدوري | الوقت | الفريق الأول | الفريق الثاني | المعلق | القناة | مميز\nالدوري الإسباني | 21:30 | ريال مدريد | برشلونة | عصام الشوالي | 1 | نعم');
  await page.click('#import');
  await page.click('#apply');
  await settle();
  check('paste import works', (await page.locator('#count').textContent()).startsWith('1 '));

  check('no console or page errors', problems.length === 0, problems.slice(0, 3).join(' | '));
} finally {
  await browser.close();
}

const failed = results.filter(r => !r.pass).length;
console.log(`\n${results.length - failed}/${results.length} browser checks passed`);
process.exit(failed ? 1 : 0);
