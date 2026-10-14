#!/usr/bin/env python3
"""
Erzeugt die Assets des Void-Titanen fuer die Cutscene des Ultimate-Modus (GeckoLib, optional):
  assets/cosmiccore/geo/void_titan.geo.json              - Modell (Bedrock-Format, ~120 Knochen)
  assets/cosmiccore/animations/void_titan.animation.json - Kinofilm-Animation (14 s, weiche Uebergaenge)
  assets/cosmiccore/textures/entity/void_titan.png        - Grundtextur (der Koerper wird als Sternenfeld gezeichnet)
  assets/cosmiccore/textures/entity/void_titan_glowmask.png - leuchtende Adern, Augen, Hoerner, Herz, Kugel

Aufbau (im Stil grosser Boss-Gegner): Brustkorb aus Rippen mit pulsierendem Herz, gegliederte Arme mit Armschienen und
Fingern (je zwei Glieder), Schulterpanzer aus drei Platten, Kopf mit Brauenwulst, vier Augen, beweglichem Kiefer und
gebogenen Hoernern, Heiligenschein aus Scherben, ein Mantel aus Stoffbahnen und sechs Ranken statt Beinen.
Mantel, Ranken, Finger und Herz bewegt zusaetzlich der Code (prozedurale Sekundaerbewegung).

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
BODY = [0, 0]      # Bereich mit Adern
BRIGHT = [0, 200]  # gleissend helle Flaeche

# muessen zu VoidTitanModel.java passen
TENDRILS, TENDRIL_SEGS = 6, 5
CAPE_STRIPS, CAPE_SEGS = 5, 4
FINGERS, FINGER_SEGS = 4, 2


def r(v):
    return [round(x, 3) for x in v]


def cube(origin, size, uv=None, rotation=None, pivot=None, inflate=0):
    c = {'origin': r(origin), 'size': r(size), 'uv': uv or BODY}
    if rotation:
        c['rotation'] = rotation
        c['pivot'] = r(pivot or [origin[0] + size[0] / 2, origin[1] + size[1] / 2, origin[2] + size[2] / 2])
    if inflate:
        c['inflate'] = inflate
    return c


BONES = []


def bone(name, pivot, cubes=(), parent=None, rotation=None):
    b = {'name': name, 'pivot': r(pivot)}
    if parent:
        b['parent'] = parent
    if rotation:
        b['rotation'] = r(rotation)
    if cubes:
        b['cubes'] = list(cubes)
    BONES.append(b)
    return name


def build():
    BONES.clear()
    bone('root', [0, 0, 0])

    # Ranken statt Beinen: sechs Ketten aus fuenf Gliedern, nach unten duenner werdend
    bone('tendrils', [0, 24, 0], parent='root')
    for k in range(TENDRILS):
        a = k * 2 * math.pi / TENDRILS
        x, z = math.cos(a) * 4.5, math.sin(a) * 4.5
        parent, y = 'tendrils', 24.0
        for i in range(TENDRIL_SEGS):
            w = 4.2 - i * 0.6
            length = 7.0 - i * 0.5
            rot = [-12 * math.sin(a), 0, 12 * math.cos(a)] if i == 0 else [-6 * math.sin(a), 0, 6 * math.cos(a)]
            uv = BRIGHT if i == TENDRIL_SEGS - 1 else BODY
            parent = bone(f't{k}_{i}', [x, y, z], parent=parent, rotation=rot,
                          cubes=[cube([x - w / 2, y - length, z - w / 2], [w, length, w], uv)])
            y -= length

    # Becken und Unterleib
    bone('body', [0, 28, 0], parent='root', cubes=[
        cube([-6, 24, -4], [12, 6, 8]),
        cube([-5, 30, -3.5], [10, 4, 7]),
        cube([-6, 34, -4], [12, 4, 8]),
        cube([-7, 26, -4.5], [14, 2, 9], inflate=0.2),  # Guertelplatte
    ])

    # Brustkorb: Wirbelsaeule, Rippen, Brustbein, Kragen
    chest = [
        cube([-1.5, 38, 3], [3, 22, 3]),
        cube([-1, 40, -6], [2, 18, 1.5]),
        cube([-12, 58, -5], [24, 4, 10]),
    ]
    for i in range(5):
        y = 40 + i * 3.6
        spread = 1.0 + 0.12 * i
        chest += [
            cube([-10 * spread, y, -5], [4, 1.4, 9]),
            cube([6 * spread, y, -5], [4, 1.4, 9]),
            cube([-7 * spread, y, -5.6], [5.5, 1.4, 1.4]),
            cube([1.5 * spread, y, -5.6], [5.5, 1.4, 1.4]),
        ]
    for i in range(5):  # Rueckenstacheln
        chest.append(cube([-1, 42 + i * 3.8, 5.5], [2, 2, 4 + i * 0.8], BRIGHT if i == 4 else BODY,
                          rotation=[-35, 0, 0], pivot=[0, 43 + i * 3.8, 5.5]))
    bone('chest', [0, 40, 0], parent='body', cubes=chest)
    bone('heart', [0, 48, 0], parent='chest', cubes=[cube([-3, 45, -3], [6, 6, 6], BRIGHT),
                                                     cube([-2, 44, -2], [4, 8, 4], BRIGHT)])

    for side, s in (('l', 1), ('r', -1)):
        def X(x0, w):  # gespiegelte x-Position eines Wuerfels
            return x0 if s > 0 else -x0 - w
        # Schulterpanzer aus drei gestaffelten Platten
        bone(f'pauldron_{side}', [13 * s, 60, 0], parent='chest', cubes=[
            cube([X(10, 9), 58, -6], [9, 4, 12], rotation=[0, 0, -15 * s], pivot=[13 * s, 60, 0]),
            cube([X(11, 9), 55, -6.5], [9, 3, 13], rotation=[0, 0, -28 * s], pivot=[13 * s, 57, 0]),
            cube([X(12, 9), 52, -7], [9, 3, 14], rotation=[0, 0, -40 * s], pivot=[13 * s, 54, 0]),
            cube([X(15, 2), 61, -1], [2, 6, 2], BRIGHT, rotation=[0, 0, -20 * s], pivot=[16 * s, 61, 0]),
        ])
        bone(f'arm_{side}', [14 * s, 58, 0], parent='chest', cubes=[cube([X(11.5, 6), 44, -3], [6, 14, 6])])
        bone(f'forearm_{side}', [14.5 * s, 44, 0], parent=f'arm_{side}', cubes=[
            cube([X(11, 7), 30, -3.5], [7, 14, 7]),
            cube([X(10.5, 8), 35, -4], [8, 2, 8], inflate=0.1),
            cube([X(10.5, 8), 39, -4], [8, 2, 8], inflate=0.1),
            cube([X(17.5, 1.5), 32, -1], [1.5, 9, 2], BRIGHT),  # leuchtende Klinge an der Armschiene
        ])
        bone(f'hand_{side}', [14.5 * s, 30, 0], parent=f'forearm_{side}', cubes=[cube([X(11.5, 6), 25, -3], [6, 5, 6])])
        for j in range(FINGERS):
            fx = 11.8 + j * 1.55
            parent, y = f'hand_{side}', 25.0
            for g in range(FINGER_SEGS):
                h = 4.5 if g == 0 else 4.0
                w = 1.4 - g * 0.2
                parent = bone(f'f{side}{j}_{g}', [X(fx, w) + w / 2, y, -1.5], parent=parent,
                              cubes=[cube([X(fx, w), y - h, -2.2], [w, h, w], BRIGHT if g == FINGER_SEGS - 1 else BODY)])
                y -= h
        bone(f'thumb_{side}', [X(11.5, 1.6) + 0.8, 27, -3], parent=f'hand_{side}',
             rotation=[-20, 0, 25 * s], cubes=[cube([X(11.5, 1.6), 23, -3.5], [1.6, 4, 1.6], BRIGHT)])

    # Hals und Kopf
    bone('neck', [0, 62, 0], parent='chest', cubes=[cube([-3, 62, -3], [6, 5, 6]), cube([-2.5, 64, 2.5], [5, 3, 2])])
    bone('head', [0, 67, -1], parent='neck', cubes=[
        cube([-6, 67, -7], [12, 10, 11]),
        cube([-6.5, 73, -7.6], [13, 2, 3]),        # Brauenwulst
        cube([-4, 67, -9], [8, 5, 3]),             # Gesichtsplatte
        cube([-5, 71, -8.6], [2.6, 1, 1], BRIGHT),  # vier Augen
        cube([2.4, 71, -8.6], [2.6, 1, 1], BRIGHT),
        cube([-4.2, 69.3, -9.3], [2, 0.8, 1], BRIGHT),
        cube([2.2, 69.3, -9.3], [2, 0.8, 1], BRIGHT),
        cube([-1, 77, -2], [2, 7, 2], BRIGHT),     # Stirnhorn
    ])
    jaw = [cube([-5, 64, -9], [10, 3, 9])]
    for i in range(4):
        jaw.append(cube([-4 + i * 2.4, 66.5, -9.2], [1, 1.2, 1], BRIGHT))
    bone('jaw', [0, 68, -1], parent='head', cubes=jaw)
    for side, s in (('l', 1), ('r', -1)):  # gebogene Hoerner aus vier Gliedern
        parent, px, py = 'head', 5 * s, 75.0
        for i in range(4):
            w = 2.6 - i * 0.5
            length = 5.5 - i * 0.6
            parent = bone(f'horn_{side}{i}', [px, py, -1], parent=parent, rotation=[18, 0, -22 * s],
                          cubes=[cube([px - w / 2, py, -1 - w / 2], [w, length, w], BRIGHT if i == 3 else BODY)])
            py += length
    # Heiligenschein aus Scherben hinter dem Kopf
    bone('halo', [0, 72, 8], parent='head', cubes=[
        cube([math.cos(a) * 14 - 1, 72 + math.sin(a) * 14 - 1.5, 7.5], [2, 3, 1], BRIGHT,
             rotation=[0, 0, math.degrees(a) - 90], pivot=[math.cos(a) * 14, 72 + math.sin(a) * 14, 8])
        for a in [i * math.pi / 7 for i in range(14)]])

    # Mantel aus Stoffbahnen am Ruecken
    bone('cape', [0, 60, 5], parent='chest')
    for s_ in range(CAPE_STRIPS):
        x = -10 + s_ * 4.2
        parent, y = 'cape', 60.0
        for i in range(CAPE_SEGS):
            length = 9.0 - i * 0.8
            parent = bone(f'cape{s_}_{i}', [x + 2, y, 5], parent=parent, rotation=[8 if i == 0 else 4, 0, 0],
                          cubes=[cube([x, y - length, 5], [4, length, 0.8], BRIGHT if i == CAPE_SEGS - 1 else BODY)])
            y -= length

    # Energiekugel ueber dem Kopf (lokal oben = beim Titan auf dem Planeten: Richtung Erde)
    bone('orb', [0, 92, 0], parent='root', cubes=[cube([-2, 90, -2], [4, 4, 4], BRIGHT)])

    return {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': 'geometry.void_titan', 'texture_width': TEX, 'texture_height': TEX,
                        'visible_bounds_width': 10, 'visible_bounds_height': 10, 'visible_bounds_offset': [0, 3, 0]},
        'bones': BONES}]}


def kf(points, easing='easeInOutSine'):
    """[(zeit, vektor[, easing])] -> GeckoLib-Keyframes mit weichen Uebergaengen."""
    out = {}
    for p in points:
        t, v = p[0], p[1]
        e = p[2] if len(p) > 2 else easing
        out[f'{t:.2f}'] = {'vector': r(v), 'easing': e}
    return out


def animation():
    L = 14.0
    b = {
        # aus der Kruste des Planeten empor, am Ende zerfaellt er zu Sternenstaub
        'root': {
            'position': kf([(0, [0, -70, 0]), (3.5, [0, 0, 0], 'easeOutCubic'), (L, [0, 0, 0])]),
            'scale': kf([(0, [1, 1, 1]), (12.5, [1, 1, 1]), (L, [0.01, 0.01, 0.01], 'easeInCubic')]),
        },
        'body': {'rotation': kf([(0, [35, 0, 0]), (3.5, [30, 0, 0]), (4.5, [-12, 0, 0], 'easeOutBack'), (6.0, [-4, 0, 0]),
                                 (8.8, [-6, 0, 0]), (9.1, [16, 0, 0], 'easeOutQuad'), (9.6, [-8, 0, 0]), (11.0, [0, 0, 0])])},
        'chest': {'rotation': kf([(0, [15, 0, 0]), (4.0, [15, 0, 0]), (4.6, [-10, 0, 0], 'easeOutBack'), (6.0, [0, 0, 0]),
                                  (8.8, [-8, 0, 0]), (9.1, [10, 0, 0]), (10.0, [0, 0, 0])])},
        'neck': {'rotation': kf([(0, [25, 0, 0]), (4.0, [25, 0, 0]), (4.6, [-20, 0, 0], 'easeOutBack'), (6.0, [-8, 0, 0]),
                                 (10.0, [0, 0, 0])])},
        'head': {'rotation': kf([(0, [35, 0, 0]), (4.0, [35, 0, 0]), (4.6, [-30, 0, 0], 'easeOutBack'), (5.6, [-18, 0, 0]),
                                 (8.8, [-28, 0, 0]), (9.2, [-40, 0, 0]), (10.5, [0, 0, 0])])},
        'jaw': {'rotation': kf([(0, [0, 0, 0]), (4.4, [0, 0, 0]), (4.7, [38, 0, 0], 'easeOutBack'), (5.7, [32, 0, 0]),
                                (6.1, [4, 0, 0]), (8.9, [4, 0, 0]), (9.1, [42, 0, 0], 'easeOutQuad'), (9.9, [0, 0, 0])])},
        'halo': {'scale': kf([(0, [0, 0, 0]), (4.4, [0, 0, 0]), (5.2, [1.3, 1.3, 1.3], 'easeOutBack'), (5.7, [1, 1, 1])])},
        'orb': {'scale': kf([(0, [0, 0, 0]), (5.8, [0, 0, 0]), (8.8, [6, 6, 6], 'easeInQuad'), (9.1, [1.5, 1.5, 1.5]),
                             (L, [1.5, 1.5, 1.5])])},
        'cape': {'rotation': kf([(0, [10, 0, 0]), (4.4, [10, 0, 0]), (4.8, [55, 0, 0], 'easeOutBack'), (6.0, [25, 0, 0]),
                                 (9.1, [60, 0, 0], 'easeOutQuad'), (10.5, [15, 0, 0])])},
        'tendrils': {'rotation': kf([(0, [0, 0, 0]), (4.5, [0, 0, 0]), (5.0, [-8, 0, 0]), (L, [0, 0, 0])])},
    }
    for side, s in (('l', 1), ('r', -1)):
        b[f'arm_{side}'] = {'rotation': kf([
            (0, [25, 0, -12 * s]), (4.0, [25, 0, -12 * s]), (4.8, [-30, 0, -78 * s], 'easeOutBack'),
            (6.0, [-168, 0, -22 * s], 'easeInOutCubic'), (8.8, [-176, 0, -14 * s]), (9.1, [-135, 0, -42 * s], 'easeOutQuad'),
            (10.8, [-25, 0, -16 * s])])}
        b[f'forearm_{side}'] = {'rotation': kf([
            (0, [-45, 0, 0]), (4.0, [-45, 0, 0]), (4.8, [-12, 0, 0]), (6.0, [-28, 0, 0]), (8.8, [-35, 0, 0]),
            (9.1, [0, 0, 0]), (10.8, [-20, 0, 0])])}
        b[f'hand_{side}'] = {'rotation': kf([(0, [0, 0, 0]), (5.5, [0, 0, 0]), (6.2, [-25, 0, 10 * s]), (8.8, [-30, 0, 10 * s]),
                                             (9.2, [10, 0, 0]), (10.8, [0, 0, 0])])}
        b[f'pauldron_{side}'] = {'rotation': kf([(0, [0, 0, 0]), (4.5, [0, 0, 0]), (4.8, [0, 0, 18 * s], 'easeOutBack'),
                                                 (6.0, [0, 0, 6 * s]), (9.1, [0, 0, 20 * s]), (10.5, [0, 0, 0])])}
    return {'format_version': '1.8.0', 'animations': {'animation.void_titan.cinematic': {
        'loop': 'hold_on_last_frame', 'animation_length': L, 'bones': b}}}


def textures():
    rnd = random.Random(13)
    base = Image.new('RGBA', (TEX, TEX), (14, 8, 28, 255))
    glow = Image.new('RGBA', (TEX, TEX), (0, 0, 0, 255))
    bp, gp = base.load(), glow.load()
    # Adern: verzweigte Risslinien in Violett und Cyan
    for _ in range(70):
        x, y = rnd.uniform(0, 190), rnd.uniform(0, 190)
        ang = rnd.uniform(0, 2 * math.pi)
        col = (170, 90, 255) if rnd.random() < 0.65 else (90, 230, 255)
        for _ in range(rnd.randint(10, 30)):
            ang += rnd.uniform(-0.6, 0.6)
            x, y = x + math.cos(ang) * 2, y + math.sin(ang) * 2
            ix, iy = int(x), int(y)
            if 0 <= ix < TEX and 0 <= iy < 196:
                gp[ix, iy] = col + (255,)
                bp[ix, iy] = (60, 30, 110, 255)
    for _ in range(400):  # funkelnde Sterne im Koerper
        ix, iy = rnd.randrange(TEX), rnd.randrange(196)
        v = rnd.randint(120, 255)
        gp[ix, iy] = (v, v, 255, 255)
    for y in range(196, TEX):  # gleissende Flaeche
        for x in range(TEX):
            f = (y - 196) / (TEX - 196)
            gp[x, y] = (int(190 + 65 * (1 - f)), int(225 + 30 * (1 - f)), 255, 255)
            bp[x, y] = (230, 240, 255, 255)
    return base, glow


def main():
    for d in ('geo', 'animations', os.path.join('textures', 'entity')):
        os.makedirs(os.path.join(ASSETS, d), exist_ok=True)
    geo = build()
    with open(os.path.join(ASSETS, 'geo', 'void_titan.geo.json'), 'w') as f:
        json.dump(geo, f, indent=1)
    with open(os.path.join(ASSETS, 'animations', 'void_titan.animation.json'), 'w') as f:
        json.dump(animation(), f, indent=1)
    base, glow = textures()
    base.save(os.path.join(ASSETS, 'textures', 'entity', 'void_titan.png'))
    glow.save(os.path.join(ASSETS, 'textures', 'entity', 'void_titan_glowmask.png'))
    print(f'Void titan generated ({len(geo["minecraft:geometry"][0]["bones"])} bones).')


if __name__ == '__main__':
    main()
