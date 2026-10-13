#!/usr/bin/env python3
"""
Erzeugt alle Texturen (inkl. Animationen), Modelle, Blockstates, Sprachdateien, Rezepte,
Loot-Tables, Tags und Data-Maps fuer Cosmic Core. Braucht nur Pillow:  pip install pillow
Aufruf (im Projektordner):  python3 tools/generate_resources.py
Alles hier ist absichtlich in einer Datei, damit du Farben/Texte/Zahlen leicht anpassen kannst.
"""
import json
import math
import os
import random
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'src', 'main', 'resources')
ASSETS = os.path.join(RES, 'assets', 'cosmiccore')
DATA = os.path.join(RES, 'data', 'cosmiccore')
MCDATA = os.path.join(RES, 'data', 'minecraft')
MOD = 'cosmiccore'

PHASES = ['dormant', 'formed', 'active', 'unstable']
FRAMES = {'dormant': 1, 'formed': 8, 'active': 16, 'unstable': 12}
FRAMETIME = {'formed': 4, 'active': 2, 'unstable': 2}


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(text)


def wjson(path, obj):
    write(path, json.dumps(obj, indent=2, ensure_ascii=False) + '\n')


# ----------------------------------------------------------------- Farb-Helfer
def clamp(v):
    return max(0, min(255, int(v)))


def lerp(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))


def put(px, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        px[x, y] = c if len(c) == 4 else (c[0], c[1], c[2], 255)


def plate(seed, base=(24, 17, 38), noise=5):
    rnd = random.Random(seed)
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            n = rnd.randint(-noise, noise)
            px[x, y] = (clamp(base[0] + n), clamp(base[1] + n), clamp(base[2] + n * 1.4), 255)
    for i in range(16):  # Fase: oben/links hell, unten/rechts dunkel
        px[i, 0] = lerp(px[i, 0][:3], (70, 55, 105), 0.55) + (255,)
        px[0, i] = lerp(px[0, i][:3], (70, 55, 105), 0.45) + (255,)
        px[i, 15] = lerp(px[i, 15][:3], (5, 3, 10), 0.6) + (255,)
        px[15, i] = lerp(px[15, i][:3], (5, 3, 10), 0.5) + (255,)
    return img


def glow_color(phase, k, n, f, nf, rnd):
    """Farbe eines Leuchtpixels: k = Position entlang des Pfads, f = Frame."""
    ph = f / nf
    if phase == 'dormant':
        return (46, 34, 78)
    if phase == 'formed':   # langsame violette Welle
        i = 0.30 + 0.45 * (0.5 + 0.5 * math.sin(2 * math.pi * (ph - k / max(n, 1))))
        return lerp((50, 28, 105), (160, 100, 255), i)
    if phase == 'active':   # schneller Lichtpuls, Cyan <-> Violett
        pulse = max(0.0, math.cos(2 * math.pi * (ph - k / max(n, 1)))) ** 2
        mix = 0.5 + 0.5 * math.sin(2 * math.pi * (ph + k / max(n, 1)))
        col = lerp((130, 80, 255), (70, 235, 255), mix)
        return lerp((38, 30, 90), col, 0.25 + 0.75 * pulse)
    # unstable: rotes Flackern + vereinzelte weisse Funken
    r = rnd.random()
    if r < 0.07:
        return (255, 225, 230)
    if r < 0.60:
        return lerp((110, 12, 26), (255, 50, 70), rnd.random())
    return (60, 10, 20)


def glow_path(px, path, phase, f, nf, seed):
    rnd = random.Random(seed * 7919 + f * 31)
    for k, (x, y) in enumerate(path):
        put(px, x, y, glow_color(phase, k, len(path), f, nf, rnd))


# ----------------------------------------------------------------- Block-Painter
def draw_casing(phase, f, nf, seed=1):
    img = plate(seed)
    px = img.load()
    dark = (10, 6, 18)
    for i in range(2, 14):
        put(px, i, 2, dark); put(px, i, 13, dark); put(px, 2, i, dark); put(px, 13, i, dark)
    for (x, y) in [(3, 3), (12, 3), (3, 12), (12, 12)]:
        put(px, x, y, (78, 64, 112))
    rnd = random.Random(seed + 99)
    for _ in range(4):
        put(px, rnd.randint(3, 12), rnd.randint(3, 12), (52, 44, 86))
    p1 = [(4, 8), (5, 8), (6, 8), (7, 8), (7, 7), (7, 6), (7, 5), (8, 5), (9, 5), (10, 5), (11, 5)]
    p2 = [(7, 9), (7, 10), (7, 11), (8, 11), (9, 11), (10, 11)]
    p3 = [(4, 5), (4, 6), (5, 6), (5, 7)]
    for k, p in enumerate((p1, p2, p3)):
        glow_path(px, p, phase, f, nf, seed + k)
    return img


def draw_frame(phase, f, nf, seed=2):
    img = plate(seed, base=(18, 12, 30), noise=4)
    px = img.load()
    for y in range(16):  # Pfosten links/rechts
        for x in (1, 14):
            put(px, x, y, (44, 34, 72))
        for x in (0, 15):
            put(px, x, y, (12, 8, 22))
    for x in range(2, 14):  # Rillen oben/unten
        put(px, x, 1, (46, 36, 76)); put(px, x, 14, (8, 5, 14))
        put(px, x, 2, (10, 6, 18)); put(px, x, 13, (10, 6, 18))
    rune = ["..XX..",
            ".X..X.",
            "X.XX.X",
            "X.XX.X",
            ".X..X.",
            "X.XXXX",
            "..XX.."]
    path = []
    for ry, row in enumerate(rune):
        for rx, ch in enumerate(row):
            if ch == 'X':
                path.append((5 + rx, 4 + ry))
    glow_path(px, path, phase, f, nf, seed)
    return img


def draw_coil(phase, f, nf, seed=3):
    img = plate(seed, base=(14, 22, 34), noise=3)
    px = img.load()
    for wy in (1, 5, 9, 13):  # Wicklungen
        for x in range(1, 15):
            put(px, x, wy, (62, 100, 126))
            put(px, x, wy + 1, (26, 44, 62))
        put(px, 0, wy, (20, 30, 44)); put(px, 15, wy, (20, 30, 44))
    for wy in (3, 7, 11):  # leuchtender Draht zwischen den Wicklungen
        path = [(x, wy) for x in range(1, 15)]
        glow_path(px, path, phase, f, nf, seed + wy)
        for x in range(1, 15):
            put(px, x, wy + 1, (8, 12, 20))
    return img


def eye_params(phase, f, nf, rnd):
    ph = f / nf
    if phase == 'dormant':
        return 0.0, (60, 40, 110), 0.5
    if phase == 'formed':
        return 0.45 + 0.12 * math.sin(2 * math.pi * ph), (115, 75, 235), 0.6
    if phase == 'active':
        mix = 0.5 + 0.5 * math.sin(2 * math.pi * ph)
        return 0.92 + 0.08 * math.sin(4 * math.pi * ph), lerp((70, 225, 255), (170, 100, 255), mix), 0.7 + 0.5 * math.sin(2 * math.pi * ph) ** 2
    return 0.65 + 0.35 * rnd.random(), (255, 50, 70), 1.2 + 0.6 * rnd.random()


def paint_eye(px, phase, f, nf, cx=7.5, cy=7.5, half_w=6.0, max_h=4.2, seed=0):
    rnd = random.Random(seed * 131 + f)
    open_, iris, pupil_w = eye_params(phase, f, nf, rnd)
    lid = (88, 60, 150) if phase != 'unstable' else (150, 30, 50)
    if open_ <= 0.01:
        for x in range(2, 14):
            put(px, x, 8, lid)
        for x in (3, 12):
            put(px, x, 9, lid)
        return
    for y in range(16):
        for x in range(16):
            dx = (x - cx) / half_w
            if abs(dx) > 1.0:
                continue
            h = open_ * max_h * math.sqrt(max(0.0, 1 - dx * dx))
            dy = abs(y - cy)
            if dy > h + 0.4:
                continue
            if dy > h - 0.7:
                put(px, x, y, lid)          # Lidrand
                continue
            d = math.hypot(x - cx, y - cy)
            if d <= 2.9:
                shade = 1.15 - d / 3.6
                c = tuple(clamp(v * shade) for v in iris)
                if abs(x - cx) <= pupil_w * 0.5 + 0.1:
                    c = (3, 0, 8)           # senkrechter Schlitz
                put(px, x, y, c)
            else:
                put(px, x, y, (26, 15, 48))
    put(px, 6, 6, (230, 225, 255))


def draw_crystal(phase, f, nf, seed=4):
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            diag = (x + y) % 6 == 0 or (x - y) % 6 == 0
            px[x, y] = (46, 26, 86, 255) if diag else (22, 11, 42, 255)
    for i in range(16):
        for (x, y) in ((i, 0), (0, i), (i, 15), (15, i)):
            px[x, y] = (12, 6, 24, 255)
    paint_eye(px, phase, f, nf, seed=seed)
    return img


def draw_glass(phase, f, nf, seed=5):
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    inner_a = {'dormant': 60, 'formed': 80, 'active': 105, 'unstable': 105}[phase]
    tint = {'dormant': (34, 20, 70), 'formed': (44, 26, 96), 'active': (40, 60, 120), 'unstable': (90, 20, 40)}[phase]
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            if edge:
                px[x, y] = (54, 28, 100, 240) if phase != 'unstable' else (96, 24, 48, 240)
            else:
                px[x, y] = (tint[0], tint[1], tint[2], inner_a)
    for (x, y) in [(1, 1), (14, 1), (1, 14), (14, 14)]:
        px[x, y] = (86, 54, 150, 220)
    stars = [(4, 4), (11, 6), (7, 11), (12, 12), (3, 10), (9, 3)]
    rnd = random.Random(seed * 17 + f)
    for k, (x, y) in enumerate(stars):
        if phase == 'dormant':
            a = 120
        else:
            a = int(110 + 140 * (0.5 + 0.5 * math.sin(2 * math.pi * (f / nf) * (1 + k % 2) + k)))
            if phase == 'unstable' and rnd.random() < 0.4:
                a = 40
        col = (200, 185, 255) if phase != 'unstable' else (255, 150, 160)
        px[x, y] = (col[0], col[1], col[2], clamp(a))
    return img


def draw_controller_front(phase, f, nf, seed=6):
    img = plate(seed, base=(20, 13, 34), noise=4)
    px = img.load()
    for i in range(2, 14):
        put(px, i, 2, (8, 5, 14)); put(px, i, 13, (8, 5, 14)); put(px, 2, i, (8, 5, 14)); put(px, 13, i, (8, 5, 14))
    for y in range(4, 12):  # Linsenfassung
        for x in range(2, 14):
            if (x in (2, 13)) or (y in (4, 11)):
                put(px, x, y, (60, 48, 96))
            else:
                put(px, x, y, (10, 6, 20))
    paint_eye(px, phase, f, nf, cx=7.5, cy=7.5, half_w=5.0, max_h=3.0, seed=seed)
    ph = f / nf
    leds = [(1, 1), (14, 1), (1, 14), (14, 14)]
    for k, (x, y) in enumerate(leds):
        if phase == 'dormant':
            c = (30, 24, 44)
        elif phase == 'formed':
            c = lerp((60, 40, 120), (170, 110, 255), 0.5 + 0.5 * math.sin(2 * math.pi * ph + k))
        elif phase == 'active':
            on = int(ph * 4) % 4 == k
            c = (70, 255, 210) if on else (24, 90, 90)
        else:
            c = (255, 40, 50) if (f + k) % 2 == 0 else (70, 10, 16)
        put(px, x, y, c)
    return img


def draw_energy_port(phase, f, nf, seed=7):
    img = draw_casing_base(seed)
    px = img.load()
    inset(px)
    bolt = [(9, 4), (8, 5), (7, 6), (6, 7), (7, 7), (8, 7), (9, 7), (8, 8), (7, 9), (6, 10)]
    glow_path(px, bolt, phase, f, nf, seed)
    return img


def draw_item_port(phase, f, nf, seed=8):
    img = draw_casing_base(seed)
    px = img.load()
    inset(px)
    chev = [(5, 5), (6, 6), (7, 7), (8, 7), (9, 6), (10, 5), (5, 8), (6, 9), (7, 10), (8, 10), (9, 9), (10, 8)]
    glow_path(px, chev, phase, f, nf, seed)
    return img


def draw_casing_base(seed):
    img = plate(seed)
    px = img.load()
    for (x, y) in [(1, 1), (14, 1), (1, 14), (14, 14)]:
        put(px, x, y, (78, 64, 112))
    return img


def inset(px):
    for y in range(3, 13):
        for x in range(3, 13):
            if x in (3, 12) or y in (3, 12):
                put(px, x, y, (58, 46, 92))
            else:
                put(px, x, y, (9, 5, 18))


def draw_core_dormant():
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = (14, 8, 26, 255)
    for y in range(4, 12):
        for x in range(4, 12):
            ring = max(abs(x - 7.5), abs(y - 7.5))
            px[x, y] = (30, 18, 52, 255) if ring > 2.8 else (6, 3, 12, 255)
    for i in range(4, 12):
        px[i, 4] = px[i, 11] = px[4, i] = px[11, i] = (70, 48, 118, 255)
    px[7, 7] = px[8, 8] = (150, 95, 255, 255)
    return img


def draw_star_dust(f, nf):
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()
    pile = [(7, 8), (8, 8), (6, 9), (7, 9), (8, 9), (9, 9), (5, 10), (6, 10), (7, 10), (8, 10), (9, 10), (10, 10),
            (4, 11), (5, 11), (6, 11), (7, 11), (8, 11), (9, 11), (10, 11), (11, 11)]
    rnd = random.Random(11)
    for (x, y) in pile:
        t = rnd.random()
        put(px, x, y, lerp((70, 40, 150), (150, 110, 255), t))
    for (x, y) in [(5, 11), (9, 10), (7, 8)]:
        put(px, x, y, (90, 230, 255))
    sparkles = [(3, 5), (11, 4), (6, 3), (12, 8), (8, 6), (4, 9)]
    for k, (x, y) in enumerate(sparkles):
        a = 0.5 + 0.5 * math.sin(2 * math.pi * f / nf + k * 1.3)
        if a > 0.55:
            put(px, x, y, (255, 255, 255))
            if a > 0.85:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    put(px, x + dx, y + dy, (160, 230, 255))
    return img


# ----------------------------------------------------------------- Ausgabe der Texturen
PAINTERS = {
    'void_casing': draw_casing,
    'rune_frame': draw_frame,
    'resonance_coil': draw_coil,
    'focus_crystal': draw_crystal,
    'focus_glass': draw_glass,
    'controller_front': draw_controller_front,
    'energy_port': draw_energy_port,
    'item_port': draw_item_port,
}


def save_strip(name, frames, frametime, folder='block'):
    w, h = frames[0].size
    strip = Image.new('RGBA', (w, h * len(frames)))
    for i, fr in enumerate(frames):
        strip.paste(fr, (0, i * h))
    out = os.path.join(ASSETS, 'textures', folder, name + '.png')
    os.makedirs(os.path.dirname(out), exist_ok=True)
    strip.save(out)
    if len(frames) > 1:
        wjson(out + '.mcmeta', {'animation': {'frametime': frametime}})


def textures():
    sheets = {}
    for name, painter in PAINTERS.items():
        for phase in PHASES:
            nf = FRAMES[phase]
            frames = [painter(phase, f, nf) for f in range(nf)]
            save_strip(f'{name}_{phase}', frames, FRAMETIME.get(phase, 1))
            sheets[(name, phase)] = frames
    save_strip('singularity_core_dormant', [draw_core_dormant()], 1)
    save_strip('star_dust', [draw_star_dust(f, 8) for f in range(8)], 3, folder='item')
    return sheets


def preview(sheets):
    names = list(PAINTERS.keys())
    scale = 6
    cols = len(PHASES) * 2
    img = Image.new('RGBA', (cols * 17 * scale, len(names) * 17 * scale), (60, 60, 70, 255))
    for r, name in enumerate(names):
        c = 0
        for phase in PHASES:
            frames = sheets[(name, phase)]
            for fr in (frames[0], frames[len(frames) // 2]):
                big = fr.resize((16 * scale, 16 * scale), Image.NEAREST)
                img.paste(big, (c * 17 * scale, r * 17 * scale), big)
                c += 1
    img.save('/tmp/preview.png')


# ----------------------------------------------------------------- Modelle / Blockstates
SIMPLE = ['void_casing', 'rune_frame', 'resonance_coil', 'focus_crystal', 'energy_port', 'item_port']


def models():
    for b in SIMPLE:
        for p in PHASES:
            wjson(f'{ASSETS}/models/block/{b}_{p}.json',
                  {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{MOD}:block/{b}_{p}'}})
        wjson(f'{ASSETS}/blockstates/{b}.json',
              {'variants': {f'phase={p}': {'model': f'{MOD}:block/{b}_{p}'} for p in PHASES}})
        wjson(f'{ASSETS}/models/item/{b}.json', {'parent': f'{MOD}:block/{b}_formed'})

    # Glas (transluzent)
    for p in PHASES:
        wjson(f'{ASSETS}/models/block/focus_glass_{p}.json',
              {'parent': 'minecraft:block/cube_all', 'render_type': 'minecraft:translucent',
               'textures': {'all': f'{MOD}:block/focus_glass_{p}'}})
    wjson(f'{ASSETS}/blockstates/focus_glass.json',
          {'variants': {f'phase={p}': {'model': f'{MOD}:block/focus_glass_{p}'} for p in PHASES}})
    wjson(f'{ASSETS}/models/item/focus_glass.json', {'parent': f'{MOD}:block/focus_glass_formed'})

    # Controller: Front (north) mit Auge, Rest wie Gehaeuse
    rot = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
    for p in PHASES:
        side = f'{MOD}:block/void_casing_{p}'
        wjson(f'{ASSETS}/models/block/controller_{p}.json',
              {'parent': 'minecraft:block/cube',
               'textures': {'particle': side, 'down': side, 'up': side, 'north': f'{MOD}:block/controller_front_{p}',
                            'east': side, 'south': side, 'west': side}})
    variants = {}
    for facing, y in rot.items():
        for p in PHASES:
            entry = {'model': f'{MOD}:block/controller_{p}'}
            if y:
                entry['y'] = y
            variants[f'facing={facing},phase={p}'] = entry
    wjson(f'{ASSETS}/blockstates/controller.json', {'variants': variants})
    wjson(f'{ASSETS}/models/item/controller.json', {'parent': f'{MOD}:block/controller_formed'})

    # Singularitaetskern: inaktiv = kleiner dunkler Wuerfel, sonst unsichtbar (der Renderer uebernimmt)
    faces = {d: {'texture': '#core'} for d in ('down', 'up', 'north', 'south', 'west', 'east')}
    wjson(f'{ASSETS}/models/block/singularity_core_dormant.json',
          {'parent': 'minecraft:block/block',
           'textures': {'particle': f'{MOD}:block/singularity_core_dormant', 'core': f'{MOD}:block/singularity_core_dormant'},
           'elements': [{'from': [4, 4, 4], 'to': [12, 12, 12], 'faces': faces}]})
    wjson(f'{ASSETS}/models/block/singularity_core_hidden.json',
          {'textures': {'particle': f'{MOD}:block/singularity_core_dormant'}})
    wjson(f'{ASSETS}/blockstates/singularity_core.json',
          {'variants': {
              'phase=dormant': {'model': f'{MOD}:block/singularity_core_dormant'},
              'phase=formed': {'model': f'{MOD}:block/singularity_core_hidden'},
              'phase=active': {'model': f'{MOD}:block/singularity_core_hidden'},
              'phase=unstable': {'model': f'{MOD}:block/singularity_core_hidden'}}})
    wjson(f'{ASSETS}/models/item/singularity_core.json', {'parent': f'{MOD}:block/singularity_core_dormant'})

    wjson(f'{ASSETS}/models/item/star_dust.json',
          {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{MOD}:item/star_dust'}})


# ----------------------------------------------------------------- Sprache
DE = {
    'itemGroup.cosmiccore': 'Kosmischer Kern',
    'block.cosmiccore.void_casing': 'Leerengehäuse',
    'block.cosmiccore.rune_frame': 'Runenrahmen',
    'block.cosmiccore.focus_glass': 'Fokusglas',
    'block.cosmiccore.resonance_coil': 'Resonanzspule',
    'block.cosmiccore.focus_crystal': 'Fokuskristall',
    'block.cosmiccore.singularity_core': 'Singularitätskern',
    'block.cosmiccore.controller': 'Kerngenerator-Controller',
    'block.cosmiccore.energy_port': 'Energie-Port',
    'block.cosmiccore.item_port': 'Item-Port',
    'item.cosmiccore.star_dust': 'Sternenstaub',
    'message.cosmiccore.status.header': '--- Kerngenerator ---',
    'message.cosmiccore.status.pending': 'Struktur kann gerade nicht geprüft werden (Chunks nicht geladen?).',
    'message.cosmiccore.status.invalid': 'Struktur unvollständig: %s Fehler.',
    'message.cosmiccore.status.error_at': 'Erster Fehler bei %s %s %s: erwartet %s',
    'message.cosmiccore.status.formed': 'Struktur gebildet.',
    'message.cosmiccore.status.energy': 'Energie: %s / %s RF',
    'message.cosmiccore.status.output': 'Leistung: %s RF/t (Resonanz %s%%)',
    'message.cosmiccore.status.instability': 'Instabilität: %s%%',
    'message.cosmiccore.status.fuel': 'Brennstoff: %s im Slot, aktuelle Ladung noch %s s',
    'message.cosmiccore.status.stabilizer': 'Stabilisator: %s im Slot, Wirkung noch %s s',
    'message.cosmiccore.status.cosmic': 'Kosmischer Faktor: x%s',
    'message.cosmiccore.status.collapsed': 'Der Kern ist kollabiert - Neustart in %s s.',
    'message.cosmiccore.expected_air': 'Luft (die Kammer muss leer sein)',
    'message.cosmiccore.expected_hull': 'Leerengehäuse oder Port',
    'message.cosmiccore.expected_slot': 'Leerengehäuse, Port oder Controller (Mitte einer Bodenkante)',
    'tooltip.cosmiccore.fuel': 'Kerngenerator-Brennstoff: %s RF/t, %s s',
    'tooltip.cosmiccore.stabilizer': 'Kerngenerator-Stabilisator: -%s Instabilität/s, %s s',
    'message.cosmiccore.status.no_size': 'Noch keine Reaktorgröße gewählt - Rechtsklick öffnet die Auswahl.',
    'gui.cosmiccore.choose_size': 'Reaktorgröße wählen',
    'gui.cosmiccore.choose_once': 'Einmalige Wahl - später nicht änderbar',
    'gui.cosmiccore.size_button': '%sx%sx%s',
    'gui.cosmiccore.size_info': 'Max. Stufe %s · %s Blöcke · %s Spulen · Puffer x%s',
    'gui.cosmiccore.build': 'Bauen',
    'gui.cosmiccore.start': 'Zünden',
    'gui.cosmiccore.stop': 'Stopp',
    'gui.cosmiccore.preview_on': 'Vorschau ✔',
    'gui.cosmiccore.preview_off': 'Vorschau',
    'gui.cosmiccore.output': 'Leistung: %s RF/t',
    'gui.cosmiccore.energy': 'Energie: %s / %s RF',
    'gui.cosmiccore.instability': 'Instabilität: %s %%',
    'gui.cosmiccore.cosmic': 'Kosmischer Faktor: x%s',
    'gui.cosmiccore.sky_on': '✦ Sternenlicht verbunden',
    'gui.cosmiccore.sky_off': '✧ Kein Himmel über der Decke',
    'gui.cosmiccore.size': 'Reaktor: %sx%sx%s',
    'gui.cosmiccore.scale': 'Leistungsstufe',
    'gui.cosmiccore.hint_errors': 'Schleichen + Rechtsklick: erster Baufehler',
    'gui.cosmiccore.status.incomplete': 'Unvollständig (%s Fehler)',
    'gui.cosmiccore.status.collapsed': 'Kollabiert! (%s s)',
    'gui.cosmiccore.status.off': 'Abgeschaltet',
    'gui.cosmiccore.status.idle': 'Bereit - kein Brennstoff',
    'gui.cosmiccore.status.unstable': 'INSTABIL!',
    'gui.cosmiccore.status.active': 'Aktiv',
    'gui.cosmiccore.tip.energy': 'Energiepuffer',
    'gui.cosmiccore.tip.instability': 'Instabilität',
    'gui.cosmiccore.tip.instability_hint': 'Bei 100 % kollabiert der Kern und zerbricht!',
    'gui.cosmiccore.tip.resonance': 'Resonanz (Leistung baut sich langsam auf)',
    'gui.cosmiccore.tip.scale': 'Leistungsstufe %s von %s',
    'gui.cosmiccore.tip.scale_output': 'Leistung x%s',
    'gui.cosmiccore.tip.scale_fuel': 'Brennstoffverbrauch x%s',
    'gui.cosmiccore.tip.scale_instability': 'Instabilität x%s',
    'gui.cosmiccore.tip.fuel_slot': 'Brennstoff (z. B. Sternenstaub)',
    'gui.cosmiccore.tip.stab_slot': 'Stabilisator (z. B. Obsidian)',
    'gui.cosmiccore.tip.sky': 'Freie Sicht zum Himmel über der Decke bringt Sternenlicht: mehr Leistung, nachts und bei Vollmond noch mehr.',
}
EN = {
    'itemGroup.cosmiccore': 'Cosmic Core',
    'block.cosmiccore.void_casing': 'Void Casing',
    'block.cosmiccore.rune_frame': 'Rune Frame',
    'block.cosmiccore.focus_glass': 'Focus Glass',
    'block.cosmiccore.resonance_coil': 'Resonance Coil',
    'block.cosmiccore.focus_crystal': 'Focus Crystal',
    'block.cosmiccore.singularity_core': 'Singularity Core',
    'block.cosmiccore.controller': 'Core Generator Controller',
    'block.cosmiccore.energy_port': 'Energy Port',
    'block.cosmiccore.item_port': 'Item Port',
    'item.cosmiccore.star_dust': 'Star Dust',
    'message.cosmiccore.status.header': '--- Core Generator ---',
    'message.cosmiccore.status.pending': 'Structure cannot be checked right now (chunks not loaded?).',
    'message.cosmiccore.status.invalid': 'Structure incomplete: %s errors.',
    'message.cosmiccore.status.error_at': 'First error at %s %s %s: expected %s',
    'message.cosmiccore.status.formed': 'Structure formed.',
    'message.cosmiccore.status.energy': 'Energy: %s / %s RF',
    'message.cosmiccore.status.output': 'Output: %s RF/t (resonance %s%%)',
    'message.cosmiccore.status.instability': 'Instability: %s%%',
    'message.cosmiccore.status.fuel': 'Fuel: %s in slot, current charge %s s left',
    'message.cosmiccore.status.stabilizer': 'Stabilizer: %s in slot, effect %s s left',
    'message.cosmiccore.status.cosmic': 'Cosmic factor: x%s',
    'message.cosmiccore.status.collapsed': 'The core has collapsed - restarting in %s s.',
    'message.cosmiccore.expected_air': 'air (the chamber must be empty)',
    'message.cosmiccore.expected_hull': 'Void Casing or port',
    'message.cosmiccore.expected_slot': 'Void Casing, port or controller (middle of a bottom edge)',
    'tooltip.cosmiccore.fuel': 'Core generator fuel: %s RF/t, %s s',
    'tooltip.cosmiccore.stabilizer': 'Core generator stabilizer: -%s instability/s, %s s',
    'message.cosmiccore.status.no_size': 'No reactor size chosen yet - right-click to open the selection.',
    'gui.cosmiccore.choose_size': 'Choose reactor size',
    'gui.cosmiccore.choose_once': 'One-time choice - cannot be changed later',
    'gui.cosmiccore.size_button': '%sx%sx%s',
    'gui.cosmiccore.size_info': 'Max level %s · %s blocks · %s coils · buffer x%s',
    'gui.cosmiccore.build': 'Build',
    'gui.cosmiccore.start': 'Ignite',
    'gui.cosmiccore.stop': 'Stop',
    'gui.cosmiccore.preview_on': 'Preview ✔',
    'gui.cosmiccore.preview_off': 'Preview',
    'gui.cosmiccore.output': 'Output: %s RF/t',
    'gui.cosmiccore.energy': 'Energy: %s / %s RF',
    'gui.cosmiccore.instability': 'Instability: %s %%',
    'gui.cosmiccore.cosmic': 'Cosmic factor: x%s',
    'gui.cosmiccore.sky_on': '✦ Starlight connected',
    'gui.cosmiccore.sky_off': '✧ No sky above the ceiling',
    'gui.cosmiccore.size': 'Reactor: %sx%sx%s',
    'gui.cosmiccore.scale': 'Power level',
    'gui.cosmiccore.hint_errors': 'Sneak + right-click: first build error',
    'gui.cosmiccore.status.incomplete': 'Incomplete (%s errors)',
    'gui.cosmiccore.status.collapsed': 'Collapsed! (%s s)',
    'gui.cosmiccore.status.off': 'Shut down',
    'gui.cosmiccore.status.idle': 'Ready - no fuel',
    'gui.cosmiccore.status.unstable': 'UNSTABLE!',
    'gui.cosmiccore.status.active': 'Active',
    'gui.cosmiccore.tip.energy': 'Energy buffer',
    'gui.cosmiccore.tip.instability': 'Instability',
    'gui.cosmiccore.tip.instability_hint': 'At 100 % the core collapses and shatters!',
    'gui.cosmiccore.tip.resonance': 'Resonance (output builds up slowly)',
    'gui.cosmiccore.tip.scale': 'Power level %s of %s',
    'gui.cosmiccore.tip.scale_output': 'Output x%s',
    'gui.cosmiccore.tip.scale_fuel': 'Fuel consumption x%s',
    'gui.cosmiccore.tip.scale_instability': 'Instability x%s',
    'gui.cosmiccore.tip.fuel_slot': 'Fuel (e.g. Star Dust)',
    'gui.cosmiccore.tip.stab_slot': 'Stabilizer (e.g. Obsidian)',
    'gui.cosmiccore.tip.sky': 'A clear view of the sky above the ceiling channels starlight: more output, even more at night and full moon.',
}


def lang():
    wjson(f'{ASSETS}/lang/de_de.json', DE)
    wjson(f'{ASSETS}/lang/en_us.json', EN)


# ----------------------------------------------------------------- Daten
BLOCKS = ['void_casing', 'rune_frame', 'focus_glass', 'resonance_coil', 'focus_crystal',
          'singularity_core', 'controller', 'energy_port', 'item_port']


def item(i):
    return {'item': i}


def shaped(name, pattern, key, result, count):
    wjson(f'{DATA}/recipe/{name}.json', {
        'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': pattern,
        'key': {k: item(v) for k, v in key.items()}, 'result': {'id': result, 'count': count}})


def shapeless(name, ingredients, result, count):
    wjson(f'{DATA}/recipe/{name}.json', {
        'type': 'minecraft:crafting_shapeless', 'category': 'misc',
        'ingredients': [item(i) for i in ingredients], 'result': {'id': result, 'count': count}})


def data():
    for b in BLOCKS:
        wjson(f'{DATA}/loot_table/blocks/{b}.json', {
            'type': 'minecraft:block',
            'pools': [{'rolls': 1.0, 'bonus_rolls': 0.0,
                       'entries': [{'type': 'minecraft:item', 'name': f'{MOD}:{b}'}],
                       'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
            'random_sequence': f'{MOD}:blocks/{b}'})
    values = [f'{MOD}:{b}' for b in BLOCKS]
    wjson(f'{MCDATA}/tags/block/mineable/pickaxe.json', {'replace': False, 'values': values})
    wjson(f'{MCDATA}/tags/block/needs_iron_tool.json', {'replace': False, 'values': values})

    shaped('void_casing', ['OIO', 'IAI', 'OIO'],
           {'O': 'minecraft:obsidian', 'I': 'minecraft:iron_ingot', 'A': 'minecraft:amethyst_shard'}, f'{MOD}:void_casing', 4)
    shaped('rune_frame', ['CAC', 'ARA', 'CAC'],
           {'C': f'{MOD}:void_casing', 'A': 'minecraft:amethyst_shard', 'R': 'minecraft:redstone'}, f'{MOD}:rune_frame', 2)
    shaped('focus_glass', ['GGG', 'GAG', 'GGG'],
           {'G': 'minecraft:glass', 'A': 'minecraft:amethyst_shard'}, f'{MOD}:focus_glass', 4)
    shaped('resonance_coil', ['CCC', 'RVR', 'CCC'],
           {'C': 'minecraft:copper_ingot', 'R': 'minecraft:redstone', 'V': f'{MOD}:void_casing'}, f'{MOD}:resonance_coil', 1)
    shaped('focus_crystal', ['ADA', 'DED', 'ADA'],
           {'A': 'minecraft:amethyst_shard', 'D': 'minecraft:diamond', 'E': 'minecraft:ender_eye'}, f'{MOD}:focus_crystal', 1)
    shaped('singularity_core', ['OEO', 'ENE', 'OEO'],
           {'O': 'minecraft:crying_obsidian', 'E': 'minecraft:ender_eye', 'N': 'minecraft:nether_star'}, f'{MOD}:singularity_core', 1)
    shaped('controller', ['CDC', 'ESE', 'CDC'],
           {'C': f'{MOD}:void_casing', 'D': 'minecraft:diamond', 'E': 'minecraft:ender_eye', 'S': f'{MOD}:star_dust'}, f'{MOD}:controller', 1)
    shapeless('energy_port', [f'{MOD}:void_casing', 'minecraft:redstone', 'minecraft:redstone', 'minecraft:copper_ingot'],
              f'{MOD}:energy_port', 1)
    shapeless('item_port', [f'{MOD}:void_casing', 'minecraft:hopper'], f'{MOD}:item_port', 1)
    shapeless('star_dust', ['minecraft:amethyst_shard', 'minecraft:amethyst_shard', 'minecraft:ender_pearl',
                            'minecraft:glowstone_dust'], f'{MOD}:star_dust', 2)

    # Data Maps: hier tunst du die Balance - auch per Datapack ueberschreibbar
    wjson(f'{DATA}/data_maps/item/core_fuel.json', {'values': {
        f'{MOD}:star_dust': {'burn_time': 2400, 'power': 1200},
        'minecraft:ender_pearl': {'burn_time': 1200, 'power': 400},
        'minecraft:ender_eye': {'burn_time': 1800, 'power': 700},
        'minecraft:echo_shard': {'burn_time': 3600, 'power': 900},
        'minecraft:sculk_catalyst': {'burn_time': 2400, 'power': 600},
        'minecraft:nether_star': {'burn_time': 12000, 'power': 3000}}})
    wjson(f'{DATA}/data_maps/item/core_stabilizer.json', {'values': {
        'minecraft:obsidian': {'duration': 1200, 'strength': 0.02},
        'minecraft:crying_obsidian': {'duration': 2400, 'strength': 0.035},
        'minecraft:netherite_scrap': {'duration': 3600, 'strength': 0.06}}})


if __name__ == '__main__':
    sheets = textures()
    models()
    lang()
    data()
    if '--preview' in sys.argv:
        preview(sheets)
    print('Resources generated.')
