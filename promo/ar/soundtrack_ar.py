"""Synthesized 15 s soundtrack for the Arabic Universe IPTV promo (promo-ar.html).

Upbeat 120 BPM pop groove in a major key, with sound effects placed on the animation's
events: ball bounces, pops, whooshes, card springs, focus ticks, device morphs,
the logo hit, digit rolls, typing, and the final cursor tap.
"""
import wave
import numpy as np

SR = 48000
DUR = 15.0
N = int(SR * DUR)
t = np.arange(N) / SR
rng = np.random.default_rng(3)
L = np.zeros(N)
R = np.zeros(N)
BEAT = 0.5


def lp(x, fc):
    fc = np.broadcast_to(np.asarray(fc, dtype=float), x.shape)
    a = np.exp(-2 * np.pi * fc / SR)
    y = np.empty_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc = (1 - a[i]) * x[i] + a[i] * acc
        y[i] = acc
    return y


def add(sig, start, gain=1.0, pan=0.0):
    i = int(round(start * SR))
    if i >= N or i < 0:
        return
    sig = sig[: N - i]
    L[i:i + len(sig)] += sig * gain * np.sqrt(0.5 * (1 - pan))
    R[i:i + len(sig)] += sig * gain * np.sqrt(0.5 * (1 + pan))


def env(n, a, d):
    x = np.arange(n) / SR
    return np.minimum(1, x / max(a, 1e-4)) * np.exp(-x / d)


def note(m):
    return 440 * 2 ** ((m - 69) / 12)


def kick():
    n = int(SR * 0.4); x = np.arange(n) / SR
    f = 50 + 130 * np.exp(-x * 35)
    return np.tanh(2.0 * np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-x * 8))


def clap():
    n = int(SR * 0.25); x = np.arange(n) / SR
    nz = rng.standard_normal(n); nz = nz - lp(nz, 1000)
    e = sum(np.exp(-np.maximum(x - o, 0) * 70) * (x >= o) for o in (0, 0.01, 0.02)) + 0.5 * np.exp(-x * 16)
    return nz * e * 0.45


def hat(open_=False):
    n = int(SR * (0.18 if open_ else 0.06)); x = np.arange(n) / SR
    nz = rng.standard_normal(n)
    return (nz - lp(nz, 8000)) * np.exp(-x * (18 if open_ else 80)) * 0.3


def pop(f0=600, f1=1400, d=0.09):
    n = int(SR * d); x = np.arange(n) / SR
    f = f0 + (f1 - f0) * (1 - np.exp(-x * 60))
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * env(n, 0.001, d / 3)


def boing(f0=180, d=0.35):
    n = int(SR * d); x = np.arange(n) / SR
    f = f0 * (1 + 0.6 * np.exp(-x * 12) * np.sin(2 * np.pi * 18 * x)) + 220 * np.exp(-x * 20)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * env(n, 0.002, d / 3)


def whoosh(d, f_lo=300, f_hi=7000, rise=True):
    n = int(SR * d); u = np.linspace(0, 1, n)
    fc = f_lo * (f_hi / f_lo) ** (u if rise else 1 - u)
    nz = rng.standard_normal(n)
    y = lp(nz, fc) - lp(nz, fc * 0.3)
    return y * np.sin(np.pi * u) ** 1.4 * 2.0


def pluck(m, d=0.35, bright=3000):
    n = int(SR * d); x = np.arange(n) / SR
    f = note(m)
    tone = np.sin(2 * np.pi * f * x) + 0.5 * np.sin(2 * np.pi * 2 * f * x) * np.exp(-x * 20) + 0.25 * np.sin(2 * np.pi * 3.01 * f * x) * np.exp(-x * 30)
    return tone * env(n, 0.002, d / 3.5)


def marimba(m, d=0.4):
    n = int(SR * d); x = np.arange(n) / SR
    f = note(m)
    return (np.sin(2 * np.pi * f * x) + 0.35 * np.sin(2 * np.pi * 3.95 * f * x) * np.exp(-x * 40)) * env(n, 0.001, 0.12)


def tick(f=2500):
    n = int(SR * 0.025); x = np.arange(n) / SR
    return np.sin(2 * np.pi * f * x) * np.exp(-x * 250)


def boom(d=1.6, f0=45):
    n = int(SR * d); x = np.arange(n) / SR
    f = f0 + 100 * np.exp(-x * 14)
    body = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-x * 2.2)
    return np.tanh(1.5 * (body + lp(rng.standard_normal(n), 3000) * np.exp(-x * 10)))


def chord_pad(ms, d):
    n = int(SR * d); x = np.arange(n) / SR
    y = sum(np.sin(2 * np.pi * note(m) * x * (1 + det)) for m in ms for det in (-0.003, 0.003))
    return y * np.minimum(1, x / 0.08) * np.minimum(1, (d - x) / 0.2)


def riser(d):
    n = int(SR * d); u = np.linspace(0, 1, n)
    f = 200 * 2 ** (u * 2.5)
    tone = np.sin(2 * np.pi * np.cumsum(f) / SR) * u ** 2
    nz = rng.standard_normal(n)
    return tone * 0.3 + (nz - lp(nz, 2000 + 9000 * u)) * u ** 2.2 * 0.8


# ---------------- intro (0 – 2 s): ball bounces, morph, "Ready?" pop, confetti
add(whoosh(0.35, 800, 3000, rise=False), 0.0, 0.35)
for ti, g in ((0.36, 1.0), (0.70, 0.8), (0.92, 0.6)):
    add(boing(160 + 40 * g), ti, 0.55 * g)
add(whoosh(0.3, 400, 5000), 0.9, 0.5)
add(pop(500, 1800, 0.14), 1.15, 0.6)
for i in range(10):
    add(tick(3000 + rng.integers(0, 3000)), 1.18 + i * 0.03, 0.15, pan=rng.uniform(-0.8, 0.8))
add(riser(0.5), 1.5, 0.7)
add(whoosh(0.3, 300, 9000), 1.72, 0.7)

# ---------------- groove (2 s → end): C major, I–V–vi–IV
prog = [(48, [60, 64, 67]), (43, [59, 62, 67]), (45, [60, 64, 69]), (41, [60, 65, 69])]
bar = BEAT * 4
b = 0
while 2.0 + b * BEAT < 15.0:
    s = 2.0 + b * BEAT
    in_logo_break = 9.1 <= s < 9.55
    if not in_logo_break:
        add(kick(), s, 0.85)
        if b % 2 == 1:
            add(clap(), s, 0.6)
        add(hat(), s + BEAT / 2, 0.55, pan=0.25)
        add(hat(), s + BEAT / 4, 0.25, pan=-0.25)
        add(hat(), s + 3 * BEAT / 4, 0.25, pan=-0.25)
        if b % 4 == 3:
            add(hat(True), s + BEAT / 2, 0.35, pan=0.3)
    root, ch = prog[int((s - 2.0) // bar) % 4]
    for k8 in range(2):
        add(pluck(root, 0.22, 800) * 0.9, s + k8 * BEAT / 2, 0.35)
    if b % 4 == 0:
        add(chord_pad(ch, bar), s, 0.05)
    b += 1

# marimba motif from 4.1 s, answering phrase each bar
motif = [72, 76, 79, 76, 81, 79, 76, 74]
k = 0
while 4.12 + k * BEAT / 2 < 9.05:
    s = 4.12 + k * BEAT / 2
    shift = [0, -1, 0, -3][int((s - 2.0) // bar) % 4]
    if k % 8 not in (3, 7):
        add(marimba(motif[k % 8] + shift), s, 0.18, pan=0.4 * np.sin(k))
    k += 1

# ---------------- word slams, stripe wipe
for s in (2.0, 2.5, 3.0, 3.5):
    add(boom(0.6, 60), s, 0.35)
    add(whoosh(0.18, 1000, 6000), s - 0.12, 0.4, pan=0.5)
add(whoosh(0.5, 300, 8000), 3.75, 0.7, pan=0.6)

# ---------------- cards spring in (rising plucks), focus ticks, blob whoosh
for i in range(4):
    add(pluck(72 + [0, 4, 7, 12][i], 0.3), 4.3 + i * 0.12, 0.3, pan=0.6 - i * 0.4)
for i, f in enumerate((5.25, 5.6, 5.95, 6.3)):
    add(pop(900 + i * 150, 1600 + i * 200, 0.07), f, 0.35, pan=0.6 - i * 0.4)
add(whoosh(0.4, 200, 6000), 6.65, 0.7)

# ---------------- devices: morph swishes and chip pops
add(boing(220, 0.3), 7.1, 0.35)
for s in (7.55, 8.1, 8.65):
    add(whoosh(0.25, 600, 7000), s - 0.05, 0.45)
    add(pop(300, 900, 0.1), s + 0.1, 0.35)
for s, p in ((7.25, 0.6), (7.85, -0.6), (8.45, 0.6)):
    add(pop(700, 2000, 0.08), s, 0.3, pan=p)

# ---------------- channel-zap static, dive, logo hit
n = int(SR * 0.12); add((rng.standard_normal(n) * 0.25) * env(n, 0.002, 0.08), 9.02, 0.6)
add(riser(0.5), 9.05, 0.9)
add(boom(2.2, 40), 9.6, 0.9)
add(kick(), 9.6, 0.8)
for i, m in enumerate([84, 88, 91, 96]):
    add(marimba(m, 0.8), 9.9 + i * 0.07, 0.14, pan=-0.5 + i * 0.33)
for i in range(8):
    add(tick(1800 + i * 150), 10.05 + i * 0.05, 0.18)
add(pop(400, 1200, 0.15), 10.5, 0.4)
add(whoosh(0.35, 800, 8000), 10.75, 0.35)

# ---------------- call to action
add(whoosh(0.4, 300, 6000), 11.35, 0.6)
add(boing(260, 0.35), 11.75, 0.45)
add(whoosh(0.35, 400, 7000), 12.1, 0.6, pan=0.6)
add(pop(600, 1500, 0.1), 12.4, 0.4)
for i in range(12):
    add(tick(2200 + (i % 3) * 400), 12.45 + i * 0.035, 0.22, pan=-0.4 + i * 0.07)
add(whoosh(0.35, 400, 7000), 12.62, 0.5, pan=-0.6)
for i in range(len("www.universeiptv-ar.com")):
    add(tick(3500 + rng.integers(-400, 400)) * 0.8, 12.9 + i * 0.75 / 23, 0.25, pan=-0.2)
for s in (13.4, 13.52, 14.35, 14.47):
    add(pluck(88, 0.12, 5000), s, 0.14)
add(tick(1200) * 2, 14.0, 0.5)
add(pop(800, 2400, 0.12), 14.02, 0.45)
add(chord_pad([60, 64, 67, 72], 1.0), 14.0, 0.08)

master = np.minimum(1, t / 0.02) * np.clip((15.0 - t) / 0.25, 0, 1)
out = np.stack([L, R], axis=1) * master[:, None]
out = np.tanh(out * 1.2) / np.tanh(1.2)
out /= np.max(np.abs(out)) + 1e-9
out *= 0.89
with wave.open("soundtrack_ar.wav", "wb") as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR)
    w.writeframes((out * 32767).astype(np.int16).tobytes())
print("wrote soundtrack_ar.wav")
