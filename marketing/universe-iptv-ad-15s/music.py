"""Original background music for the Universe IPTV ad, synthesized from scratch (no samples,
no third-party audio -> no rights issues). 120 BPM so scene cuts at 3.0 / 6.0 / 10.0 s land on beats.
Usage: python3 music.py out.wav [duration_s]
"""
import sys
import wave
import numpy as np

SR = 48000
BPM = 120
BEAT = 60 / BPM
DUR = float(sys.argv[2]) if len(sys.argv) > 2 else 15.0
N = int(SR * DUR)
t = np.arange(N) / SR
rng = np.random.default_rng(7)


def hz(m):
    return 440.0 * 2 ** ((m - 69) / 12)


def env_adsr(n, a, r):
    e = np.ones(n)
    na, nr = int(a * SR), int(r * SR)
    e[:na] = np.linspace(0, 1, na) if na else 1
    if nr:
        e[-nr:] *= np.linspace(1, 0, nr)
    return e


def pad_voice(freq, n, detune):
    tt = np.arange(n) / SR
    out = np.zeros(n)
    for d in (-detune, 0, detune):
        f = freq * 2 ** (d / 1200)
        for k in range(1, 7):
            out += np.sin(2 * np.pi * f * k * tt + k) / k ** 1.6
    return out / 3


def lowpass(x, cutoff):
    a = np.exp(-2 * np.pi * cutoff / SR)
    y = np.empty_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc = (1 - a) * v + a * acc
        y[i] = acc
    return y


def declick(n, ms=5):
    e = np.ones(n)
    k = min(int(ms / 1000 * SR), n // 2)
    e[:k] = np.linspace(0, 1, k)
    e[-k:] *= np.linspace(1, 0, k)
    return e


L = np.zeros(N)
R = np.zeros(N)

# Chords (MIDI notes) per segment: D minor, cinematic, resolving to F major on the CTA.
chords = [
    (0.0, 2.0, [50, 57, 62, 65]),    # Dm
    (2.0, 4.0, [46, 58, 62, 65]),    # Bb
    (4.0, 6.0, [53, 57, 60, 65]),    # F
    (6.0, 8.0, [48, 55, 60, 64]),    # C
    (8.0, 10.0, [50, 57, 62, 65]),   # Dm
    (10.0, 12.0, [53, 60, 65, 69]),  # F (CTA)
    (12.0, 13.0, [52, 60, 64, 67]),  # C/E
    (13.0, DUR, [53, 60, 65, 69]),   # F (hold)
]
for s, e, notes in chords:
    i0, i1 = int(s * SR), int(e * SR)
    n = i1 - i0
    for j, m in enumerate(notes):
        v = pad_voice(hz(m), n, 7) * env_adsr(n, 0.35, 0.4) * 0.11
        pan = 0.3 + 0.4 * (j / (len(notes) - 1))
        L[i0:i1] += v * (1 - pan)
        R[i0:i1] += v * pan

# Sub-bass pulse on 8th notes (root of each chord), enters at 3.0 s, drops out at 9.5 s for the riser.
for s, e, notes in chords:
    root = hz(notes[0] - 12)
    k = s
    while k < e - 1e-6:
        if 3.0 <= k < 9.5 or k >= 10.0:
            n = int(BEAT / 2 * SR)
            i0 = int(k * SR)
            n = min(n, N - i0)
            tt = np.arange(n) / SR
            v = np.sin(2 * np.pi * root * tt) * np.exp(-tt * 9) * 0.28 * declick(n)
            if k >= 10.0:
                v *= 0.6
            L[i0:i0 + n] += v
            R[i0:i0 + n] += v
        k += BEAT / 2

# Soft kick on beats 3.0-9.5 s and 10.0-13.0 s.
for b in np.arange(3.0, 13.0, BEAT):
    if 9.5 <= b < 10.0:
        continue
    i0 = int(b * SR)
    n = int(0.25 * SR)
    tt = np.arange(n) / SR
    f = 50 + 90 * np.exp(-tt * 30)
    v = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-tt * 14) * 0.45 * declick(n)
    L[i0:i0 + n] += v
    R[i0:i0 + n] += v

# Off-beat hats 6.0-9.5 s (scene 3 energy).
for b in np.arange(6.0 + BEAT / 2, 9.5, BEAT):
    i0 = int(b * SR)
    n = int(0.06 * SR)
    noise = rng.standard_normal(n)
    hp = noise - lowpass(noise, 6000)
    v = hp * np.exp(-np.arange(n) / SR * 60) * 0.035 * declick(n, 1)
    L[i0:i0 + n] += v * 0.8
    R[i0:i0 + n] += v

# Riser 8.5-10.0 s into the CTA, then a low impact exactly on the CTA cut (10.0 s).
i0, i1 = int(8.5 * SR), int(10.0 * SR)
n = i1 - i0
noise = rng.standard_normal(n)
ramp = np.linspace(0, 1, n) ** 2
v = lowpass(noise - lowpass(noise, 300), 2500) * ramp ** 2 * 0.03
L[i0:i1] += v
R[i0:i1] += v
i0 = int(10.0 * SR)
n = int(1.6 * SR)
tt = np.arange(n) / SR
v = np.sin(2 * np.pi * (40 + 30 * np.exp(-tt * 8)) * tt) * np.exp(-tt * 3) * 0.5 * declick(n)
L[i0:i0 + n] += v
R[i0:i0 + n] += v

# Simple stereo reverb: convolve with decaying noise impulse responses.
ir_n = int(1.8 * SR)
decay = np.exp(-np.arange(ir_n) / SR * 3.2)
irL = rng.standard_normal(ir_n) * decay
irR = rng.standard_normal(ir_n) * decay
irL /= np.abs(irL).sum() / 6
irR /= np.abs(irR).sum() / 6


def conv(x, ir):
    m = len(x) + len(ir) - 1
    size = 1 << (m - 1).bit_length()
    y = np.fft.irfft(np.fft.rfft(x, size) * np.fft.rfft(ir, size), size)[:len(x)]
    return y


wet = 0.22
L, R = L + wet * conv(L, irL), R + wet * conv(R, irR)

# Fade in 0.3 s, fade out over the last 1.2 s.
fade = np.ones(N)
fade[:int(0.3 * SR)] = np.linspace(0, 1, int(0.3 * SR))
fade[-int(1.2 * SR):] *= np.linspace(1, 0, int(1.2 * SR)) ** 1.5
L *= fade
R *= fade

peak = max(np.abs(L).max(), np.abs(R).max())
L, R = L / peak * 0.7, R / peak * 0.7
data = (np.stack([L, R], axis=1) * 32767).astype(np.int16)
with wave.open(sys.argv[1], 'wb') as w:
    w.setnchannels(2)
    w.setsampwidth(2)
    w.setframerate(SR)
    w.writeframes(data.tobytes())
print('wrote', sys.argv[1], DUR, 's')
