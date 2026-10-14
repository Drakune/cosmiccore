#!/usr/bin/env python3
"""
Erzeugt die Assets des Void-Titanen fuer die Cutscene des Ultimate-Modus (GeckoLib, optional):
  assets/cosmiccore/geo/void_titan.geo.json              - Oberkoerper (Becken, Rumpf, Kopf, Arme, Fluegel)
  assets/cosmiccore/geo/void_titan_tail.geo.json         - ein Schweifsegment (wird je Teil der Entity gezeichnet)
  assets/cosmiccore/animations/void_titan.animation.json - Kinofilm-Animation (14 s, weiche Uebergaenge)
  assets/cosmiccore/textures/entity/void_titan.png        - EINE bemalte Textur fuer beide Modelle

Jeder Wuerfel bekommt einen eigenen Bereich im Textur-Atlas und wird flaechenweise nach seinem Material bemalt
(Panzer, Leerenfleisch, Horn, Leuchten, Stoff) - mit Kanten, Lichtkante oben, Nieten, Rissen und Rauschen.

Aufruf: python3 tools/generate_titan.py (wird auch von generate_resources.py aufgerufen)
"""
import json
import math
import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'cosmiccore')
TEX = 512
FINGERS, FINGER_SEGS = 3, 2  # muss zu VoidTitanModel.java passen

ARMOR, FLESH, HORN, GLOW, CLOTH = 'armor', 'flesh', 'horn', 'glow', 'cloth'


class Atlas:
    """Einfacher Regal-Packer fuer Box-UVs."""

    def __init__(self):
        self.x = self.y = self.row = 0
        self.regions = []  # (u, v, w, h, d, material)

    def alloc(self, w, h, d, material):
        bw, bh = 2 * (d + w), d + h
        if self.x + bw > TEX:
            self.x, self.y, self.row = 0, self.y + self.row + 1, 0
        u, v = self.x, self.y
        self.x += bw + 1
        self.row = max(self.row, bh)
        if self.y + bh > TEX:
            raise RuntimeError('Textur-Atlas voll')
        self.regions.append((u, v, w, h, d, material))
        return [u, v]


ATLAS = None
BONES = []


def cube(origin, size, material, rotation=None, pivot=None):
    w, h, d = (int(math.ceil(s)) for s in size)
    c = {'origin': origin, 'size': size, 'uv': ATLAS.alloc(w, h, d, material)}
    if rotation:
        c['rotation'] = rotation
        c['pivot'] = pivot or [origin[0] + size[0] / 2, origin[1] + size[1] / 2, origin[2] + size[2] / 2]
    return c


def bone(name, pivot, cubes=(), parent=None, rotation=None):
    b = {'name': name, 'pivot': pivot}
    if parent:
        b['parent'] = parent
    if rotation:
        b['rotation'] = rotation
    if cubes:
        b['cubes'] = list(cubes)
    BONES.append(b)
    return name


def geometry(identifier, bones, bounds):
    return {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': identifier, 'texture_width': TEX, 'texture_height': TEX,
                        'visible_bounds_width': bounds, 'visible_bounds_height': bounds, 'visible_bounds_offset': [0, 2, 0]},
        'bones': bones}]}


# ----------------------------------------------------------------- Oberkoerper

def body_model():
    BONES.clear()
    bone('root', [0, 0, 0])
    bone('body', [0, 0, 0], parent='root', cubes=[
        cube([-8, 0, -6], [16, 6, 12], ARMOR),       # Hueftpanzer
        cube([-5, -4, -4], [10, 4, 8], ARMOR),       # Schweifansatz
        cube([-6, 6, -5], [12, 6, 10], FLESH),       # Taille
        cube([-9, 2, -7], [3, 3, 3], HORN),          # Hueftstacheln
        cube([6, 2, -7], [3, 3, 3], HORN),
    ])
    chest = [
        cube([-10, 12, -7], [20, 16, 14], FLESH),    # Rumpf
        cube([-11, 16, -8], [9, 12, 3], ARMOR),      # Brustplatten (links/rechts)
        cube([2, 16, -8], [9, 12, 3], ARMOR),
        cube([-2, 18, -9], [4, 8, 2], GLOW),         # Herzkern zwischen den Platten
        cube([-8, 12, -8], [16, 4, 2], ARMOR),       # Bauchplatte
        cube([-13, 28, -7], [26, 4, 14], ARMOR),     # Kragen
    ]
    for i in range(4):                               # Rueckenplatten mit Stacheln
        chest.append(cube([-2, 14 + i * 4, 7], [4, 3, 3], ARMOR))
        chest.append(cube([-1, 15 + i * 4, 10], [2, 2, 3 + i], HORN, rotation=[-30, 0, 0], pivot=[0, 16 + i * 4, 10]))
    bone('chest', [0, 12, 0], parent='body', cubes=chest)

    for side, s in (('l', 1), ('r', -1)):
        def X(x0, w):
            return x0 if s > 0 else -x0 - w
        bone(f'pauldron_{side}', [13 * s, 30, 0], parent='chest', cubes=[
            cube([X(10, 10), 26, -8], [10, 8, 16], ARMOR),
            cube([X(10, 10), 25, -8], [10, 1, 16], GLOW),
            cube([X(16, 3), 34, -2], [3, 9, 3], HORN, rotation=[0, 0, -18 * s], pivot=[17.5 * s, 34, 0]),
            cube([X(13, 2), 34, 2], [2, 6, 2], HORN, rotation=[-15, 0, -10 * s], pivot=[14 * s, 34, 3]),
        ])
        bone(f'arm_{side}', [14 * s, 28, 0], parent='chest', cubes=[
            cube([X(11, 6), 14, -3], [6, 12, 6], FLESH),
            cube([X(10, 8), 12, -4], [8, 4, 8], ARMOR),
        ])
        bone(f'forearm_{side}', [14 * s, 14, 0], parent=f'arm_{side}', cubes=[
            cube([X(10, 8), 2, -4], [8, 12, 8], ARMOR),
            cube([X(18, 2), 2, -1], [2, 10, 2], GLOW),  # Klinge an der Armschiene
        ])
        bone(f'hand_{side}', [14 * s, 2, 0], parent=f'forearm_{side}', cubes=[cube([X(11, 6), -3, -3], [6, 5, 6], FLESH)])
        for j in range(FINGERS):
            fx = 11 + j * 2
            parent, y = f'hand_{side}', -3
            for g in range(FINGER_SEGS):
                h = 4 if g == 0 else 3
                parent = bone(f'f{side}{j}_{g}', [X(fx, 2) + 1, y, -1], parent=parent,
                              cubes=[cube([X(fx, 2), y - h, -2], [2, h, 2], HORN)])
                y -= h
        # Fluegel: drei Knochenspeichen mit Haeuten aus Leerenstoff
        bone(f'wing_{side}', [7 * s, 28, 7], parent='chest')
        for i, (length, ang) in enumerate(((30, 10), (26, 35), (20, 60))):
            wx = 7 if s > 0 else -7 - length
            bone(f'wing_{side}{i}', [7 * s, 28, 7], parent=f'wing_{side}', rotation=[0, 0, ang * s], cubes=[
                cube([wx, 27, 7], [length, 2, 2], HORN),
                cube([wx, 19, 8], [length, 8, 1], CLOTH),
            ])

    bone('neck', [0, 32, 0], parent='chest', cubes=[cube([-3, 32, -3], [6, 5, 6], FLESH)])
    bone('head', [0, 37, -1], parent='neck', cubes=[
        cube([-6, 37, -8], [12, 10, 12], ARMOR),      # Helmschaedel
        cube([-5, 41, -9], [4, 1, 1], GLOW),          # Augenschlitze
        cube([1, 41, -9], [4, 1, 1], GLOW),
        cube([-6, 44, -9], [12, 2, 2], ARMOR),        # Brauenwulst
        cube([-4, 37, -10], [8, 4, 3], FLESH),        # Maul
        cube([-1, 47, -6], [2, 4, 10], HORN),         # Kamm
    ])
    bone('jaw', [0, 38, -6], parent='head', cubes=[
        cube([-4, 34, -10], [8, 3, 6], ARMOR),
        cube([-3, 37, -10], [1, 1, 1], GLOW), cube([2, 37, -10], [1, 1, 1], GLOW),
    ])
    for side, s in (('l', 1), ('r', -1)):
        parent, px, py = 'head', 5 * s, 46
        for i in range(4):
            w = 3 - i * 0.5
            length = 6 - i
            parent = bone(f'horn_{side}{i}', [px, py, -2], parent=parent, rotation=[20, 0, -24 * s],
                          cubes=[cube([px - w / 2, py, -2 - w / 2], [w, length, w], HORN)])
            py += length
    # Energiekugel vor der Brust (vom Code bzw. der Animation skaliert)
    bone('orb', [0, 30, -26], parent='root', cubes=[cube([-2, 28, -28], [4, 4, 4], GLOW)])
    return geometry('geometry.void_titan', list(BONES), 12)


# ----------------------------------------------------------------- Schweifsegment (Laengsachse nach unten)

def tail_model():
    BONES.clear()
    bone('segment', [0, 0, 0], cubes=[
        cube([-4, -11, -4], [8, 11, 8], FLESH),      # Wirbelkoerper
        cube([-5, -5, -5], [10, 4, 10], ARMOR),      # Panzerring
        cube([-1, -8, 4], [2, 3, 5], HORN, rotation=[-25, 0, 0], pivot=[0, -7, 4]),  # Rueckenstachel
        cube([-2, -12, -2], [4, 1, 4], GLOW),        # leuchtendes Gelenk
        cube([-8, -8, -1], [3, 5, 1], CLOTH),        # Seitenflossen
        cube([5, -8, -1], [3, 5, 1], CLOTH),
    ])
    return geometry('geometry.void_titan_tail', list(BONES), 4)


# ----------------------------------------------------------------- Animation

def kf(points, easing='easeInOutSine'):
    out = {}
    for p in points:
        out[f'{p[0]:.2f}'] = {'vector': p[1], 'easing': p[2] if len(p) > 2 else easing}
    return out


def animation():
    L = 14.0
    b = {
        # zusammengekauert - erwacht (3-4.5 s) - bruellt (4.5-5.5 s) - laedt (6.5-9.5 s) - schleudert (9.5 s)
        'body': {'rotation': kf([(0, [30, 0, 0]), (3.0, [26, 0, 0]), (4.5, [-10, 0, 0], 'easeOutBack'), (6.0, [-2, 0, 0]),
                                 (9.4, [-12, 0, 0]), (9.7, [18, 0, 0], 'easeOutQuad'), (10.4, [-6, 0, 0]), (12.0, [0, 0, 0])])},
        'chest': {'rotation': kf([(0, [20, 0, 0]), (3.2, [20, 0, 0]), (4.6, [-14, 0, 0], 'easeOutBack'), (6.0, [0, 0, 0]),
                                  (9.4, [-10, 0, 0]), (9.7, [12, 0, 0]), (11.0, [0, 0, 0])])},
        'neck': {'rotation': kf([(0, [30, 0, 0]), (3.2, [30, 0, 0]), (4.6, [-25, 0, 0], 'easeOutBack'), (6.0, [-5, 0, 0])])},
        'head': {'rotation': kf([(0, [35, 0, 0]), (3.4, [35, 0, 0]), (4.7, [-30, 0, 0], 'easeOutBack'), (5.6, [-12, 0, 0]),
                                 (9.4, [-5, 0, 0]), (9.8, [-20, 0, 0]), (11.0, [0, 0, 0])])},
        'jaw': {'rotation': kf([(0, [0, 0, 0]), (4.5, [0, 0, 0]), (4.8, [40, 0, 0], 'easeOutBack'), (5.6, [34, 0, 0]),
                                (6.0, [4, 0, 0]), (9.6, [4, 0, 0]), (9.8, [42, 0, 0]), (10.6, [0, 0, 0])])},
        'orb': {'scale': kf([(0, [0, 0, 0]), (6.5, [0, 0, 0]), (9.5, [6, 6, 6], 'easeInQuad'), (9.8, [0.01, 0.01, 0.01]),
                             (L, [0.01, 0.01, 0.01])])},
    }
    for side, s in (('l', 1), ('r', -1)):
        b[f'arm_{side}'] = {'rotation': kf([
            (0, [30, 0, -25 * s]), (3.2, [30, 0, -25 * s]), (4.6, [-25, 0, -80 * s], 'easeOutBack'),
            (6.5, [-95, 0, -30 * s], 'easeInOutCubic'), (9.4, [-110, 0, -20 * s]), (9.7, [-80, 0, -12 * s], 'easeOutQuad'),
            (11.5, [-15, 0, -15 * s])])}
        b[f'forearm_{side}'] = {'rotation': kf([
            (0, [-70, 0, 0]), (3.2, [-70, 0, 0]), (4.6, [-15, 0, 0]), (6.5, [-35, 0, 0]), (9.4, [-45, 0, 0]),
            (9.7, [0, 0, 0]), (11.5, [-20, 0, 0])])}
        b[f'wing_{side}'] = {'rotation': kf([
            (0, [0, 0, -75 * s]), (3.5, [0, 0, -75 * s]), (4.7, [0, 0, 20 * s], 'easeOutBack'), (6.0, [0, 0, 5 * s]),
            (9.7, [0, 0, 28 * s]), (11.0, [0, 0, 8 * s])])}
        b[f'pauldron_{side}'] = {'rotation': kf([(0, [0, 0, 0]), (4.5, [0, 0, 0]), (4.8, [0, 0, 16 * s], 'easeOutBack'),
                                                 (6.0, [0, 0, 4 * s]), (9.7, [0, 0, 18 * s]), (11.0, [0, 0, 0])])}
    return {'format_version': '1.8.0', 'animations': {
        'animation.void_titan.cinematic': {'loop': 'hold_on_last_frame', 'animation_length': L, 'bones': b},
        'animation.void_titan.idle': {'loop': True, 'animation_length': 4.0, 'bones': {
            'chest': {'rotation': kf([(0, [0, 0, 0]), (2.0, [-3, 0, 0]), (4.0, [0, 0, 0])])}}}}}


# ----------------------------------------------------------------- Textur

PALETTE = {
    ARMOR: dict(base=(30, 28, 50), light=(78, 72, 118), dark=(12, 10, 22), accent=(120, 80, 210)),
    FLESH: dict(base=(40, 18, 62), light=(70, 34, 100), dark=(18, 6, 30), accent=(110, 235, 255)),
    HORN: dict(base=(176, 168, 202), light=(226, 220, 244), dark=(70, 62, 96), accent=(255, 255, 255)),
    GLOW: dict(base=(150, 240, 255), light=(235, 255, 255), dark=(70, 170, 230), accent=(255, 255, 255)),
    CLOTH: dict(base=(48, 22, 80), light=(80, 44, 120), dark=(22, 8, 40), accent=(220, 170, 70)),
}


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def paint_face(px, rnd, x0, y0, w, h, mat, face):
    p = PALETTE[mat]
    if w <= 0 or h <= 0:
        return
    top = face == 'up'
    for y in range(h):
        for x in range(w):
            # Grundton mit senkrechtem Verlauf und Rauschen
            f = y / max(1, h - 1)
            c = lerp(p['light'] if top else p['base'], p['dark'], 0.15 + 0.45 * f if not top else 0.1)
            n = rnd.uniform(-1, 1)
            c = tuple(max(0, min(255, int(v + n * (10 if mat != GLOW else 4)))) for v in c)
            edge = x == 0 or y == 0 or x == w - 1 or y == h - 1
            if mat == ARMOR:
                if y == 0 or x == 0:
                    c = p['light']                                 # Lichtkante
                elif edge:
                    c = p['dark']
                elif (x + y) % 7 == 0 and rnd.random() < 0.15:
                    c = lerp(c, p['light'], 0.4)                  # Kratzer
                if not edge and w > 5 and h > 5 and (x, y) in ((2, 2), (w - 3, 2), (2, h - 3), (w - 3, h - 3)):
                    c = p['light']                                 # Nieten
                if y == h - 1:
                    c = p['accent']                                # violetter Saum
            elif mat == FLESH:
                if edge:
                    c = p['dark']
            elif mat == HORN:
                c = lerp(p['light'], p['dark'], f * 0.8) if face != 'up' else p['light']
                if x == 0:
                    c = lerp(c, p['dark'], 0.4)
            elif mat == GLOW:
                if edge:
                    c = p['dark']
            elif mat == CLOTH:
                if y >= h - 1:
                    c = p['accent']                                # Goldsaum
                elif (x * 3 + y) % 5 == 0:
                    c = lerp(c, p['dark'], 0.5)                    # Gewebe
            px[x0 + x, y0 + y] = c + (255,)
    if mat == FLESH and w > 3 and h > 3:
        # leuchtende Risse durch das Leerenfleisch
        for _ in range(max(1, (w * h) // 30)):
            x, y = rnd.randrange(w), rnd.randrange(h)
            for _ in range(rnd.randint(3, 8)):
                if 0 <= x < w and 0 <= y < h:
                    px[x0 + x, y0 + y] = p['accent'] + (255,)
                x += rnd.choice((-1, 0, 1))
                y += rnd.choice((0, 1))


def paint(regions):
    rnd = random.Random(7)
    img = Image.new('RGBA', (TEX, TEX), (0, 0, 0, 0))
    px = img.load()
    for (u, v, w, h, d, mat) in regions:
        faces = {
            'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d),
            'west': (u, v + d, d, h), 'north': (u + d, v + d, w, h),
            'east': (u + d + w, v + d, d, h), 'south': (u + 2 * d + w, v + d, w, h),
        }
        for face, (fx, fy, fw, fh) in faces.items():
            paint_face(px, rnd, fx, fy, fw, fh, mat, face)
    return img


def main():
    global ATLAS
    for d in ('geo', 'animations', os.path.join('textures', 'entity')):
        os.makedirs(os.path.join(ASSETS, d), exist_ok=True)
    ATLAS = Atlas()
    body = body_model()
    tail = tail_model()
    with open(os.path.join(ASSETS, 'geo', 'void_titan.geo.json'), 'w') as f:
        json.dump(body, f, indent=1)
    with open(os.path.join(ASSETS, 'geo', 'void_titan_tail.geo.json'), 'w') as f:
        json.dump(tail, f, indent=1)
    with open(os.path.join(ASSETS, 'animations', 'void_titan.animation.json'), 'w') as f:
        json.dump(animation(), f, indent=1)
    paint(ATLAS.regions).save(os.path.join(ASSETS, 'textures', 'entity', 'void_titan.png'))
    old = os.path.join(ASSETS, 'textures', 'entity', 'void_titan_glowmask.png')
    if os.path.exists(old):
        os.remove(old)  # nur noch eine Textur
    print(f'Void titan generated ({len(body["minecraft:geometry"][0]["bones"])} bones, {len(ATLAS.regions)} cubes).')


if __name__ == '__main__':
    main()
