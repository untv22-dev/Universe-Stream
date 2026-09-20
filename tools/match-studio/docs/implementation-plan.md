# Universe Poster Studio Implementation Plan

Goal: mobile Arabic editor producing notebook match posters matching 579730.jpg.
Architecture: static client app, local assets, shared Canvas preview/export, deterministic import pipeline. No account, database or server upload. User approved execution directly.
Stack: ES modules, Canvas 2D, SheetJS 0.20.3, embedded Cairo and Aref Ruqaa fonts.
Spec: 2026-09-19-universe-poster-design.md from the approved conversation.

## Global constraints
2048×3072 PNG, 2:3. Max 300 rows and 10 MB. Preserve user text. Unknown crests produce a neutral mark plus warning. No beIN branding. Exact URL http://universe-player.com. Process imports locally.

## Review focus
Quoted CSV cells; Excel fractional time; invalid or empty rows; unknown names; long content causing pagination. Test these through pure parsing/layout modules.

## Tasks
- [ ] Create notebook background from supplied reference, preserving header and decorative elements while clearing mutable content. Store at dist/assets/notebook.png.
- [ ] Vendor fonts, SheetJS and identifiable club crests; record sources. Implement exact normalized alias resolver in dist/catalog.mjs.
- [ ] Implement parseDelimited(text), mapRows(rows,mapping), validate(matches), paginate(matches) in dist/core.mjs. Verify with node --test tests/core.test.mjs.
- [ ] Implement renderer in dist/poster.mjs using asynchronous asset preload and same measured layout for preview and export. No per-row network calls.
- [ ] Implement RTL mobile editor in dist/index.html, dist/style.css and dist/app.mjs: paste/file import, mapping, sheet choice, editable rows, date, page selector, PNG download and image fallback.
- [ ] Check syntax, reference assets, parser fixtures, pagination and export. Register supported WebMCP import action with input validation.
- [ ] Commit and push exact source, package static assets, save version, deploy privately, verify terminal deployment success.
