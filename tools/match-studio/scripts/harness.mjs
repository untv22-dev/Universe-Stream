// Runs the browser renderer under Node so the poster can be checked without a browser.
// Used by scripts/check-render.mjs and tests/render.test.mjs.
//
// The dist modules touch document/Image/fetch at import time, so the globals below must be
// installed before the first dynamic import — hence the explicit ordering in load().
import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

export const DIST = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../dist');

// Resolved lazily so a plain `node --test tests/core.test.mjs` never needs the dependency.
export async function loadCanvas() {
  try {
    return await import('@napi-rs/canvas');
  } catch {
    return null;
  }
}

export async function load(canvas) {
  const {createCanvas, loadImage, GlobalFonts} = canvas;
  GlobalFonts.registerFromPath(path.join(DIST, 'assets/hand.ttf'), 'Hand');
  GlobalFonts.registerFromPath(path.join(DIST, 'assets/ui.ttf'), 'UI');

  globalThis.document = {fonts: {load: async () => {}}, createElement: () => createCanvas(1024, 1536)};
  globalThis.Image = class {
    set src(src) {
      loadImage(path.resolve(DIST, src))
        .then(im => {
          Object.assign(this, {width: im.width, height: im.height, _img: im});
          this.onload?.();
        })
        .catch(() => this.onerror?.());
    }
  };
  globalThis.fetch = async src => ({
    ok: true,
    json: async () => JSON.parse(await fs.readFile(path.resolve(DIST, src), 'utf8')),
  });

  const core = await import(path.join(DIST, 'core.mjs'));
  const {loadCatalog} = await import(path.join(DIST, 'catalog.mjs'));
  const poster = await import(path.join(DIST, 'poster.mjs'));
  return {core, poster, catalog: await loadCatalog(), createCanvas};
}

// Renders one page of a delimited table and returns the canvas plus the prepared layout.
export async function renderTable(env, text, {config = {}, page = 0, date = '2026-09-20'} = {}) {
  const {core, poster, catalog, createCanvas} = env;
  const matrix = core.parseDelimited(text);
  const rows = core.mapRows(matrix.slice(1), core.detectMapping(matrix[0]));
  const prepared = await poster.prepare(rows, catalog);
  // The harness Image wrapper carries the real image on _img; the renderer wants that.
  for (const [url, img] of prepared.images) if (img) prepared.images.set(url, img._img);
  const canvas = createCanvas(2048, 3072);
  poster.render(canvas, prepared.pages[page], date, catalog, prepared.images, page, prepared.pages.length, config);
  return {canvas, prepared, rows};
}

// A one-league table long enough to force `pages` pages.
export const bulkTable = n =>
  ['الدوري | الوقت | الفريق الأول | الفريق الثاني | المعلق | القناة | مميز']
    .concat(
      Array.from(
        {length: n},
        (_, i) => `الدوري الإنجليزي | ${String(10 + (i % 12)).padStart(2, '0')}:00 | ليفربول | ارسنال | معلق رقم ${i + 1} | ${(i % 9) + 1} | لا`,
      ),
    )
    .join('\n');
