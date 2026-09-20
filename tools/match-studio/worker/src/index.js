// Turns a screenshot of a match schedule into the rows the studio's editor expects.
//
// This exists because the page itself cannot do it: reading an Arabic table out of an arbitrary
// screenshot needs a vision model, and a vision model needs an API key, which cannot live in a
// static page. The worker is the smallest thing that can hold the key — it validates the request,
// rate-limits it, calls the model and returns rows in the studio's own field names.
//
// Setup:
//   wrangler kv namespace create RATE        # paste the id into wrangler.toml
//   wrangler secret put ANTHROPIC_API_KEY
//   wrangler secret put CLIENT_TOKEN         # any long random string; also set in dist/config.mjs
//   wrangler deploy
import Anthropic from '@anthropic-ai/sdk';

// Mirrors `fields` in dist/core.mjs. Kept in the tool schema so the model returns rows the editor
// can use directly, with no column-mapping step for the user to do by hand.
const ROW_SCHEMA = {
  type: 'object',
  properties: {
    league: {type: 'string', description: 'Competition name, exactly as written in the image.'},
    time: {type: 'string', description: 'Kick-off as HH:mm in 24-hour form, e.g. "21:30". Empty string if the image does not show one.'},
    home: {type: 'string', description: 'First/home team, exactly as written in the image.'},
    away: {type: 'string', description: 'Second/away team, exactly as written in the image.'},
    commentator: {type: 'string', description: 'Commentator, or empty string if absent.'},
    channel: {type: 'string', description: 'Channel name or number, or empty string if absent.'},
    featured: {type: 'boolean', description: 'True only if the image marks this match as highlighted or featured.'},
    confidence: {type: 'string', enum: ['high', 'low'], description: 'low when any field on this row was hard to read.'},
  },
  required: ['league', 'time', 'home', 'away', 'commentator', 'channel', 'featured', 'confidence'],
  additionalProperties: false,
};

const TOOL = {
  name: 'emit_matches',
  description: 'Return every match row read from the image, in the order they appear.',
  strict: true,
  input_schema: {
    type: 'object',
    properties: {
      matches: {type: 'array', items: ROW_SCHEMA},
      notes: {type: 'string', description: 'Empty string, or one short sentence about anything unreadable.'},
    },
    required: ['matches', 'notes'],
    additionalProperties: false,
  },
};

const SYSTEM = `You read football match schedules out of screenshots and photographs.

Rules:
- Transcribe what is in the image. Never invent a match, a time, a commentator or a channel.
- Keep team and competition names in the script and spelling the image uses. Do not translate,
  correct or normalise them.
- Times: convert to 24-hour HH:mm. Convert Arabic-Indic digits to Western. If a row shows no time,
  or shows something like "TBD", return an empty string rather than guessing one.
- Leave commentator and channel as empty strings when the image does not show them.
- Set featured only when the image visibly marks the row (a star, a highlight colour, bold).
- Set confidence to "low" for any row where you had to strain to read a field.
- If the image is not a match schedule at all, return an empty matches array and say so in notes.

Always answer by calling the emit_matches tool.`;

const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
const MEDIA_TYPES = new Set(['image/png', 'image/jpeg', 'image/webp', 'image/gif']);

const json = (body, status, origin) =>
  new Response(JSON.stringify(body), {
    status,
    headers: {
      'content-type': 'application/json; charset=utf-8',
      ...corsHeaders(origin),
      'cache-control': 'no-store',
    },
  });

function corsHeaders(origin) {
  if (!origin) return {};
  return {
    'access-control-allow-origin': origin,
    'access-control-allow-headers': 'content-type,x-studio-token',
    'access-control-allow-methods': 'POST,OPTIONS',
    'access-control-max-age': '86400',
    vary: 'origin',
  };
}

// Only origins listed in ALLOWED_ORIGINS get CORS headers back, so a random page cannot spend
// the key from a visitor's browser.
function allowedOrigin(request, env) {
  const origin = request.headers.get('origin');
  if (!origin) return null;
  const allowed = String(env.ALLOWED_ORIGINS || '')
    .split(',')
    .map(s => s.trim())
    .filter(Boolean);
  return allowed.includes(origin) ? origin : null;
}

// A public endpoint holding someone's API key is worth draining, so cap per client per day.
async function overLimit(env, key) {
  if (!env.RATE) return false;
  const limit = Number(env.DAILY_LIMIT || 60);
  const slot = `${key}:${new Date().toISOString().slice(0, 10)}`;
  const used = Number((await env.RATE.get(slot)) || 0);
  if (used >= limit) return true;
  // Not atomic — two requests in the same instant can both read the same count. That is fine for
  // a spend cap: the drift is one or two requests, not an order of magnitude.
  await env.RATE.put(slot, String(used + 1), {expirationTtl: 60 * 60 * 36});
  return false;
}

async function readImage(request) {
  const type = request.headers.get('content-type') || '';
  if (type.startsWith('multipart/form-data')) {
    const form = await request.formData();
    const file = form.get('image');
    if (!file || typeof file === 'string') throw new HttpError(400, 'لم تصل أي صورة');
    const bytes = new Uint8Array(await file.arrayBuffer());
    return {bytes, mediaType: file.type};
  }
  const body = await request.json().catch(() => null);
  if (!body?.image) throw new HttpError(400, 'لم تصل أي صورة');
  const match = /^data:([^;,]+);base64,(.*)$/s.exec(body.image);
  if (!match) throw new HttpError(400, 'صيغة الصورة غير مفهومة');
  const binary = atob(match[2]);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
  return {bytes, mediaType: match[1]};
}

class HttpError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

const toBase64 = bytes => {
  let s = '';
  for (let i = 0; i < bytes.length; i += 0x8000) s += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  return btoa(s);
};

export default {
  async fetch(request, env) {
    const origin = allowedOrigin(request, env);

    if (request.method === 'OPTIONS') return new Response(null, {status: 204, headers: corsHeaders(origin)});
    if (request.method !== 'POST') return json({error: 'POST only'}, 405, origin);
    if (new URL(request.url).pathname !== '/extract') return json({error: 'not found'}, 404, origin);
    if (request.headers.get('origin') && !origin) return json({error: 'origin not allowed'}, 403, null);

    try {
      if (!env.ANTHROPIC_API_KEY) throw new HttpError(500, 'الخادم غير مهيأ: مفتاح API غير مضبوط');
      if (env.CLIENT_TOKEN && request.headers.get('x-studio-token') !== env.CLIENT_TOKEN) {
        throw new HttpError(401, 'رمز الوصول غير صحيح');
      }

      const client = request.headers.get('x-studio-token') || request.headers.get('cf-connecting-ip') || 'anon';
      if (await overLimit(env, client)) throw new HttpError(429, 'تجاوزت حد الاستخدام اليومي. حاول غدًا.');

      const {bytes, mediaType} = await readImage(request);
      if (bytes.byteLength > MAX_IMAGE_BYTES) throw new HttpError(413, 'حجم الصورة أكبر من 5 ميجابايت');
      if (!MEDIA_TYPES.has(mediaType)) throw new HttpError(415, 'اختر صورة PNG أو JPG أو WebP');

      const anthropic = new Anthropic({apiKey: env.ANTHROPIC_API_KEY});
      const response = await anthropic.messages.create({
        model: 'claude-opus-5',
        max_tokens: 16000,
        system: SYSTEM,
        tools: [TOOL],
        tool_choice: {type: 'tool', name: 'emit_matches'},
        messages: [{
          role: 'user',
          content: [
            {type: 'image', source: {type: 'base64', media_type: mediaType, data: toBase64(bytes)}},
            {type: 'text', text: 'Read every match row in this schedule and return it with emit_matches.'},
          ],
        }],
      });

      if (response.stop_reason === 'refusal') throw new HttpError(422, 'تعذر قراءة هذه الصورة');
      if (response.stop_reason === 'max_tokens') throw new HttpError(422, 'الجدول في الصورة أطول مما يمكن قراءته دفعة واحدة. جرّب قصّ الصورة إلى جزأين.');

      const call = response.content.find(b => b.type === 'tool_use' && b.name === 'emit_matches');
      if (!call) throw new HttpError(502, 'لم يرجع النموذج جدولًا');

      return json({matches: call.input.matches ?? [], notes: call.input.notes ?? ''}, 200, origin);
    } catch (err) {
      if (err instanceof HttpError) return json({error: err.message}, err.status, origin);
      // Never surface the upstream message: it can carry key fragments and request internals.
      console.error('extract failed', err);
      return json({error: 'تعذر تحليل الصورة. حاول مرة أخرى.'}, 502, origin);
    }
  },
};
