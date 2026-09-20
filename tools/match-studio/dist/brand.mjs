// Everything on the poster that identifies the sender.
//
// These elements used to be pixels baked into assets/notebook.png, which is why the brand name and
// site fields in the editor did nothing: there was no way to change them short of repainting the
// photo. The plate is now text-free (assets/notebook-clean.png) and every identity element is drawn
// here, from one layout table, so changing a string is a config change rather than an image edit.
//
// Coordinates are in the renderer's 1024x1536 space, measured off the original plate so the
// redrawn elements land where the printed ones did. Angles match the photographed page tilt.

export const PLATE = 'assets/notebook-clean.png';
export const LOGO_KEY = 'brand:logo';

const INK_STICKY = '#1b0f52';
const INK_PEN = '#2417a0';
const BADGE = '#40159e';

export const defaultBrand = () => ({
  name: 'Universe IPTV',
  tag: 'SPORTS',
  site: 'http://universe-player.com',
  whatsapp: '',
  cta: 'اشترك الآن',
  showCta: true,
  sticky: 'كرة القدم\nأجمل مع',
  notes: 'مباريات كثيرة ..\nمعلقون مميزون ..\nمتعة مستمرة ..',
  footnote: 'مباريات اليوم\nحكايات جديدة ..\nمن عالم الساحرة المستديرة ..',
  watchOn: 'شاهد أيضًا على',
  logo: '',
});

// Every tunable position in one place, so nudging the layout never means touching drawing code.
const L = {
  sticky: {x: 130, y: 52, angle: 6 * Math.PI / 180, size: 33, lead: 40, width: 135},
  // markX/textX are offsets from x, kept apart so the mark and the name never overlap.
  stickyLogo: {x: 128, y: 152, angle: 6 * Math.PI / 180, size: 16, mark: 30, width: 92, markX: -52, textX: 28},
  notes: {x: 940, y: 80, angle: -5 * Math.PI / 180, size: 25, lead: 37, width: 172},
  // Stops short of x=462 where the sender badge begins.
  footnote: {x: 378, y: 1345, angle: -10 * Math.PI / 180, size: 23, lead: 33, width: 228},
  badge: {x: 462, y: 1352, w: 221, h: 63, r: 11},
  watchOn: {x: 583, y: 1437, size: 25},
  url: {x: 442, y: 1462, w: 268, h: 37, r: 10, size: 19},
  cta: {x: 310, y: 1265, w: 430, h: 44, r: 8, size: 18},
  whatsapp: {x: 525, y: 1325, size: 15},
};

// Mirrors poster.mjs's text(): centred, baseline-middle, direction picked from the script used.
function line(ctx, s, x, y, size, color, font = 'Hand', align = 'center') {
  ctx.fillStyle = color;
  ctx.font = `700 ${size}px ${font}`;
  ctx.textAlign = align;
  ctx.textBaseline = 'middle';
  ctx.direction = /[\u0600-\u06ff]/.test(s) ? 'rtl' : 'ltr';
  ctx.fillText(s, x, y);
}

function block(ctx, text, {x, y, angle, size, lead, width}, color, font = 'Hand', align = 'center') {
  const rows = String(text ?? '').split('\n').filter(s => s.trim()).slice(0, 4);
  if (!rows.length) return;
  ctx.save();
  ctx.translate(x, y);
  ctx.rotate(angle);
  rows.forEach((row, i) => {
    // Shrink rather than overflow: these sit in fixed gaps between photographed decorations.
    let size2 = size;
    ctx.font = `700 ${size2}px ${font}`;
    while (size2 > 9 && ctx.measureText(row).width > width) {
      size2 -= 1;
      ctx.font = `700 ${size2}px ${font}`;
    }
    line(ctx, row, 0, i * lead, size2, color, font, align);
  });
  ctx.restore();
}

function roundRect(ctx, {x, y, w, h, r}) {
  ctx.beginPath();
  ctx.roundRect(x, y, w, h, r);
}

// Draws the uploaded logo into a circle, or a lettered disc when there is none.
function mark(ctx, img, cx, cy, size, initial) {
  if (img) {
    const k = Math.min(size / img.width, size / img.height);
    ctx.drawImage(img, cx - img.width * k / 2, cy - img.height * k / 2, img.width * k, img.height * k);
    return;
  }
  ctx.fillStyle = '#ffffff';
  ctx.beginPath();
  ctx.arc(cx, cy, size / 2, 0, Math.PI * 2);
  ctx.fill();
  line(ctx, initial, cx, cy + 1, size * 0.62, BADGE, 'UI');
}

export function drawBrand(ctx, brand, images) {
  const b = {...defaultBrand(), ...brand};
  const logo = images?.get(LOGO_KEY) || null;
  const initial = (b.name || 'U').trim().charAt(0).toUpperCase() || 'U';

  // Sticky note, top left: tagline above the sender's mark.
  block(ctx, b.sticky, L.sticky, INK_STICKY);
  if (b.name || logo) {
    const s = L.stickyLogo;
    ctx.save();
    ctx.translate(s.x, s.y);
    ctx.rotate(s.angle);
    mark(ctx, logo, s.markX, 0, s.mark, initial);
    if (!logo) {
      ctx.strokeStyle = INK_STICKY;
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      ctx.arc(s.markX, 0, s.mark / 2, 0, Math.PI * 2);
      ctx.stroke();
    }
    let size = s.size;
    ctx.font = `700 ${size}px UI`;
    while (size > 8 && ctx.measureText(b.name).width > s.width) {
      size -= 1;
      ctx.font = `700 ${size}px UI`;
    }
    line(ctx, b.name, s.textX, 0, size, INK_STICKY, 'UI');
    ctx.restore();
  }

  // Hand-written notes, top right and bottom left.
  block(ctx, b.notes, L.notes, INK_PEN, 'Hand', 'right');
  block(ctx, b.footnote, L.footnote, INK_PEN);

  // Sender badge, bottom centre.
  if (b.name || logo) {
    const g = L.badge;
    ctx.fillStyle = BADGE;
    roundRect(ctx, g);
    ctx.fill();
    mark(ctx, logo, g.x + 38, g.y + g.h / 2, 44, initial);
    const textX = g.x + 72;
    const textW = g.w - 84;
    let size = 20;
    ctx.font = `700 ${size}px UI`;
    while (size > 9 && ctx.measureText(b.name).width > textW) {
      size -= 1;
      ctx.font = `700 ${size}px UI`;
    }
    const hasTag = Boolean((b.tag || '').trim());
    line(ctx, b.name, textX + textW / 2, g.y + (hasTag ? 22 : g.h / 2), size, '#ffffff', 'UI');
    if (hasTag) line(ctx, b.tag, textX + textW / 2, g.y + 45, 24, '#ffffff', 'UI');
  }

  if (b.watchOn) line(ctx, b.watchOn, L.watchOn.x, L.watchOn.y, L.watchOn.size, INK_PEN);

  // Site, bottom centre, in the hand-drawn box the plate used to carry.
  if (b.site) {
    const u = L.url;
    ctx.strokeStyle = INK_PEN;
    ctx.lineWidth = 2.5;
    roundRect(ctx, u);
    ctx.stroke();
    let size = u.size;
    ctx.font = `700 ${size}px UI`;
    while (size > 9 && ctx.measureText(b.site).width > u.w - 24) {
      size -= 1;
      ctx.font = `700 ${size}px UI`;
    }
    line(ctx, b.site, u.x + u.w / 2, u.y + u.h / 2, size, INK_PEN, 'UI');
  }
}

// Drawn after the table so it sits above the page, and kept below y=1250 so that enabling it
// can never disturb the schedule — tests/render.test.mjs asserts exactly that.
export function drawCallToAction(ctx, brand) {
  const b = {...defaultBrand(), ...brand};
  if (b.showCta && b.cta) {
    const c = L.cta;
    ctx.fillStyle = '#4b187c';
    roundRect(ctx, c);
    ctx.fill();
    line(ctx, b.cta, c.x + c.w / 2, c.y + c.h / 2, c.size, '#ffffff', 'UI');
  }
  if (b.whatsapp) line(ctx, `واتساب: ${b.whatsapp}`, L.whatsapp.x, L.whatsapp.y, L.whatsapp.size, '#301067', 'UI');
}
