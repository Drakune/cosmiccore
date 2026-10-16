#!/usr/bin/env python3
"""
Erzeugt die Assets des Void-Titanen fuer die Cutscene des Ultimate-Modus (GeckoLib, optional):
  assets/cosmiccore/geckolib/models/void_titan.geo.json              - Oberkoerper (Becken, Rumpf, Kopf, Arme, Fluegel)
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
FINGERS, FINGER_SEGS = 4, 2  # muss zu VoidTitanModel.java passen

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
    """Gepanzerter Kriegsherr der Leere: breite Schultern, schmale Taille, langer Schweif (separates Modell)."""
    BONES.clear()
    bone('root', [0, 0, 0])
    bone('body', [0, 0, 0], parent='root', cubes=[
        cube([-9, 0, -6], [18, 5, 12], ARMOR),                                         # Guertel
        cube([-7, -6, -7], [6, 7, 2], ARMOR, rotation=[10, 0, 0], pivot=[-4, 1, -6]),  # Schurzplatten vorn
        cube([1, -6, -7], [6, 7, 2], ARMOR, rotation=[10, 0, 0], pivot=[4, 1, -6]),
        cube([-11, -5, -4], [3, 6, 8], ARMOR), cube([8, -5, -4], [3, 6, 8], ARMOR),     # Seitenplatten
        cube([-6, -14, 5], [12, 14, 1], CLOTH, rotation=[10, 0, 0], pivot=[0, 0, 5]),  # Lendentuch hinten
        cube([-5, -5, -4], [10, 6, 8], FLESH),                                         # Schweifansatz
        cube([-6, 5, -5], [12, 8, 10], FLESH),                                         # Taille
    ] + [cube([-5 + c * 6, 6 + r * 2.5, -5.6], [4, 2, 1], ARMOR) for r in range(3) for c in range(2)])
    chest = [
        cube([-9, 13, -6], [18, 7, 12], FLESH),
        cube([-11, 20, -7], [22, 9, 14], FLESH),
        cube([-11, 19, -8.5], [10, 9, 3], ARMOR, rotation=[0, -8, 0], pivot=[-6, 23, -7]),   # Brustplatten
        cube([1, 19, -8.5], [10, 9, 3], ARMOR, rotation=[0, 8, 0], pivot=[6, 23, -7]),
        cube([-2, 21, -9.5], [4, 5, 2], GLOW),                                               # Herzkern
        cube([-8, 29, -6], [16, 4, 12], ARMOR),                                              # Halsberge
        cube([-12, 14, -4], [2, 10, 8], ARMOR), cube([10, 14, -4], [2, 10, 8], ARMOR),       # Flankenplatten
        cube([-12.5, 16, -1], [1, 6, 2], GLOW), cube([11.5, 16, -1], [1, 6, 2], GLOW),       # Leuchtadern
    ]
    for i in range(5):  # Rueckengrat mit Stacheln
        chest.append(cube([-2.5, 13 + i * 3.5, 6], [5, 3, 2], ARMOR))
        chest.append(cube([-1, 14 + i * 3.5, 8], [2, 2, 3 + i], HORN, rotation=[-35, 0, 0], pivot=[0, 15 + i * 3.5, 8]))
    bone('chest', [0, 13, 0], parent='body', cubes=chest)
    bone('core', [0, 23.5, -10], parent='chest')  # Markierung: Herzkern (fuer das Leuchten)

    for side, s in (('l', 1), ('r', -1)):
        def X(x0, w):
            return x0 if s > 0 else -x0 - w
        bone(f'pauldron_{side}', [13 * s, 30, 0], parent='chest', cubes=[
            cube([X(10, 11), 26, -8], [11, 6, 16], ARMOR, rotation=[0, 0, -12 * s], pivot=[13 * s, 29, 0]),
            cube([X(11, 10), 23, -8.5], [10, 4, 17], ARMOR, rotation=[0, 0, -24 * s], pivot=[13 * s, 25, 0]),
            cube([X(12, 9), 20.5, -9], [9, 3, 18], ARMOR, rotation=[0, 0, -36 * s], pivot=[13 * s, 22, 0]),
            cube([X(10, 11), 25, -8], [11, 1, 16], GLOW, rotation=[0, 0, -12 * s], pivot=[13 * s, 29, 0]),
            cube([X(14, 3), 32, -3], [3, 10, 3], HORN, rotation=[0, 0, -20 * s], pivot=[15.5 * s, 32, -1.5]),
            cube([X(18, 2), 30, 1], [2, 7, 2], HORN, rotation=[0, 0, -35 * s], pivot=[19 * s, 30, 2]),
        ])
        bone(f'arm_{side}', [15 * s, 28, 0], parent='chest', cubes=[
            cube([X(12.5, 6), 16, -3.5], [6, 11, 7], FLESH),
            cube([X(12, 7), 13, -4.5], [7, 4, 9], ARMOR),
            cube([X(14.5, 2), 13, 4], [2, 2, 5], HORN, rotation=[-30, 0, 0], pivot=[15.5 * s, 14, 4]),
        ])
        bone(f'forearm_{side}', [15.5 * s, 14, 0], parent=f'arm_{side}', cubes=[
            cube([X(11.5, 8), 1, -4], [8, 13, 8], ARMOR),
            cube([X(19, 1), 3, -1], [1, 9, 2], GLOW),
        ] + [cube([X(19.5, 2), 4 + i * 3.5, 1], [2, 2, 3], HORN, rotation=[0, 25 * s, 0], pivot=[20 * s, 5 + i * 3.5, 1])
             for i in range(3)])
        bone(f'hand_{side}', [15.5 * s, 1, 0], parent=f'forearm_{side}', cubes=[cube([X(12, 7), -4, -3.5], [7, 5, 7], ARMOR)])
        for j in range(FINGERS):
            fx = 12 + j * 2.4
            parent, y = f'hand_{side}', -4
            for g in range(FINGER_SEGS):
                parent = bone(f'f{side}{j}_{g}', [X(fx, 2) + 1, y, -2], parent=parent,
                              cubes=[cube([X(fx, 2), y - 5, -3], [2, 5, 2], ARMOR if g == 0 else HORN)])
                y -= 5
        bone(f'thumb_{side}', [X(12, 2) + 1, -2, -3], parent=f'hand_{side}', rotation=[-25, 0, 30 * s],
             cubes=[cube([X(12, 2), -7, -4], [2, 5, 2], HORN)])
        # Fluegel: fuenf Knochenspeichen mit Haeuten
        bone(f'wing_{side}', [6 * s, 28, 7], parent='chest')
        for i, (length, ang) in enumerate(((34, 5), (30, 22), (26, 40), (22, 58), (16, 76))):
            wx = 6 if s > 0 else -6 - length
            bone(f'wing_{side}{i}', [6 * s, 28, 7], parent=f'wing_{side}', rotation=[0, 0, ang * s], cubes=[
                cube([wx, 27, 7], [length, 2, 2], HORN),
                cube([wx + (2 if s > 0 else 0), 21, 8], [length - 2, 6, 1], CLOTH),
            ])

    bone('neck', [0, 32, -1], parent='chest', cubes=[cube([-3.5, 32, -4.5], [7, 5, 7], FLESH),
                                                      cube([-4, 33, -5], [8, 2, 8], ARMOR)])
    bone('head', [0, 36, -2], parent='neck', cubes=[
        cube([-5, 36, -6], [10, 9, 10], ARMOR),        # Hinterkopf
        cube([-4.5, 36, -9], [9, 8, 3], ARMOR),        # Gesichtsmaske
        cube([-5.5, 42, -9.5], [11, 2, 3], ARMOR),     # Brauenwulst
        cube([-6, 36, -7], [2, 5, 6], ARMOR), cube([4, 36, -7], [2, 5, 6], ARMOR),  # Wangen
        cube([-4, 40, -9.6], [3, 1, 1], GLOW, rotation=[0, 0, 12], pivot=[-2.5, 40.5, -9.1]),   # Augen
        cube([1, 40, -9.6], [3, 1, 1], GLOW, rotation=[0, 0, -12], pivot=[2.5, 40.5, -9.1]),
        cube([-1, 45, -7], [2, 6, 2], GLOW), cube([-3.5, 44.5, -6], [1, 4, 1], GLOW), cube([2.5, 44.5, -6], [1, 4, 1], GLOW),
    ])
    bone('eye_l', [2.5, 40.5, -9.8], parent='head')   # Markierungen fuer das Augenleuchten
    bone('eye_r', [-2.5, 40.5, -9.8], parent='head')
    bone('jaw', [0, 37, -5], parent='head', cubes=[
        cube([-3.5, 33, -9], [7, 3, 6], ARMOR),
        cube([-2.5, 36, -9.2], [1, 2, 1], GLOW), cube([1.5, 36, -9.2], [1, 2, 1], GLOW),
    ])
    for side, s in (('l', 1), ('r', -1)):  # zurueckgeschwungene Hoerner
        parent, px, py = 'head', 4.5 * s, 44
        for i in range(5):
            w = 3 - i * 0.45
            length = 5 - i * 0.5
            rot = [-35, 0, -25 * s] if i == 0 else [-15, 0, -6 * s]
            parent = bone(f'horn_{side}{i}', [px, py, -4], parent=parent, rotation=rot,
                          cubes=[cube([px - w / 2, py, -4 - w / 2], [w, length, w], GLOW if i == 4 else HORN)])
            py += length
    bone('halo', [0, 41, 6], parent='head', cubes=[
        cube([math.cos(a) * 12 - 1, 41 + math.sin(a) * 12 - 1.5, 5.5], [2, 3, 1], GLOW,
             rotation=[0, 0, math.degrees(a) - 90], pivot=[math.cos(a) * 12, 41 + math.sin(a) * 12, 6])
        for a in [i * math.pi / 6 for i in range(12)]])
    bone('orb', [0, 22, -30], parent='root')  # Markierung: Energiekugel zwischen den Haenden
    return geometry('geometry.void_titan', list(BONES), 14)


# ----------------------------------------------------------------- Schweifsegment (Laengsachse nach unten)

def tail_model():
    BONES.clear()
    bone('segment', [0, 0, 0], cubes=[
        cube([-4, -11, -4], [8, 11, 8], FLESH),
        cube([-5, -3, -5], [10, 3, 10], ARMOR),
        cube([-4.5, -8, -4.5], [9, 2, 9], ARMOR),
        cube([-1, -7, 4], [2, 3, 6], HORN, rotation=[-30, 0, 0], pivot=[0, -6, 4]),
        cube([-2, -9, -4.6], [4, 6, 1], GLOW),
        cube([-9, -9, -0.5], [4, 6, 1], CLOTH, rotation=[0, 0, 15], pivot=[-5, -6, 0]),
        cube([5, -9, -0.5], [4, 6, 1], CLOTH, rotation=[0, 0, -15], pivot=[5, -6, 0]),
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
    for d in (os.path.join('geckolib', 'models'), os.path.join('geckolib', 'animations'), os.path.join('textures', 'entity')):
        os.makedirs(os.path.join(ASSETS, d), exist_ok=True)
    ATLAS = Atlas()
    body = body_model()
    tail = tail_model()
    with open(os.path.join(ASSETS, 'geckolib', 'models', 'void_titan.geo.json'), 'w') as f:
        json.dump(body, f, indent=1)
    with open(os.path.join(ASSETS, 'geckolib', 'models', 'void_titan_tail.geo.json'), 'w') as f:
        json.dump(tail, f, indent=1)
    with open(os.path.join(ASSETS, 'geckolib', 'animations', 'void_titan.animation.json'), 'w') as f:
        json.dump(animation(), f, indent=1)
    paint(ATLAS.regions).save(os.path.join(ASSETS, 'textures', 'entity', 'void_titan.png'))
    old = os.path.join(ASSETS, 'textures', 'entity', 'void_titan_glowmask.png')
    if os.path.exists(old):
        os.remove(old)  # nur noch eine Textur
    print(f'Void titan generated ({len(body["minecraft:geometry"][0]["bones"])} bones, {len(ATLAS.regions)} cubes).')


if __name__ == '__main__':
    main()
