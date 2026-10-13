#!/usr/bin/env python3
"""
Erzeugt die eigenen Sounds von Cosmic Core synthetisch (numpy) und kodiert sie mit ffmpeg (libvorbis) als OGG.
Aufruf (im Projektordner):  python3 tools/generate_sounds.py
Ausgabe: src/main/resources/assets/cosmiccore/sounds/<name>.ogg
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'cosmiccore', 'sounds')
SR = 44100
rng = np.random.default_rng(7)


def t_axis(sec):
    return np.linspace(0, sec, int(SR * sec), endpoint=False)


def env_adsr(n, a=0.02, d=0.1, s=0.7, r=0.3):
    """Einfache Huellkurve (Anteile der Gesamtlaenge)."""
    e = np.ones(n)
    ai, di, ri = int(n * a), int(n * d), int(n * r)
    e[:ai] = np.linspace(0, 1, ai) if ai else 1
    e[ai:ai + di] = np.linspace(1, s, di) if di else s
    e[ai + di:n - ri] = s
    e[n - ri:] = np.linspace(s, 0, ri) if ri else 0
    return e


def tone(t, f, phase=0.0):
    return np.sin(2 * np.pi * f * t + phase)


def sweep(t, f0, f1):
    """Sinus mit gleitender Frequenz (exponentiell)."""
    f = f0 * (f1 / f0) ** (t / t[-1])
    return np.sin(2 * np.pi * np.cumsum(f) / SR)


def noise(n):
    return rng.standard_normal(n)


def lowpass(x, alpha):
    y = np.empty_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc += alpha * (v - acc)
        y[i] = acc
    return y


def reverb(x, delays=(0.031, 0.047, 0.071, 0.113), decay=0.45):
    """Kleiner Hall aus ein paar Echos (gibt den Klaengen Raum)."""
    out = x.copy()
    for k, d in enumerate(delays):
        s = int(SR * d)
        g = decay ** (k + 1)
        out[s:] += x[:-s] * g
    tail = np.zeros(int(SR * 0.3))
    return np.concatenate([out, tail])


def normalize(x, peak=0.9):
    m = np.max(np.abs(x)) or 1.0
    return x / m * peak


def save(name, x):
    os.makedirs(OUT, exist_ok=True)
    x = normalize(x)
    pcm = (x * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix='.wav', delete=False) as tmp:
        path = tmp.name
    with wave.open(path, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', path, '-c:a', 'libvorbis', '-q:a', '4',
                    os.path.join(OUT, name + '.ogg')], check=True)
    os.remove(path)


# ----------------------------------------------------------------- Kern
def core_hum():
    t = t_axis(4.0)
    base = 0.6 * tone(t, 55) + 0.35 * tone(t, 110.4) + 0.15 * tone(t, 165.2)
    shimmer = 0.12 * tone(t, 880 + 6 * np.sin(2 * np.pi * 0.7 * t)) * (0.5 + 0.5 * np.sin(2 * np.pi * 0.5 * t))
    wobble = 1 + 0.15 * np.sin(2 * np.pi * 0.8 * t)
    return reverb((base * wobble + shimmer) * env_adsr(len(t), 0.15, 0.1, 0.9, 0.3))


def core_ignite():
    t = t_axis(2.2)
    rise = sweep(t, 60, 420) * 0.6
    chord = sum(tone(t, f) for f in (220, 277.2, 329.6, 440)) * 0.15 * np.clip((t - 1.0) / 0.4, 0, 1)
    whoosh = lowpass(noise(len(t)), 0.08) * 0.6 * np.sin(np.pi * t / t[-1])
    return reverb((rise + chord + whoosh) * env_adsr(len(t), 0.05, 0.1, 0.9, 0.35))


def core_form():
    t = t_axis(3.2)
    suck = sweep(t, 900, 70) * 0.5 * np.clip(1.6 - t, 0, 1)
    flash_t = np.clip(t - 1.7, 0, None)
    flash = sum(tone(t, f) for f in (261.6, 392, 523.3, 784)) * 0.2 * np.exp(-flash_t * 1.6) * (t > 1.7)
    boom = tone(t, 48) * np.exp(-flash_t * 3.0) * (t > 1.7) * 0.8
    air = lowpass(noise(len(t)), 0.05) * 0.4 * np.clip(1.6 - t, 0, 1)
    return reverb(suck + flash + boom + air)


def core_alarm():
    t = t_axis(0.7)
    beat = np.exp(-((t - 0.05) / 0.04) ** 2) + 0.7 * np.exp(-((t - 0.25) / 0.04) ** 2)
    body = tone(t, 70) * 0.9 + tone(t, 140) * 0.3 + tone(t, 520) * 0.15
    return reverb(body * beat)


def core_collapse():
    t = t_axis(3.5)
    boom = tone(t, 38) * np.exp(-t * 1.2) + tone(t, 52) * np.exp(-t * 1.6) * 0.6
    crack = noise(len(t)) * np.exp(-t * 6) * 0.8
    rumble = lowpass(noise(len(t)), 0.02) * np.exp(-t * 0.8) * 1.2
    down = sweep(t, 600, 40) * np.exp(-t * 1.0) * 0.4
    return reverb(boom + crack + rumble + down, decay=0.55)


# ----------------------------------------------------------------- Laser und Asteroiden
def laser_lock():
    t = t_axis(0.35)
    return reverb((tone(t, 1320) * 0.6 + tone(t, 1980) * 0.3) * env_adsr(len(t), 0.01, 0.2, 0.4, 0.5))


def laser_charge():
    t = t_axis(1.0)
    whine = sweep(t, 200, 1600) * 0.5
    sparkle = tone(t, 2400 + 400 * np.sin(2 * np.pi * 9 * t)) * 0.12 * (t / t[-1])
    return reverb((whine + sparkle) * env_adsr(len(t), 0.05, 0.05, 1.0, 0.1))


def asteroid_shatter():
    t = t_axis(1.6)
    crunch = noise(len(t)) * np.exp(-t * 7) * 0.9
    thump = tone(t, 70) * np.exp(-t * 5) * 0.8
    glass = sum(tone(t, f) * np.exp(-t * (3 + k)) for k, f in enumerate((1760, 2349, 2960, 3520))) * 0.18
    return reverb(crunch + thump + glass, decay=0.5)


def collect_ping():
    t = t_axis(0.6)
    return reverb((tone(t, 1567.98) * 0.6 + tone(t, 3135.96) * 0.2) * np.exp(-t * 6))


def orb_formed():
    t = t_axis(1.6)
    chord = sum(tone(t, f) for f in (392, 493.9, 587.3, 784)) * 0.22
    swell = np.clip(t / 0.25, 0, 1) * np.exp(-np.clip(t - 0.25, 0, None) * 1.8)
    shimmer = tone(t, 2349 + 30 * np.sin(2 * np.pi * 6 * t)) * 0.08
    return reverb((chord + shimmer) * swell)


def core_absorb():
    t = t_axis(1.8)
    down = sweep(t, 1200, 90) * 0.5 * np.exp(-t * 0.8)
    thump = tone(t, 55) * np.exp(-np.clip(t - 0.9, 0, None) * 4) * (t > 0.9)
    chord = sum(tone(t, f) for f in (130.8, 196, 261.6)) * 0.2 * np.exp(-np.clip(t - 0.9, 0, None) * 1.5) * (t > 0.9)
    return reverb(down + thump + chord, decay=0.5)


def awakening():
    t = t_axis(7.0)
    drone = (tone(t, 41.2) + 0.6 * tone(t, 61.7) + 0.4 * tone(t, 82.4)) * np.clip(t / 1.5, 0, 1)
    choir = sum(tone(t, f * (1 + 0.003 * np.sin(2 * np.pi * (0.3 + k * 0.1) * t)))
                for k, f in enumerate((220, 261.6, 329.6, 392, 523.3))) * 0.12 * np.clip((t - 1.0) / 2.0, 0, 1)
    rise = sweep(t, 80, 640) * 0.15 * np.clip(t / 3.0, 0, 1)
    air = lowpass(noise(len(t)), 0.03) * 0.3
    return reverb((drone * 0.5 + choir + rise + air) * env_adsr(len(t), 0.1, 0.1, 1.0, 0.35), decay=0.6)


SOUNDS = {
    'core_hum': core_hum, 'core_ignite': core_ignite, 'core_form': core_form, 'core_alarm': core_alarm,
    'core_collapse': core_collapse, 'laser_lock': laser_lock, 'laser_charge': laser_charge,
    'asteroid_shatter': asteroid_shatter, 'collect_ping': collect_ping, 'orb_formed': orb_formed,
    'core_absorb': core_absorb, 'awakening': awakening,
}

if __name__ == '__main__':
    for name, fn in SOUNDS.items():
        save(name, fn())
        print('  ', name)
    print('Sounds generated.')
