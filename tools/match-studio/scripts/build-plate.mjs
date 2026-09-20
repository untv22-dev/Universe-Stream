// Regenerates dist/assets/notebook-clean.png from the original photographed plate.
//
// The original notebook.png has the sender's identity printed into it: the sticky-note tagline and
// logo, the margin notes, the "Universe IPTV SPORTS" pill, "شاهد أيضًا على" and the site box. That
// is why the brand name and site fields in the editor used to do nothing. dist/brand.mjs draws all
// of those at render time instead, and needs a plate with none of them on it.
//
// Run after changing the source plate:  node scripts/build-plate.mjs
//
// The brand ink (dark purple/blue, luminance ~50-110) is much darker than the notebook's own
// ruled lines (~195-215) and paper (~225-240), so a luminance threshold inside each zone selects
// the branding without touching the rules. The resulting mask is thin strokes, which Laplace
// diffusion fills from their own immediate surroundings — rules included, since the pixels either
// side of a stroke crossing a rule are themselves rule-coloured.
import {createCanvas, loadImage} from '@napi-rs/canvas';
import fs from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const SRC = path.join(ROOT, 'dist/assets/notebook.png');
const OUT = path.join(ROOT, 'dist/assets/notebook-clean.png');

// [x, y, w, h, threshold, dilate] in 1024x1536 poster space.
export const ZONES = {
  // Stops at x=215; the hand-lettered title starts at x=254 and must survive untouched.
  sticky:      [ 48,  16, 168, 176, 165, 3],  // "كرة القدم أجمل مع" + heart + logo, on the yellow note
  // Starts at x=766 to take the trailing "‏.." ellipses; the title's last letter ends at x=758.
  sideNotes:   [766,  60, 180, 130, 150, 3],  // "مباريات كثيرة .. معلقون مميزون .. متعة مستمرة .."
  bottomLeft:  [240, 1305, 278, 138, 150, 3], // "مباريات اليوم / حكايات جديدة / من عالم الساحرة المستديرة"
  // Threshold 200 rather than 170: the pill carries a soft drop shadow that survives a darker cut.
  logoBadge:   [440, 1332, 268, 104, 215, 4], // the purple "Universe IPTV SPORTS" pill
  watchOn:     [505, 1418, 158,  32, 150, 3], // "شاهد أيضًا على"
  urlBox:      [432, 1452, 288,  54, 185, 3], // the outlined http://universe-player.com box
};

const lum = (r, g, b) => 0.299 * r + 0.587 * g + 0.114 * b;

export async function buildPlate() {
  const im = await loadImage(SRC);
  const W = im.width, H = im.height;
  const canvas = createCanvas(W, H);
  const ctx = canvas.getContext('2d');
  ctx.drawImage(im, 0, 0);
  const img = ctx.getImageData(0, 0, W, H);
  const d = img.data;

  let mask = new Uint8Array(W * H);
  for (const [x0, y0, w, h, threshold] of Object.values(ZONES)) {
    for (let y = y0; y < y0 + h; y++)
      for (let x = x0; x < x0 + w; x++) {
        const i = (y * W + x) * 4;
        if (lum(d[i], d[i + 1], d[i + 2]) < threshold) mask[y * W + x] = 1;
      }
  }

  // Dilate per zone so antialiased stroke edges come along, but never past the zone bounds.
  for (const [x0, y0, w, h, , dilate] of Object.values(ZONES)) {
    for (let pass = 0; pass < dilate; pass++) {
      const next = mask.slice();
      for (let y = Math.max(1, y0); y < Math.min(H - 1, y0 + h); y++)
        for (let x = Math.max(1, x0); x < Math.min(W - 1, x0 + w); x++) {
          const i = y * W + x;
          if (mask[i] || mask[i - 1] || mask[i + 1] || mask[i - W] || mask[i + W]) next[i] = 1;
        }
      mask = next;
    }
  }

  const todo = [];
  for (let y = 1; y < H - 1; y++) for (let x = 1; x < W - 1; x++) if (mask[y * W + x]) todo.push((y * W + x) * 4);
  console.error(`masked ${todo.length} px (${(todo.length / (W * H) * 100).toFixed(2)}% of the plate)`);

  // Seed from the nearest unmasked pixel on the same row so relaxation starts near the answer.
  for (const i of todo) {
    const p = i / 4, y = Math.floor(p / W);
    let l = p, r = p;
    while (l > y * W && mask[l]) l--;
    while (r < (y + 1) * W - 1 && mask[r]) r++;
    for (let c = 0; c < 3; c++) d[i + c] = (d[l * 4 + c] + d[r * 4 + c]) / 2;
  }

  for (let it = 0; it < 1500; it++) {
    for (const i of todo) {
      for (let c = 0; c < 3; c++) {
        const avg = (d[i - 4 + c] + d[i + 4 + c] + d[i - W * 4 + c] + d[i + W * 4 + c]) / 4;
        d[i + c] += 1.9 * (avg - d[i + c]);
      }
    }
  }

  // Diffusion produces a perfectly smooth fill, which reads as a blurred patch against the
  // photographic grain around it. Measure the grain just outside each zone and put it back.
  const maskedAt = (x, y) => x < 0 || y < 0 || x >= W || y >= H || mask[y * W + x];
  for (const [x0, y0, w, h] of Object.values(ZONES)) {
    let sum = 0, n = 0;
    for (let y = y0; y < y0 + h; y++)
      for (let x = x0; x < x0 + w; x++) {
        // High-pass residual of clean pixels inside the zone bounds: pixel minus its 5px neighbours.
        if (maskedAt(x, y) || maskedAt(x - 2, y) || maskedAt(x + 2, y) || maskedAt(x, y - 2) || maskedAt(x, y + 2)) continue;
        const i = (y * W + x) * 4;
        const around = (d[i - 8] + d[i + 8] + d[i - W * 8] + d[i + W * 8]) / 4;
        sum += (d[i] - around) ** 2; n++;
      }
    if (n < 200) continue;
    const sigma = Math.sqrt(sum / n);
    for (let y = y0; y < y0 + h; y++)
      for (let x = x0; x < x0 + w; x++) {
        if (!mask[y * W + x]) continue;
        const i = (y * W + x) * 4;
        // Box-Muller would be tidier, but paper grain is fine with a cheap symmetric draw.
        const noise = (Math.random() + Math.random() + Math.random() - 1.5) * sigma;
        for (let c = 0; c < 3; c++) d[i + c] = Math.max(0, Math.min(255, d[i + c] + noise));
      }
  }

  ctx.putImageData(img, 0, 0);
  return canvas;
}

if (import.meta.url === `file://${path.resolve(process.argv[1])}`) {
  const out = process.argv[2] ? path.resolve(process.argv[2]) : OUT;
  await fs.writeFile(out, (await buildPlate()).toBuffer('image/png'));
  console.error('wrote ' + path.relative(process.cwd(), out));
}
