"""Synthesized 15 s soundtrack for the Universe IPTV promo, synced to promo.html's timeline."""
import wave
import numpy as np

SR = 48000
DUR = 15.0
N = int(SR * DUR)
t = np.arange(N) / SR
rng = np.random.default_rng(7)
L = np.zeros(N)
R = np.zeros(N)

BEAT = 0.7667 / 2          # kinetic words land every 2 beats (~156.5 BPM)
GRID0 = 2.3


def onepole_lp(x, fc):
    """One-pole low-pass; fc may be a scalar or per-sample array."""
    fc = np.broadcast_to(np.asarray(fc, dtype=float), x.shape)
    a = np.exp(-2 * np.pi * fc / SR)
    y = np.empty_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc = (1 - a[i]) * x[i] + a[i] * acc
        y[i] = acc
    return y


def add(sig, start, gain=1.0, pan=0.0):
    i = int(start * SR)
    if i >= N:
        return
    sig = sig[: N - i]
    L[i:i + len(sig)] += sig * gain * np.sqrt(0.5 * (1 - pan))
    R[i:i + len(sig)] += sig * gain * np.sqrt(0.5 * (1 + pan))


def env(n, a, d):
    x = np.arange(n) / SR
    return np.minimum(1, x / max(a, 1e-4)) * np.exp(-x / d)


def kick(d=0.45):
    n = int(SR * d); x = np.arange(n) / SR
    f = 45 + 140 * np.exp(-x * 30)
    ph = 2 * np.pi * np.cumsum(f) / SR
    return np.tanh(2.2 * np.sin(ph) * np.exp(-x * 7)) + 0.3 * rng.standard_normal(n) * np.exp(-x * 200)


def boom(d=3.0, f0=38):
    n = int(SR * d); x = np.arange(n) / SR
    f = f0 + 90 * np.exp(-x * 12)
    ph = 2 * np.pi * np.cumsum(f) / SR
    body = np.sin(ph) * np.exp(-x * 1.4)
    crack = onepole_lp(rng.standard_normal(n), 2500) * np.exp(-x * 9) * 1.8
    return np.tanh(1.6 * (body + crack))


def clap():
    n = int(SR * 0.3); x = np.arange(n) / SR
    nz = rng.standard_normal(n)
    nz = nz - onepole_lp(nz, 900)
    e = sum(np.exp(-np.maximum(x - o, 0) * 60) * (x >= o) for o in (0, 0.011, 0.022)) + np.exp(-x * 14) * 0.6
    return nz * e * 0.5


def hat():
    n = int(SR * 0.08); x = np.arange(n) / SR
    nz = rng.standard_normal(n)
    return (nz - onepole_lp(nz, 7000)) * np.exp(-x * 70) * 0.35


def whoosh(d, rise=True, f_lo=300, f_hi=6000):
    n = int(SR * d); u = np.linspace(0, 1, n)
    fc = f_lo * (f_hi / f_lo) ** (u if rise else 1 - u)
    nz = rng.standard_normal(n)
    y = onepole_lp(nz, fc) - onepole_lp(nz, fc * 0.25)
    shape = np.sin(np.pi * u) ** 1.5 if rise else np.sin(np.pi * np.minimum(1, u * 1.6)) ** 1.2 * (1 - u)
    return y * shape * 2.2


def riser(d):
    n = int(SR * d); u = np.linspace(0, 1, n)
    f = 110 * 2 ** (u * 3)
    ph = 2 * np.pi * np.cumsum(f) / SR
    tone = (np.sin(ph) + 0.5 * np.sin(ph * 2.01) + 0.3 * np.sin(ph * 3.02)) * u ** 2
    nz = rng.standard_normal(n)
    air = (nz - onepole_lp(nz, 1500 + 8000 * u)) * u ** 2.5
    return tone * 0.35 + air * 0.9


def saw(f, n, detune=0.0):
    x = np.arange(n) / SR
    ph = (f * (1 + detune) * x) % 1
    return 2 * ph - 1


def note(m):
    return 440 * 2 ** ((m - 69) / 12)


# --- pad: A minor progression, low-passed detuned saws across the whole spot
chords = [[57, 60, 64], [53, 57, 60], [48, 55, 60], [55, 59, 62]]
pad = np.zeros(N)
seg_len = BEAT * 8
for k in range(int(DUR / seg_len) + 1):
    s0 = int(k * seg_len * SR); s1 = min(N, int((k + 1) * seg_len * SR) + int(0.2 * SR))
    if s0 >= N:
        break
    n = s1 - s0
    ch = chords[k % 4]
    blk = sum(saw(note(m), n, d) for m in ch for d in (-0.004, 0.004))
    fade = np.minimum(1, np.arange(n) / (0.15 * SR)) * np.minimum(1, (n - np.arange(n)) / (0.2 * SR))
    pad[s0:s1] += blk * fade
cut = 500 + 1800 * np.clip((t - 2.3) / 2, 0, 1) * (t < 11.5) + 2500 * np.clip((t - 11.55) / 0.5, 0, 1) * np.exp(-np.maximum(t - 12.5, 0) * 0.5)
pad = onepole_lp(onepole_lp(pad, cut), cut)
pad_gain = 0.05 + 0.03 * (t > 2.3) + 0.06 * (t > 11.55)
L += pad * pad_gain * (1 + 0.2 * np.sin(t * 1.3))
R += pad * pad_gain * (1 - 0.2 * np.sin(t * 1.3))

# --- intro: warp wash that decelerates, letters land, riser into the first hit
add(whoosh(1.3, rise=False, f_lo=200, f_hi=9000), 0.0, 0.9)
add(boom(1.5, 44), 0.25, 0.45)
for i in range(8):
    add(clap() * 0.6, 1.0 + i * 0.07, 0.35, pan=-0.7 + i * 0.2)
add(riser(0.6), 1.7, 0.9)

# --- beat section 2.3 → 10.4
bass_roots = [45, 41, 36, 43]
b = 0
while GRID0 + b * BEAT < 10.4:
    s = GRID0 + b * BEAT
    add(kick(), s, 0.9)
    if b % 2 == 1:
        add(clap(), s, 0.55)
    add(hat(), s + BEAT / 2, 0.5, pan=0.3)
    add(hat(), s + BEAT / 4, 0.25, pan=-0.3)
    add(hat(), s + 3 * BEAT / 4, 0.25, pan=-0.3)
    # sub bass on off-beats
    root = bass_roots[(b // 8) % 4]
    n = int(SR * BEAT * 0.45); x = np.arange(n) / SR
    add(np.tanh(1.5 * np.sin(2 * np.pi * note(root) * x)) * env(n, 0.005, 0.12), s + BEAT / 2, 0.45)
    b += 1

# arpeggio in the holographic wall section
arp = [69, 72, 76, 79, 81, 79, 76, 72]
step = BEAT / 4
k = 0
while 4.6 + k * step < 10.4:
    s = 4.6 + k * step
    chord_shift = [0, -4, -9, -2][int((s - GRID0) / (BEAT * 8)) % 4]
    f = note(arp[k % 8] + chord_shift)
    n = int(SR * 0.22)
    tone = saw(f, n) + saw(f, n, 0.006)
    tone = onepole_lp(tone, 1800 + 1500 * np.exp(-np.arange(n) / SR * 25)) * env(n, 0.002, 0.07)
    add(tone, s, 0.13, pan=0.5 * np.sin(k * 0.9))
    k += 1

# word slams + camera whips
for s in (2.3, 3.067, 3.833):
    add(boom(1.2, 50), s, 0.5)
    add(whoosh(0.35), s - 0.3, 0.5, pan=0.4)
add(boom(1.6, 42), 4.6, 0.6)
add(whoosh(0.9, rise=False, f_lo=250, f_hi=7000), 4.6, 0.8)
for s, p in ((5.45, -0.6), (6.5, 0.6), (7.72, -0.6), (8.95, 0.5)):
    add(whoosh(0.45), s, 0.9, pan=p)

# --- wormhole riser and the logo drop
add(riser(1.75), 9.8, 1.2)
add(whoosh(1.0, rise=True, f_lo=400, f_hi=12000), 10.55, 0.9)
add(boom(3.4, 34), 11.55, 1.1)
add(kick(0.8), 11.55, 1.0)
# shimmer on the logo reveal and tagline
for i, m in enumerate([81, 84, 88, 93, 96]):
    n = int(SR * 2.8); x = np.arange(n) / SR
    tone = np.sin(2 * np.pi * note(m) * x) + 0.3 * np.sin(2 * np.pi * note(m) * 2.003 * x)
    add(tone * env(n, 0.01, 0.9), 12.25 + i * 0.06, 0.07, pan=-0.6 + i * 0.3)
add(boom(1.9, 55) * 0.5, 13.05, 0.45)
for i, m in enumerate([69, 76, 81]):
    n = int(SR * 1.9); x = np.arange(n) / SR
    add(np.sin(2 * np.pi * note(m) * x) * env(n, 0.02, 0.7), 13.05 + i * 0.05, 0.08, pan=-0.3 + i * 0.3)

# --- master: fade in/out, glue, normalize
master = np.minimum(1, t / 0.05) * np.clip((15.0 - t) / 0.35, 0, 1)
out = np.stack([L, R], axis=1) * master[:, None]
out = np.tanh(out * 1.3) / np.tanh(1.3)
out /= np.max(np.abs(out)) + 1e-9
out *= 0.89
pcm = (out * 32767).astype(np.int16)
with wave.open("soundtrack.wav", "wb") as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR)
    w.writeframes(pcm.tobytes())
print("wrote soundtrack.wav", pcm.shape)
