// Usage: node render.js <outDir> [frame,frame,...]   (no list = all 450 frames)
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

(async () => {
  const out = process.argv[2];
  const only = process.argv[3] ? process.argv[3].split(',').map(Number) : null;
  fs.mkdirSync(out, { recursive: true });
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1080, height: 1920 }, deviceScaleFactor: 1 });
  await page.goto('file://' + path.join(__dirname, 'ad.html'));
  await page.evaluate(() => document.fonts.ready);
  await page.waitForFunction(() => [...document.images].every(i => i.complete && i.naturalWidth > 0));
  const fontsOk = await page.evaluate(() => document.fonts.check('900 40px Cairo') && document.fonts.check('700 40px Cairo'));
  if (!fontsOk) throw new Error('Cairo font not loaded');
  const frames = only || [...Array(450).keys()];
  for (const f of frames) {
    await page.evaluate(n => window.render(n), f);
    await page.screenshot({ path: path.join(out, `f${String(f).padStart(4, '0')}.png`), omitBackground: false });
  }
  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
