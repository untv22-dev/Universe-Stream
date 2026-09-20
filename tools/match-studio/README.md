# Universe IPTV Match Studio

Static Arabic match-poster generator. Serve `dist/` with any static host. All import and rendering is client-side. Runtime downloads no external assets.

Supports paste, CSV, XLSX/XLS, explicit column mapping, worksheet selection, editing, ordering, date, featured rows, deterministic PNG export and pagination. Club lookup covers 76 clubs from the four reference leagues with Arabic/English aliases. Unknown names are shown with neutral marks and warnings.

Validation: `node --test tests/core.test.mjs`. `scripts/check-render.mjs` additionally renders with @napi-rs/canvas from the Codex primary runtime; this is a render check, not a browser QA claim. Browser UI and WebMCP interaction tests were unavailable for this static project's managed preview environment.

The example data is fictional design data, not verified fixtures. This project does not retrieve schedules. Asset sources and licenses are in `dist/assets/`.
