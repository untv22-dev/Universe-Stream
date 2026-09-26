# Universe IPTV — 15 s promo

`universe-iptv-promo.mp4` is a 15-second, 1920×1080, 30 fps motion-graphics spot with a
synthesized stereo soundtrack. Everything in it is procedural: no stock footage, no real
channel or provider content.

## Timeline

| Time | Shot |
|---|---|
| 0.0 – 2.3 s | Hyperspace warp, neon spiral lines, 3D kinetic "ENTER THE UNIVERSE" that the camera punches through |
| 2.3 – 4.6 s | Kinetic word slams: **LIVE SPORTS** · **BLOCKBUSTER MOVIES** · **WORLD CHANNELS** (4K UHD / 4K HDR taglines) |
| 4.6 – 10.5 s | Holographic wall of floating screens over a neon grid; fast zooms into live sports, a blockbuster movie, and international channels, then a pull-back reveal |
| 10.5 – 11.6 s | Wormhole transition with neon rings, white flash |
| 11.6 – 15.0 s | Logo: glowing planet, orbit rings, play glyph, **UNIVERSE IPTV**, and the call to action **STREAM WITHOUT LIMITS** |

## Files

- `promo.html` — the whole scene (three.js + bloom/chromatic-aberration post). Open it through
  any static server (e.g. `npx http-server .`) after `npm install` to watch it loop live in a browser.
  `?render` disables the live loop; `window.renderAt(seconds)` draws one deterministic frame.
- `render.mjs` — headless Chromium (Playwright) frame renderer: `node render.mjs <outDir> <from> <to> [step] [workers]`.
- `soundtrack.py` — numpy synth for the music bed, whooshes, hits, riser, and logo shimmer, timed to the cuts.
- `build.sh` — renders all 450 frames, builds the audio, and encodes the MP4 (H.264 + AAC).
- `fonts/` — Orbitron and Rajdhani (SIL Open Font License, from Google Fonts).

## Rebuilding

```bash
cd promo
npm install
pip install numpy imageio-ffmpeg        # imageio-ffmpeg only if you have no ffmpeg with libx264
FFMPEG=$(python3 -c "import imageio_ffmpeg as i; print(i.get_ffmpeg_exe())") ./build.sh
```

Rendering uses software WebGL (SwiftShader) and takes roughly 10–15 minutes on 4 cores.

---

# Arabic promo (`ar/`)

`ar/universe-iptv-ar-promo.mp4` is a second, stylistically different 15-second spot in Arabic:
flat, playful 2D motion design (Canvas 2D) with sub-frame motion blur, RTL-first motion (everything
enters and wipes right-to-left), and a bouncy 120 BPM synthesized track with sound effects on every beat.

| Time | Shot |
|---|---|
| 0.0 – 2.0 s | A ball bounces in with squash-and-stretch and morphs into a screen: «جاهز؟» |
| 2.0 – 4.1 s | Kinetic word slams on color wipes: «عالم / كامل / من الترفيه / بين إيديك», then a stripe transition |
| 4.1 – 7.0 s | «كل اللي بتحبه في مكان واحد»: four category cards spring in and a TV-remote focus ring hops across them |
| 7.0 – 9.5 s | «اتفرج في أي مكان»: phone → tablet → laptop → smart TV morph with feature chips, channel-zap static, dive into the screen |
| 9.5 – 11.5 s | Logo: planet, orbit ring, UNIVERSE IPTV wordmark, «بث بلا حدود» |
| 11.5 – 15.0 s | «اشترك الآن» with the WhatsApp number (+20 10 1741 1422, digits roll in) and www.universeiptv-ar.com (typed); a cursor taps WhatsApp |

Rebuild with `cd promo/ar && FFMPEG=... ./build.sh` (needs Playwright's Chromium, numpy, ffmpeg).
Fonts: Cairo, Lalezar, Orbitron (SIL Open Font License, from Google Fonts).
