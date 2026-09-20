// Renders the sample poster to a PNG you can actually look at.
//
// This is a render check, not browser QA: it exercises the same layout and drawing code the page
// uses, under @napi-rs/canvas rather than a real browser. Fonts, RTL shaping and image decoding
// can still differ from Chrome or Safari.
//
//   node scripts/check-render.mjs [outputDir]
//
import fs from 'node:fs/promises';
import path from 'node:path';
import {loadCanvas, load, renderTable, bulkTable} from './harness.mjs';

const outDir = path.resolve(process.argv[2] ?? 'out');

const canvas = await loadCanvas();
if (!canvas) {
  console.error('@napi-rs/canvas is not installed. Run `npm install` first.');
  process.exit(1);
}

const env = await load(canvas);
const {sample} = env.core;
const config = {showCta: true, cta: 'اشترك الآن', whatsapp: '+201000000000'};

await fs.mkdir(outDir, {recursive: true});

const shots = [
  ['sample', sample, {config}],
  ['sample-no-cta', sample, {config: {}}],
  ['long-channel', 'الدوري | الوقت | الفريق الأول | الفريق الثاني | المعلق | القناة | مميز\nالدوري الإسباني | 21:30 | ريال مدريد | برشلونة | عصام الشوالي | 1 HD 4K UHD EXTRA | نعم', {config}],
  ['paginated-last', bulkTable(40), {config, page: 2}],
];

const report = [];
for (const [name, table, opts] of shots) {
  const {canvas: c, prepared} = await renderTable(env, table, opts);
  const file = path.join(outDir, `${name}.png`);
  await fs.writeFile(file, c.toBuffer('image/png'));
  report.push({
    name,
    file: path.relative(process.cwd(), file),
    pages: prepared.pages.length,
    rowsPerPage: prepared.pages.map(p => p.reduce((n, s) => n + s.rows.length, 0)),
    unknownCrests: prepared.unknown,
    size: [c.width, c.height],
  });
}

console.log(JSON.stringify(report, null, 2));
