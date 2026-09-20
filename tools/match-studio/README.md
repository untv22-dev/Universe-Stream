# Universe IPTV Match Studio

Static Arabic match-poster generator. Serve `dist/` with any static host. Import and rendering are
entirely client-side and the runtime downloads no external assets.

Supports paste, CSV, XLSX/XLS, explicit column mapping, worksheet selection, editing, ordering,
date, featured rows, deterministic PNG export and pagination. Club lookup covers 76 clubs from the
four reference leagues with Arabic/English aliases. Unknown names are shown with neutral marks and
warnings.

## Poster identity

Everything that names the sender — the sticky-note tagline, the margin notes, the badge, the site
box, the WhatsApp line and the call to action — is drawn at render time by `dist/brand.mjs` and
editable in the page, including an uploaded logo. Identity settings persist in `localStorage`;
the match table does not.

The background plate `dist/assets/notebook-clean.png` is generated from the original photograph by
`scripts/build-plate.mjs`, which removes the printed branding. Regenerate it after changing the
source plate. Two decorations are deliberately left in the photograph: the hand-drawn ball with
"كرة القدم أسلوب حياة", and the small label on the pen. They are drawn in the same ink as the
surrounding doodles and cannot be lifted cleanly, so they stay as part of the artwork.

## Checks

```bash
npm install
npm test                    # 12 unit + render tests, no browser needed
npm run check-render        # writes sample posters to out/ for eyeballing
npm run serve               # static server on dist/
node scripts/browser-check.mjs http://127.0.0.1:8080   # needs `npm i -D playwright`
```

`npm test` covers the parser, pagination and — through `@napi-rs/canvas` — the renderer itself,
including the invariants behind three shipped bugs: a stray canvas path that erased the last
channel badge, a page number drawn invisibly inside the call-to-action box, and a fixed-height
channel badge that long channel names overflowed.

`scripts/browser-check.mjs` is the only check that exercises the real page: the file picker,
`localStorage`, `canvas.toBlob()` and the download. Earlier versions of this project had no way to
run it, and the browser path was untested.

## Scope

The example data is fictional design data, not verified fixtures. This project does not retrieve
schedules. Asset sources and licences are in `dist/assets/SOURCES.txt`.
