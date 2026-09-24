#!/usr/bin/env bash
# Rebuilds the ad from source. Needs: ffmpeg, node + playwright (Chromium).
set -euo pipefail
cd "$(dirname "$0")"
LOGO=../../app/src/main/res/drawable/universe_stream_logo.png
# Remove the logo's baked-in black tile (pixels are otherwise untouched; aspect ratio preserved).
ffmpeg -loglevel error -y -i "$LOGO" -vf "format=rgba,colorkey=0x040008:similarity=0.10:blend=0.08" logo_key.png
FRAMES=$(mktemp -d)
NODE_PATH="${NODE_PATH:-$(npm root -g)}" node render.js "$FRAMES"
mkdir -p out
ffmpeg -loglevel error -y -framerate 30 -i "$FRAMES/f%04d.png" -f lavfi -i anullsrc=r=48000:cl=stereo \
  -map 0:v -map 1:a -c:v libx264 -preset slow -crf 16 -profile:v high -pix_fmt yuv420p -r 30 \
  -c:a aac -b:a 128k -ar 48000 -ac 2 -t 15 -movflags +faststart out/UniverseIPTV_Ad_15s_1080x1920_master.mp4
ffmpeg -loglevel error -y -i out/UniverseIPTV_Ad_15s_1080x1920_master.mp4 -vf scale=720:1280:flags=lanczos \
  -c:v libx264 -preset slow -crf 24 -profile:v high -pix_fmt yuv420p -c:a aac -b:a 96k -movflags +faststart \
  out/UniverseIPTV_Ad_15s_720x1280_web.mp4
ffmpeg -loglevel error -y -i "$FRAMES/f0170.png" -q:v 2 out/UniverseIPTV_Ad_cover_1080x1920.jpg
rm -rf "$FRAMES"
