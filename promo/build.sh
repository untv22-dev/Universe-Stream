#!/usr/bin/env bash
# Renders promo.html frame-by-frame, synthesizes the soundtrack, and encodes universe-iptv-promo.mp4.
# Needs: node + npm install (three, playwright with a Chromium), python3 + numpy, and ffmpeg with libx264
# (set FFMPEG=/path/to/ffmpeg if it is not on PATH; `pip install imageio-ffmpeg` ships one).
set -euo pipefail
cd "$(dirname "$0")"
FFMPEG="${FFMPEG:-ffmpeg}"
WORKERS="${WORKERS:-3}"
rm -rf frames
node render.mjs frames 0 450 1 "$WORKERS"
python3 soundtrack.py
"$FFMPEG" -y -framerate 30 -i frames/f%04d.png -i soundtrack.wav \
  -c:v libx264 -preset slow -crf 16 -pix_fmt yuv420p -profile:v high -tune film \
  -c:a aac -b:a 256k -shortest -movflags +faststart universe-iptv-promo.mp4
echo "wrote universe-iptv-promo.mp4"
