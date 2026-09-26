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
