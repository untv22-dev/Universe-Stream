#!/usr/bin/env bash
# Renders promo-ar.html frame-by-frame, synthesizes the soundtrack, and encodes universe-iptv-ar-promo.mp4.
# Needs: node with playwright (Chromium), python3 + numpy, and ffmpeg with libx264 (set FFMPEG=... if not on PATH).
set -euo pipefail
cd "$(dirname "$0")"
FFMPEG="${FFMPEG:-ffmpeg}"
rm -rf frames
node render.mjs frames 0 450 1 "${WORKERS:-3}"
python3 soundtrack_ar.py
"$FFMPEG" -y -framerate 30 -i frames/f%04d.png -i soundtrack_ar.wav \
  -c:v libx264 -preset slow -crf 19 -maxrate 14M -bufsize 28M -pix_fmt yuv420p -profile:v high \
  -c:a aac -b:a 256k -shortest -movflags +faststart universe-iptv-ar-promo.mp4
echo "wrote universe-iptv-ar-promo.mp4"
