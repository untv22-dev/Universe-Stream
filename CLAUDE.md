# CLAUDE.md

Guidance for Claude Code (and other AI assistants) working in this repository.

## What this project is

**UniverseStream** is a TV-first IPTV client for Android TV, written in Kotlin with
Jetpack Compose (TV + Material3), Room, Hilt, WorkManager, and Media3/ExoPlayer.
Phones and tablets are supported, but Android TV / D-pad is the primary UX target.

It plays content from user-supplied providers — `Xtream Codes`, `Stalker Portal`,
`Jellyfin`, and `M3U` playlists — with Live TV, Movies, Series, EPG/XMLTV guide,
DVR recording, offline VOD downloads, Cast, multi-view, parental controls, and a
companion-APK plugin API. It is a *client*, never a content source.

Application ID: `com.universestream.app` · package root: `com.universestream.*`

## Toolchain and versions

| Thing | Value |
|---|---|
| Gradle build files | Kotlin DSL (`*.gradle.kts`) |
| Version catalog | `gradle/libs.versions.toml` — **all** dependency versions live here |
| AGP / Kotlin / KSP | 8.10.1 / 2.2.0 / 2.2.0-2.0.2 |
| JDK | **17** (source, target, and jvmToolchain) |
| compileSdk / targetSdk / minSdk | 36 / 36 / **25** |
| Media3 | 1.9.2 (plus a bundled FFmpeg decoder AAR, see below) |
| Room | 2.8.4, schema version **64**, `exportSchema = true` |
| DI | Hilt 2.56.2 via KSP |
| Coverage | Kover (`ci` variant) aggregated at the root project |

## Module layout

Four Gradle modules, strict one-way dependencies:

```
:app  ──▶ :domain, :data, :player       Android application
:data ──▶ :domain                        Android library
:player ──▶ :domain                      Android library
:domain                                  pure Kotlin/JVM library (no Android deps)
```

- **`:domain`** (`com.universestream.domain`, ~107 files) — pure JVM. Models
  (`model/`), repository *interfaces* (`repository/`), manager *interfaces*
  (`manager/`), the `IptvProvider` contract (`provider/`), and use cases
  (`usecase/`). Only depends on `javax.inject` and coroutines-core. **Never add an
  Android dependency here** — it would break the JVM test path and the layering.
- **`:data`** (`com.universestream.data`, ~182 files) — Room database + DAOs
  (`local/`), provider clients (`remote/xtream`, `remote/stalker`,
  `remote/jellyfin`, `remote/http`), parsers (`parser/M3uParser`,
  `parser/XmltvParser`), EPG resolution (`epg/`), repository implementations
  (`repository/*Impl`), DataStore preferences (`preferences/`), credential
  encryption (`security/`), sync/workers (`sync/`), recording + download +
  backup managers (`manager/`).
- **`:player`** (`com.universestream.player`, ~81 files) — playback abstraction
  (`PlayerEngine`) and the Media3 implementation (`Media3PlayerEngine`), plus
  stream-type detection, retry/recovery policies, timeshift, track selection,
  audio compatibility, and player stats under `playback/`, `timeshift/`,
  `tracks/`, `audio/`, `stats/`.
- **`:app`** (`com.universestream.app`, ~323 files) — Compose UI, navigation, Hilt
  modules (`di/`), and Android/TV integrations (`tv/` Watch Next + launcher,
  `tvinput/` TV Input Framework, `cast/`, `plugins/`, `update/`, `service/`,
  `backup/`, `pairing/`, `diagnostics/`, `device/`).

Docs live in `docs/` (`PLUGIN_API.md`, `CHANGELOG.md`, `DEV_SEEDING.md`,
`FFMPEG.md`, `TRANSLATION_SERVICE_OPERATIONS.md`, …). `tools/` holds ancillary
scripts and a standalone live-translation service (Python/Docker).

## Build and test commands

Requires a local Android SDK (`ANDROID_HOME` / `local.properties` with `sdk.dir`)
and JDK 17.

```bash
./gradlew assembleDebug                     # debug APK (applicationId .debug)
./gradlew :app:assembleBeta                 # beta APK (.beta, unminified)
./gradlew :app:assembleRelease              # release APK (minify + shrink + sign)

./gradlew testDebugUnitTest                 # all unit tests (what CI runs)
./gradlew :data:testDebugUnitTest           # one module
./gradlew :domain:test                      # :domain is a JVM module — plain `test`
./gradlew :app:testDebugUnitTest --tests "*SyncManagerTest*"

./gradlew koverXmlReportCi koverHtmlReportCi   # coverage → build/reports/kover/
./gradlew connectedAndroidTest              # instrumentation tests (device/emulator)
```

**Build resources are deliberately constrained** in `gradle.properties`:
`org.gradle.parallel=false`, `org.gradle.workers.max=1`, `-Xmx2048m`. Kotlin
compilation of `:data` and `:app` is heavy; low-memory environments have died with
`GC overhead limit exceeded`. Prefer building a single module, and do not "fix"
slow builds by raising parallelism without checking memory headroom.

There is no ktlint/detekt/spotless configuration. Match surrounding style
(`kotlin.code.style=official`); do not introduce a formatter.

## Conventions that matter

### Architecture

- Interfaces in `:domain`, implementations in `:data` — a repository is
  `FooRepository` (domain) + `FooRepositoryImpl` (data), bound in
  `app/di/RepositoryModule.kt`. Managers follow the same split
  (`RecordingManager` / `RecordingManagerImpl`).
- Fallible operations return `com.universestream.domain.model.Result`
  (`Success` / `Error(message, exception?)` / `Loading`) — **not** `kotlin.Result`
  and not thrown exceptions across layer boundaries.
- Provider backends implement `domain/provider/IptvProvider`
  (`XtreamProvider`, `StalkerProvider`, …). New provider types go through this
  interface plus the sync pipeline, never straight into UI.
- UI is Compose-only: `XxxScreen.kt` + `XxxViewModel.kt` under
  `app/ui/screens/<feature>/`, `@HiltViewModel` with constructor injection,
  `MutableStateFlow` ⇢ `asStateFlow()` UI state. Shared widgets live in
  `app/ui/components/`, design tokens in `app/ui/design/` (`AppColors`,
  `AppSpacing`, `AppTypography`, `AppShapes`, `AppMotion`, `FocusSpec`) and
  `app/ui/theme/`. Use the tokens; don't hardcode dp/colors.
- Navigation is a single `NavHost` in `app/navigation/AppNavigation.kt`; every
  route string is a constant in `object Routes`. Add routes there, not inline.
- Compose files are large by design and split by concern (e.g. Settings is ~50
  `Settings*.kt` files). Follow the existing split rather than growing one file.

### TV-first UI

- `app/device/DeviceSupport.kt` (`isTelevisionDevice()`,
  `rememberIsTelevisionDevice()`, `isFireTvDevice()`) gates form-factor behavior.
  Mobile/compact variants are separate composables (`MobileLiveTvContent.kt`,
  `MobileProvidersContent.kt`, `MobileSettingsCategoryBar.kt`).
- **Do not change TV behavior when fixing a phone/tablet issue.** The established
  pattern is a compact branch alongside an untouched TV branch — recent history is
  full of commits that explicitly preserve the TV path.
- D-pad focus is a first-class concern: `ui/design/FocusHelpers.kt`, `FocusSpec.kt`,
  `ui/interaction/TvComponents.kt`, `MouseSupport.kt`. Touch fixes must not consume
  events the D-pad path depends on.

### Localization

- 25 translated locale packs ship alongside English (`app/src/main/res/values-*/`).
  The source of truth is `app/src/main/res/values/strings.xml` (1730 `<string>`
  keys plus 5 `<plurals>`).
- **No hardcoded user-facing strings.** Every visible label, filter, count, error,
  or dialog goes through a string resource; add the key to `values/strings.xml`
  and, where you can, to the translated locales. Several recent commits exist
  purely to fix leaked literals.
- `app/localization/AppLocaleSupport.kt` handles in-app locale selection.

**Every locale has TWO resource files — read both.** Each `values-<loc>/` contains
`strings.xml` *and* `strings_missing.xml`, and Android merges every XML file in
the directory into one resource table. Two consequences, both of which have
already bitten:

- Adding a key to `strings.xml` when it already exists in `strings_missing.xml`
  is a **build failure**, not a warning: `MergeResources` aborts with
  `Error: Duplicate resources`. Always check both files before adding a key.
- Any coverage audit that reads only `strings.xml` is wrong. Real coverage is
  the union of the two files: 1628 of 1730 keys in 24 locales (**102 genuinely
  missing** each) and 1666 in `ar` (**64 missing**). A single-file comparison
  reports a far larger, and false, gap.

**`strings_missing.xml` is mostly untranslated English.** It appears to have been
generated to satisfy the `MissingTranslation` lint check rather than to translate
anything. In 24 of the 25 locales every entry is byte-identical to
`values/strings.xml` (184 entries each; `es` 183, `fr` 148). Arabic is the sole
exception — 186 of its 189 entries are real Arabic. So a locale can look complete
to lint while ~184 keys render in English, and nothing flags it. When judging
translation coverage, compare *values* against the English source, not just key
presence.

### Never leak provider secrets or raw errors

- `data/sync/SyncErrorSanitizer` maps provider/network exceptions to short user
  messages; `player/playback/PlaybackLogSanitizer` strips credentials and opaque
  path segments from URLs before logging.
- Credentials are encrypted at rest (`data/security/CredentialCrypto`,
  `androidx.security:security-crypto`) and stored via DataStore.
- Never log or surface a raw stream URL, portal token, MAC, username, or password.
  Never put real provider credentials in tracked files.

### Room database

- `data/local/UniverseStreamDatabase.kt` — **version 64**, ~40 entities, 63
  `MIGRATION_*` constants, `exportSchema = true` with JSON in `data/schemas/`.
- Any schema change requires: bump `version`, add an explicit `MIGRATION_n_n+1`,
  wire it into the migration list, **commit the generated
  `data/schemas/.../<version>.json`**, and cover it in
  `data/src/androidTest/.../UniverseStreamDatabaseMigrationTest.kt`.
- FTS entities (`ChannelFtsEntity`, `MovieFtsEntity`, `SeriesFtsEntity`) back
  search; the catalogs are large, so DAO queries in `local/dao/Daos.kt` and
  `CatalogSyncDao.kt` are written for big playlists — keep them paged/limited.

### Sync

`data/sync/` is the heaviest subsystem (`SyncManager.kt` is ~7k lines, split across
`SyncManager*Support/Strategy/Fetcher/Stager` helpers). Work is driven by
WorkManager (`SyncWorker`, `ProviderSyncWorker`, `BackgroundEpgSyncWorker`,
`XtreamIndexWorker`, `StalkerIndexWorker`) with staging tables, catalog size
limits, and adaptive policies. Prefer extending an existing `SyncManager*`
helper file over adding logic to `SyncManager` itself.

### Bundled FFmpeg decoder

`player/libs/media3-decoder-ffmpeg-1.9.2.aar` is checked in and consumed by both
`:player` and `:app` (`implementation(files("../player/libs/…"))`). `:player`'s
`preBuild` runs `verifyLocalFfmpegArtifact`, which asserts the manifest's
`media3Version`, arm64-v8a/armeabi-v7a `.so` payloads, required classes, and the
`mp2` / `audio/mpeg-L2` decoder. If you change the Media3 version you must rebuild
or replace that AAR and its `.properties` manifest — see `docs/FFMPEG.md` and
`docs/FFMPEG-LGPL-NOTICE.md`. Only `arm64-v8a` and `armeabi-v7a` ABIs are shipped.

### Build types and signing

- `debug` — `.debug` suffix; reads optional dev-seeding values from
  `local.properties` into `BuildConfig` (`XTREAM_DEV_*`, `M3U_DEV_*`) so
  onboarding can be skipped. Release inherits empty defaults, so a release APK can
  never ship a contributor's credentials. See `local.properties.example` and
  `docs/DEV_SEEDING.md`.
- `beta` — derived from `release`, `.beta` suffix, `APP_UPDATE_CHANNEL="beta"`,
  minify/shrink off for faster CI distribution.
- `release` — minify + resource shrink + `app/proguard-rules.pro`.
- Signing is optional and file-driven: `keystore.properties` at the repo root.
  It, `keystore/`, `*.jks`, and `local.properties` are gitignored — **never commit
  them**. When present, the build also computes
  `BuildConfig.OFFICIAL_SIGNING_CERT_SHA256` from the keystore.

## Releases and CI

Workflows in `.github/workflows/`:

- **`release.yml`** ("Android CI and Release") — `workflow_dispatch` only. Runs
  `testDebugUnitTest` + Kover, builds and `apksigner`-verifies the release APK,
  then publishes a GitHub Release. If signing secrets are absent it generates an
  ephemeral CI key so the APK stays installable.
- **`android-release.yml`** — fast CI on PRs to `main`, `v*` tags, and manual runs.
- **`beta.yml`** — beta APK on pushes to `develop`.

Release mechanics to respect:

- `versionCode` / `versionName` in `app/build.gradle.kts` are the single source of
  truth; CI `sed`s them out and derives the tag `v<versionName>` and asset name.
- Release notes are extracted from `docs/CHANGELOG.md` by matching a
  `## [<versionName>] - <date>` heading. **A version bump without a matching
  changelog heading publishes an empty release body** — always add the heading in
  the same change that bumps `versionName`.
- Keep changelog entries in the existing Added / Fixed / Changed style.

## Working agreements

- **Branching:** develop on the branch you were assigned; `main` is the default
  branch. Do not push elsewhere without explicit permission.
- **Commits:** Conventional-Commit style is used throughout —
  `fix(mobile): …`, `feat: …`, `perf: …`, `chore(release): …`, `test: …`,
  `ui: …`, `fix(ci): …`. Match it.
- **Scope discipline:** this repo's history is many small, surgical commits. Fix
  the reported problem; don't opportunistically refactor adjacent code.
- `AGENTS.md` and `.github/copilot-instructions.md` carry additional
  assistant-facing rules (graphify knowledge graph, emulator orientation, live-TV
  validation). Read `AGENTS.md` before emulator or playback debugging — its
  validation requirements are stricter than a normal smoke test and are summarized
  below.

## Validating playback changes (from AGENTS.md)

Playback bugs are **not** validated by "it builds", "it installs", "it launched",
or a single screenshot.

- **Emulator orientation:** align the device frame and app rotation first. Known
  good is landscape frame with the app upright at `ROTATION_270`:
  ```bash
  adb shell cmd window set-ignore-orientation-request true
  adb shell cmd window user-rotation lock 3
  ```
  `cur=2340x1080 app=2340x1080` alone is *not* proof of correct orientation.
- **Live TV stuckness:** capture screenshots on a 2-second cadence for at least
  ~90s (45 frames), and prefer ~2 minutes (61 frames) when re-validating a fix that
  previously failed around the one-minute mark. Confirm frames keep changing via
  unique SHA-256 counts, confirm the media session is still `PLAYING` with
  `error=null`, and check logcat for `fatal-error`, stuck-player timeouts, and
  unintended MPEG-TS fallback.
- Validate **more than one channel** when the report says "live TV" generally, and
  record channel names, frame count, interval, unique-hash count, media-session
  state, and log findings in the final report.

## Repo hygiene notes

- Root-level `gradle_*.log` / `final_narrow_*.log` files are captured build/test
  output. Eleven of them are **tracked** (committed by accident in `051ef3d`
  alongside an unrelated UI change) even though nine match `.gitignore` patterns —
  ignore rules do not apply to already-tracked paths. They are UTF-16LE
  PowerShell captures and five embed a local developer path. Don't add new ones
  to commits, and don't treat their presence as precedent.
- `MOBILE_UI_FIX_REPORT.md` and `UNIVERSE_STREAM_REBRAND_REPORT.md` are historical
  Arabic-language work reports, not specifications.
- `.claude/`, `graphify-out/`, `translations/`, and `docs/*-plan.md` /
  `docs/*-handoff.md` are gitignored scratch space.
