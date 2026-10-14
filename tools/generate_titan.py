#!/usr/bin/env python3
"""
Erzeugt die Assets des Void-Titanen fuer die Erwachen-Cutscene (GeckoLib, optional):
  assets/cosmiccore/geo/void_titan.geo.json             - Modell (Bedrock-Format)
  assets/cosmiccore/animations/void_titan.animation.json - Kinofilm-Animation (12 s)
  assets/cosmiccore/textures/entity/void_titan.png       - Grundtextur (der Koerper wird als Sternenfeld gezeichnet)
  assets/cosmiccore/textures/entity/void_titan_glowmask.png - leuchtende Adern, Augen, Krone, Kugel
Aufruf: python3 tools/generate_titan.py (wird auch von generate_resources.py aufgerufen)
"""
import json
import math
import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'cosmiccore')
TEX = 256
BODY_UV = [0, 0]      # Bereich mit Adern
BRIGHT_UV = [0, 200]  # gleissend helle Flaeche (Augen, Kugel, Krone, Krallen)


def cube(origin, size, uv=BODY_UV, rotation=None, pivot=None):
    c = {'origin': origin, 'size': size, 'uv': uv}
    if rotation:
        c['rotation'] = rotation
        c['pivot'] = pivot or origin
    return c


def bone(name, pivot, cubes=(), parent=None, rotation=None):
    b = {'name': name, 'pivot': pivot}
    if parent:
        b['parent'] = parent
    if rotation:
        b['rotation'] = rotation
    if cubes:
        b['cubes'] = list(cubes)
    return b


def model():
    bones = [
        bone('root', [0, 0, 0]),
        bone('body', [0, 40, 0], parent='root', cubes=[
            cube([-4, 0, -3], [8, 14, 6]),            # Schweif aus Leere (unten schmal)
            cube([-7, 14, -4], [14, 12, 8]),          # Unterleib
            cube([-10, 26, -5], [20, 18, 10]),        # Brust
            cube([-14, 38, -5], [28, 6, 10]),         # Schultern
            cube([-3, 30, -5.6], [6, 6, 1], BRIGHT_UV),  # leuchtender Herzkern
        ]),
        bone('head', [0, 44, 0], parent='body', cubes=[
            cube([-5, 44, -5], [10, 11, 10]),
            cube([-4, 48.5, -5.4], [3, 1.5, 1], BRIGHT_UV),  # Augen
            cube([1, 48.5, -5.4], [3, 1.5, 1], BRIGHT_UV),
            cube([-6, 55, -1], [2, 8, 2], BRIGHT_UV),        # Krone
            cube([4, 55, -1], [2, 8, 2], BRIGHT_UV),
            cube([-1, 55, -1], [2, 12, 2], BRIGHT_UV),
        ]),
        bone('halo', [0, 52, 7], parent='head', cubes=[
            cube([round(math.cos(a) * 11 - 1, 2), round(52 + math.sin(a) * 11 - 1, 2), 6.5], [2, 2, 1], BRIGHT_UV)
            for a in [i * math.pi / 6 for i in range(12)]
        ]),
    ]
    for side, s in (('l', 1), ('r', -1)):
        x0 = 12 if s > 0 else -18
        bones.append(bone(f'arm_{side}', [14 * s, 42, 0], parent='body', cubes=[cube([x0, 24, -3], [6, 18, 6])]))
        hx = 12 if s > 0 else -19
        bones.append(bone(f'hand_{side}', [15 * s, 24, 0], parent=f'arm_{side}', cubes=[
            cube([hx, 16, -3.5], [7, 8, 7]),
            cube([hx, 11, -3], [2, 5, 2], BRIGHT_UV),
            cube([hx + 5, 11, -3], [2, 5, 2], BRIGHT_UV),
            cube([hx + 2.5, 10, 1], [2, 6, 2], BRIGHT_UV),
        ]))
        bones.append(bone(f'wing_{side}', [6 * s, 40, 5], parent='body'))
        for i, (length, ang) in enumerate(((36, 0), (30, 22), (24, 44))):
            wx = 6 if s > 0 else -6 - length
            bones.append(bone(f'wing_{side}{i}', [6 * s, 40, 5], parent=f'wing_{side}', rotation=[0, 0, ang * s],
                              cubes=[cube([wx, 39, 5], [length, 2, 1])]))
    bones.append(bone('orb', [0, 30, -16], parent='root', cubes=[cube([-2, 28, -18], [4, 4, 4], BRIGHT_UV)]))
    geo = {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': 'geometry.void_titan', 'texture_width': TEX, 'texture_height': TEX,
                        'visible_bounds_width': 8, 'visible_bounds_height': 8, 'visible_bounds_offset': [0, 2, 0]},
        'bones': bones}]}
    return geo


def kf(points):
    """{zeit: vektor} -> Bedrock-Keyframes (Zeiten als Strings)."""
    return {f'{t:.2f}': v for t, v in points}


def animation():
    L = 12.0
    bones = {
        'root': {
            'position': kf([(0, [0, -30, 0]), (3.0, [0, 0, 0]), (L, [0, 0, 0])]),
            'scale': kf([(0, [0.2, 0.2, 0.2]), (2.0, [1, 1, 1]), (10.5, [1, 1, 1]), (L, [0.01, 0.01, 0.01])]),
        },
        'body': {'rotation': kf([(0, [25, 0, 0]), (3.5, [25, 0, 0]), (4.5, [-5, 0, 0]), (7.0, [-5, 0, 0]),
                                 (7.5, [12, 0, 0]), (8.0, [-12, 0, 0]), (9.0, [0, 0, 0])])},
        'head': {'rotation': kf([(0, [40, 0, 0]), (3.5, [40, 0, 0]), (4.5, [-15, 0, 0]), (6.0, [0, 0, 0]),
                                 (7.4, [10, 0, 0]), (8.0, [-8, 0, 0]), (9.0, [0, 0, 0])])},
        'halo': {'rotation': kf([(0, [0, 0, 0]), (L, [0, 0, 720])]),
                 'scale': kf([(0, [0, 0, 0]), (4.0, [0, 0, 0]), (5.0, [1.2, 1.2, 1.2]), (5.4, [1, 1, 1])])},
        'orb': {'scale': kf([(0, [0, 0, 0]), (4.8, [0, 0, 0]), (7.5, [5, 5, 5]), (7.8, [1.2, 1.2, 1.2]), (L, [1.2, 1.2, 1.2])])},
    }
    for side, s in (('l', 1), ('r', -1)):
        bones[f'arm_{side}'] = {'rotation': kf([(0, [0, 0, 10 * s]), (3.5, [0, 0, 10 * s]), (4.5, [0, 0, -70 * s]),
                                                (5.5, [-80, 0, -25 * s]), (7.5, [-90, 0, -15 * s]),
                                                (8.0, [-60, 0, -35 * s]), (9.0, [-20, 0, -10 * s])])}
        bones[f'hand_{side}'] = {'rotation': kf([(0, [0, 0, 0]), (5.5, [0, 0, 0]), (6.0, [-30, 0, 0]), (8.0, [-30, 0, 0]),
                                                 (9.0, [0, 0, 0])])}
        bones[f'wing_{side}'] = {'rotation': kf([(0, [0, 0, -70 * s]), (4.0, [0, 0, -70 * s]), (5.0, [0, 0, 15 * s]),
                                                 (8.0, [0, 0, 25 * s]), (9.0, [0, 0, 10 * s])])}
    return {'format_version': '1.8.0', 'animations': {'animation.void_titan.cinematic': {
        'loop': 'hold_on_last_frame', 'animation_length': L, 'bones': bones}}}


def textures():
    rnd = random.Random(13)
    base = Image.new('RGBA', (TEX, TEX), (14, 8, 28, 255))
    glow = Image.new('RGBA', (TEX, TEX), (0, 0, 0, 255))
    bp, gp = base.load(), glow.load()
    # Adern: verzweigte Risslinien in Violett und Cyan
    for _ in range(46):
        x, y = rnd.uniform(0, 190), rnd.uniform(0, 190)
        ang = rnd.uniform(0, 2 * math.pi)
        col = (170, 90, 255) if rnd.random() < 0.65 else (90, 230, 255)
        for _ in range(rnd.randint(10, 26)):
            ang += rnd.uniform(-0.6, 0.6)
            x, y = x + math.cos(ang) * 2, y + math.sin(ang) * 2
            ix, iy = int(x), int(y)
            if 0 <= ix < TEX and 0 <= iy < 196:
                gp[ix, iy] = col + (255,)
                bp[ix, iy] = (60, 30, 110, 255)
    for _ in range(300):  # funkelnde Sterne im Koerper
        ix, iy = rnd.randrange(TEX), rnd.randrange(196)
        v = rnd.randint(120, 255)
        gp[ix, iy] = (v, v, 255, 255)
    for y in range(196, TEX):  # gleissende Flaeche fuer Augen, Krone, Kugel, Krallen
        for x in range(TEX):
            f = (y - 196) / (TEX - 196)
            gp[x, y] = (int(200 + 55 * (1 - f)), int(235 + 20 * (1 - f)), 255, 255)
            bp[x, y] = (230, 240, 255, 255)
    return base, glow


def main():
    os.makedirs(os.path.join(ASSETS, 'geo'), exist_ok=True)
    os.makedirs(os.path.join(ASSETS, 'animations'), exist_ok=True)
    os.makedirs(os.path.join(ASSETS, 'textures', 'entity'), exist_ok=True)
    with open(os.path.join(ASSETS, 'geo', 'void_titan.geo.json'), 'w') as f:
        json.dump(model(), f, indent=1)
    with open(os.path.join(ASSETS, 'animations', 'void_titan.animation.json'), 'w') as f:
        json.dump(animation(), f, indent=1)
    base, glow = textures()
    base.save(os.path.join(ASSETS, 'textures', 'entity', 'void_titan.png'))
    glow.save(os.path.join(ASSETS, 'textures', 'entity', 'void_titan_glowmask.png'))
    print('Void titan generated.')


if __name__ == '__main__':
    main()
