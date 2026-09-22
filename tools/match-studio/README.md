# Universe IPTV Match Studio

Static Arabic match-poster generator. Serve `dist/` with any static host. Import and rendering are
entirely client-side and the runtime downloads no external assets.

Supports paste, CSV, XLSX/XLS, explicit column mapping, worksheet selection, editing, ordering,
date, featured rows, deterministic PNG export and pagination. Multi-page posters download in one
action, and the table is kept on the device between visits.

Club lookup covers 150 clubs across eight leagues — the Premier League, LaLiga, Ligue 1, the Süper
Lig, Serie A, the Bundesliga, Liga Portugal and the Eredivisie — with Arabic and English aliases.
A name that is a character or two out is repaired and the correction is reported; a name that is
genuinely unknown gets a neutral mark and a warning, never another club's crest.

**No Arab leagues yet.** The crest source (`scripts/fetch-assets.mjs`) carries 25 European leagues
and no Saudi, Egyptian or other Arab competition, so those are not covered. To add one, drop the
PNGs into `dist/assets/clubs/`, add entries to `dist/assets/clubs.json`, and add the competition
to `leagueDefs` in `dist/catalog.mjs` — the drawing code needs no changes. Club badges are
trademarks of their clubs; see `dist/assets/SOURCES.txt`.

A kick-off that has not been announced can be entered as `TBD`, `لم يحدد` or `—` and renders as a
placeholder instead of failing validation.

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

## Reading a table from an image

Upload or paste a screenshot of a schedule from any app and the rows are extracted, matched to
crests and rendered — no column mapping and nothing to type. The step stays hidden until you
configure it, so the page still works as a purely static tool.

This needs a backend, because reading an Arabic table out of an arbitrary screenshot needs a vision
model and a vision model needs an API key, which cannot live in a static page. `worker/` is that
backend: a Cloudflare Worker that holds the key, checks the origin and a shared token, caps usage
per client per day, and returns rows in the studio's own field names.

```bash
cd worker
npm install
npx wrangler kv namespace create RATE     # paste the id into wrangler.toml
npx wrangler secret put ANTHROPIC_API_KEY
npx wrangler secret put CLIENT_TOKEN      # any long random string
npx wrangler deploy
```

Then set `extractEndpoint` and `extractToken` in `dist/config.mjs`, and add your site's origin to
`ALLOWED_ORIGINS` in `wrangler.toml`. `CLIENT_TOKEN` is visible to anyone who opens the page — it
only stops other sites using the endpoint. The origin allowlist and `DAILY_LIMIT` are what bound
the spend.

**On accuracy.** The aim is no typing in the normal case, and that is what the flow does. It is not
a guarantee of a perfect reading: compressed, skewed or low-contrast images will lose cells. So
rows the model was unsure about are flagged in the editor rather than quietly trusted, club names
that needed an approximate match are reported rather than silently corrected, and the editor stays
there as a safety net. Measure your own accuracy before trusting it:

```bash
node scripts/deploy-check.mjs <endpoint> <token> shot.png [expected.json]
```

That is the only check in this repo that exercises the model; everything else stubs the endpoint.

## Checks

```bash
npm install
npm test                    # 12 unit + render tests, no browser needed
npm run check-render        # writes sample posters to out/ for eyeballing
npm run serve               # static server on dist/
node scripts/browser-check.mjs http://127.0.0.1:8080   # needs `npm i -D playwright`
(cd worker && npm test)    # the worker's origin / token / cap / size guards
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
