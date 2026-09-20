// "Upload a screenshot, get a poster" — the path that skips typing entirely.
//
// Unlike the paste/CSV path this deliberately has no column-mapping step: the model returns rows
// already in the editor's field names, so the image goes straight through validation into the
// editor and the preview repaints. The editor stays available as a safety net, and rows the model
// was unsure about are marked so they are easy to glance at rather than re-read in full.
import {config} from './config.mjs';
import {fields, formatTime} from './core.mjs';

export const visionEnabled = () => Boolean(config.extractEndpoint);

const MAX_BYTES = 5 * 1024 * 1024;
const TYPES = /^image\/(png|jpeg|webp|gif)$/;

export class VisionError extends Error {}

// Reads the file as a data: URL so the request body is plain JSON and easy to log-free.
const toDataUrl = file =>
  new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(new VisionError('تعذر قراءة الصورة'));
    reader.readAsDataURL(file);
  });

export async function extractFromImage(file, {signal} = {}) {
  if (!visionEnabled()) throw new VisionError('قراءة الصور غير مفعّلة في هذه النسخة');
  if (!file) throw new VisionError('اختر صورة أولًا');
  if (!TYPES.test(file.type)) throw new VisionError('اختر صورة PNG أو JPG أو WebP');
  if (file.size > MAX_BYTES) throw new VisionError('حجم الصورة أكبر من 5 ميجابايت');

  const image = await toDataUrl(file);
  let response;
  try {
    response = await fetch(config.extractEndpoint, {
      method: 'POST',
      headers: {'content-type': 'application/json', 'x-studio-token': config.extractToken},
      body: JSON.stringify({image}),
      signal,
    });
  } catch (e) {
    if (e?.name === 'AbortError') throw e;
    throw new VisionError('تعذر الوصول إلى خدمة القراءة. تأكد من اتصالك.');
  }

  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new VisionError(body.error || 'تعذر تحليل الصورة');
  if (!Array.isArray(body.matches)) throw new VisionError('رد غير متوقع من خدمة القراءة');
  if (!body.matches.length) throw new VisionError(body.notes || 'لم يُعثر على جدول مباريات في هذه الصورة');

  return {rows: body.matches.map(toRow), notes: String(body.notes || '')};
}

// The worker's schema already matches `fields`, but the response is still untrusted input:
// coerce every value rather than trusting its type, and never let an extra key through.
function toRow(raw, i) {
  const text = v => String(v ?? '').trim().slice(0, 160);
  const row = {id: i + 1};
  for (const f of fields) {
    row[f] = f === 'featured' ? raw?.featured === true : f === 'time' ? formatTime(text(raw?.time)) : text(raw?.[f]);
  }
  row.lowConfidence = raw?.confidence === 'low';
  return row;
}
