<div align="center">

# Cosmic Core

**A multiblock reactor for Minecraft 1.21.1 (NeoForge) that turns starlight into power.**

**[English](#english)** · **[Deutsch](#deutsch)**

</div>

---

<a id="english"></a>

## English

Cosmic Core adds a reactor you build block by block. Once it's complete, the blocks get pulled into its center and a small black hole appears, held in place by a net of glowing particles. Feed it fuel and it makes RF. Push it harder and it makes a lot more RF, but it also gets more unstable. That trade-off is what the whole mod is about.

### Contents

- [What you get](#en-features)
- [Requirements](#en-requirements)
- [Downloads for each Minecraft version](#en-versions)
- [Installation](#en-install)
- [Your first reactor](#en-first)
- [Reactor sizes](#en-sizes)
- [Fuel and stabilizers](#en-fuel)
- [Ports, console and storage](#en-ports)
- [Modules](#en-modules)
- [Asteroids](#en-asteroids)
- [Overdrive and Ultimate mode](#en-overdrive)
- [Commands](#en-commands)
- [Configuration](#en-config)
- [Building from source](#en-build)
- [License](#en-license)

<a id="en-features"></a>

### What you get

- A reactor in four sizes, from a compact 5×5×5 up to the 11×11×11 Ultimate Core.
- A living black hole in the middle, with an accretion disk, jets and a containment field you can actually watch working.
- Power levels you unlock with rare materials, plus Overdrive for players who like living dangerously.
- Asteroids that drift over your base at night. Lasers pull them down and their loot ends up in your storage.
- A floating hologram console that shows output, buffer, fuel and how long your fuel and stabilizers will last.
- The Cosmic Handbook, an in-game book with rotating 3D previews of every reactor size.
- Optional support for JEI, EMI and REI, and an optional cutscene if GeckoLib is installed.

<a id="en-requirements"></a>

### Requirements

| What | Version |
|------|---------|
| Minecraft | 1.21.1 (other versions: see below) |
| NeoForge | 21.1.x |
| Java | 21 (25 from Minecraft 26.1) |
| GeckoLib *(optional)* | 4.9.x, only needed for the Void Titan cutscene |
| JEI / EMI / REI *(optional)* | any version for 1.21.1 |

<a id="en-versions"></a>

### Downloads for each Minecraft version

Every Minecraft version has its own file on the [Releases page](https://github.com/Drakune/cosmiccore/releases/latest). Pick the one that matches your game.

| Minecraft | File | NeoForge (required) | Java | GeckoLib *(optional)* | Recipe viewer *(optional)* | Source |
|---|---|---|---|---|---|---|
| 1.21.1 | `cosmiccore-1.21.1-1.2.1.jar` | 21.1.259 or newer | 21 | 4.9.x | JEI, EMI, REI | `main` |
| 1.21.2 | `cosmiccore-1.21.2-1.2.1.jar` | 21.2.1-beta or newer | 21 | – | REI 17.x | `mc/1.21.2` |
| 1.21.3 | `cosmiccore-1.21.3-1.2.1.jar` | 21.3.100 or newer | 21 | 4.7.x | REI 17.x | `mc/1.21.3` |
| 1.21.4 | `cosmiccore-1.21.4-1.2.1.jar` | 21.4.161 or newer | 21 | 4.8.x | JEI 20.x, REI 18.x | `mc/1.21.4` |
| 1.21.5 | `cosmiccore-1.21.5-1.2.1.jar` | 21.5.101 or newer | 21 | 5.1.x | JEI 21.x, REI 19.x | `mc/1.21.5` |
| 1.21.8 | `cosmiccore-1.21.8-1.2.1.jar` | 21.8.57 or newer | 21 | 5.2.x | JEI 24.x, REI 20.x | `mc/1.21.8` |
| 1.21.10 | `cosmiccore-1.21.10-1.2.1.jar` | 21.10.64 or newer | 21 | 5.3.x | JEI 26.x, REI 21.9.x | `mc/1.21.10` |
| 1.21.11 | `cosmiccore-1.21.11-1.2.1.jar` | 21.11.48 or newer | 21 | 5.4.x | JEI 27.x, REI 21.11.x | `mc/1.21.11` |
| 26.1.2 | `cosmiccore-26.1.2-1.2.1.jar` | 26.1.2.122 or newer | 25 | 5.5.2+ | JEI 29.x, REI 26.1.x | `mc/26.1.2` |
| 26.2 | `cosmiccore-26.2-1.2.1.jar` | 26.2.0.93 or newer | 25 | 5.5.6+ | JEI 30.x, REI 26.2.x | `mc/26.2` |
| 26.3 | `cosmiccore-26.3-1.2.1.jar` | 26.3.0.85 or newer | 25 | 5.5.7+ | JEI 31.x, REI 26.3.x | `mc/26.3` |

- **NeoForge** is the only required dependency.
- **GeckoLib** is only needed for the Void Titan cutscene. Without it the mod works the same, there is just no cutscene. There is no GeckoLib for 1.21.2, so that version has no cutscene.
- **JEI, REI or EMI** show the reactor fuels and stabilizers. Use whichever you like, or none. EMI is only available up to 1.21.1.
- Worlds can be moved to a newer Minecraft version: reactors, settings and stored items carry over.

<a id="en-install"></a>

### Installation

1. Install NeoForge for Minecraft 1.21.1.
2. Drop the Cosmic Core `.jar` into your `mods` folder.
3. On a server, both the server and every player need the mod.

<a id="en-first"></a>

### Your first reactor

1. Craft the **Cosmic Handbook** (a book and star dust). It explains everything in the game, with pictures.
2. Place the **Controller**. A menu opens right away and asks you to pick a size. Choose carefully, this is permanent.
3. Turn on the **Preview**. Ghost blocks show you what goes where, and wrong blocks get a red outline.
4. Build the structure. Every reactor needs at least **one Energy Port and one Item Port** in its casing.
5. Put fuel in the controller, press **Start**, and watch it come to life.

> **Tip:** In creative mode, the **Build** button places the whole structure for you, ports included.

<a id="en-sizes"></a>

### Reactor sizes

| Size | Power levels | Output per level | Buffer per level | Overdrive levels |
|------|:-----------:|:----------------:|:----------------:|:----------------:|
| 5×5×5 | 1–4 | ×0.6 | 1 G RF | 1 |
| 7×7×7 | 1–6 | ×0.85 | 2 G RF | 2 |
| 9×9×9 | 1–8 | ×1.1 | 3 G RF | 3 |
| **11×11×11 (Ultimate)** | 1–10 | ×1.3 | 5 G RF | 3 + Ultimate mode |

Higher levels give you more power, but they also burn fuel faster and heat the core up quicker. You unlock each new level once with an item: star dust for 5×5×5, compressed star dust for 7×7×7, neutron fragments for 9×9×9 and quasar essence for the Ultimate Core.

<a id="en-fuel"></a>

### Fuel and stabilizers

**Fuel** keeps the reaction going. Stronger fuels give much more RF per tick, and some of them run hotter.

| Fuel | RF/t | Burn time |
|------|-----:|----------:|
| Ender Pearl | 400 | 60 s |
| Sculk Catalyst | 600 | 120 s |
| Eye of Ender | 700 | 90 s |
| Echo Shard | 900 | 180 s |
| Star Dust | 1,200 | 120 s |
| Compressed Star Dust | 2,600 | 1,200 s |
| Nether Star | 3,000 | 600 s |
| Neutron Fragment | 8,000 | 300 s *(runs hot)* |
| Quasar Essence | 20,000 | 150 s *(runs very hot)* |

**Stabilizers** (Obsidian, Crying Obsidian, Netherite Scrap) feed a containment field that keeps instability in check. If instability hits 100 %, a 10-second countdown starts. Get it back under 95 % in time, or the core collapses.

The controller shows how long your fuel and stabilizers will last at your current settings. The estimate is updated every second.

<a id="en-ports"></a>

### Ports, console and storage

- **Energy Port:** pushes RF into any cable next to it.
- **Item Port:** pipes and hoppers can feed fuel, stabilizers and modules through it. It also has its own **storage with 27 slots**, where all asteroid loot ends up. Right-click to open it, or pipe the loot out.
- Put the ports **right next to the controller** and the three of them merge into one console with a single wide hologram screen.
- A connector only appears on a port when something is actually plugged into it, like a pipe, cable or chest from any mod.

<a id="en-modules"></a>

### Modules

Modules go into the fold-out tab on the right side of the controller menu.

| Module | What it does |
|--------|--------------|
| Efficiency Module | 15 % less fuel use (up to 4) |
| Stability Module | 12 % less instability (up to 4) |
| Anti-Asteroid Module | keeps asteroids away, can be switched on and off |
| Color Module | gives the core your own color scheme. You can share the scheme as a code. |

<a id="en-asteroids"></a>

### Asteroids

At night, asteroids drift over reactors that are formed. They come in small, medium, large and the rare huge size. With a Star Funnel on the roof and four Cosmic Lasers around it, a tractor beam pulls them down. They burst at the funnel and the fragments spiral into your Item Port. You can find ores, star dust, neutron fragments and, if you're very lucky, quasar essence in them.

<a id="en-overdrive"></a>

### Overdrive and Ultimate mode

**Overdrive Particles** double your output per level, but they also triple the instability. If the reactor breaks apart, the particles are lost.

The **Ultimate Core** has one more step: **Ultimate mode**, which costs four Quasar Overdrive Particles. The first time you activate it, something from another universe notices. If you have GeckoLib installed, you'll see it happen.

<a id="en-commands"></a>

### Commands

| Command | What it does |
|---------|--------------|
| `/cosmiccore cutscene` | Replays the Void Titan cutscene for the nearest awakened Ultimate Core (needs GeckoLib) |

<a id="en-config"></a>

### Configuration

- `config/cosmiccore-common.toml`: balancing, for example buffer sizes, output multiplier, instability, collapse behavior and asteroid spawning.
- `config/cosmiccore-client.toml`: graphics quality (`LOW` / `MEDIUM` / `HIGH`) and detail distance.
- Fuels and stabilizers are data maps, so a datapack can change them:
  `data/cosmiccore/data_maps/item/core_fuel.json` and `core_stabilizer.json`.
- Asteroid loot tables can be changed by datapack too: `data/cosmiccore/loot_table/asteroid/`.

<a id="en-build"></a>

### Building from source

```bash
./gradlew build          # the jar ends up in build/libs/
./gradlew runClient      # starts a test client
./gradlew runData        # regenerates recipes, loot tables, tags and data maps
python3 tools/generate_resources.py   # regenerates textures, models and language files (needs Pillow)
```

You can run the test client with a recipe viewer (`-Pviewer=jei` or `-Pviewer=emi`) or without GeckoLib (`-Pgeckolib=off`).

The other Minecraft versions live in their own branches (`mc/1.21.2` … `mc/26.3`), see the table above.

<a id="en-license"></a>

### License

[MIT License](LICENSE) · © 2026 DR4KUN3

You may use, modify and share the mod and its code, including in modpacks and your own projects, as long as the copyright notice is kept.

<p align="right"><a href="#english">Back to top ↑</a> · <a href="#deutsch">Deutsch ↓</a></p>

---

<a id="deutsch"></a>

## Deutsch

Mit Cosmic Core baust du einen Reaktor Block für Block zusammen. Ist er fertig, werden die Blöcke in seine Mitte gesaugt und es entsteht ein kleines schwarzes Loch, gehalten von einem Netz aus leuchtenden Partikeln. Gib ihm Brennstoff und es erzeugt RF. Drehst du es höher, kommt deutlich mehr RF heraus, aber es wird auch instabiler. Genau darum geht es in der ganzen Mod.

### Inhalt

- [Was dich erwartet](#de-features)
- [Voraussetzungen](#de-requirements)
- [Downloads für jede Minecraft-Version](#de-versions)
- [Installation](#de-install)
- [Dein erster Reaktor](#de-first)
- [Reaktorgrößen](#de-sizes)
- [Brennstoff und Stabilisatoren](#de-fuel)
- [Ports, Konsole und Lager](#de-ports)
- [Module](#de-modules)
- [Asteroiden](#de-asteroids)
- [Overdrive und Ultimate-Modus](#de-overdrive)
- [Befehle](#de-commands)
- [Konfiguration](#de-config)
- [Selbst bauen](#de-build)
- [Lizenz](#de-license)

<a id="de-features"></a>

### Was dich erwartet

- Ein Reaktor in vier Größen, vom kompakten 5×5×5 bis zum 11×11×11 großen Ultimativen Kern.
- Ein lebendiges schwarzes Loch in der Mitte, mit Akkretionsscheibe, Jets und einem Eindämmungsfeld, dem du beim Arbeiten zusehen kannst.
- Leistungsstufen, die du mit seltenen Materialien freischaltest, und Overdrive für alle, die gern etwas riskieren.
- Asteroiden, die nachts über deine Basis ziehen. Laser holen sie herunter, und die Beute landet in deinem Lager.
- Eine schwebende Hologramm-Konsole mit Leistung, Puffer, Brennstoff und der Restlaufzeit von Brennstoff und Stabilisatoren.
- Das Kosmische Handbuch, ein Buch im Spiel mit drehbaren 3D-Ansichten jeder Reaktorgröße.
- Optionale Unterstützung für JEI, EMI und REI und eine optionale Zwischensequenz, wenn GeckoLib installiert ist.

<a id="de-requirements"></a>

### Voraussetzungen

| Was | Version |
|-----|---------|
| Minecraft | 1.21.1 (other versions: see below) |
| NeoForge | 21.1.x |
| Java | 21 (25 from Minecraft 26.1) |
| GeckoLib *(optional)* | 4.9.x, nur für die Void-Titan-Zwischensequenz |
| JEI / EMI / REI *(optional)* | jede Version für 1.21.1 |

<a id="de-versions"></a>

### Downloads für jede Minecraft-Version

Für jede Minecraft-Version gibt es eine eigene Datei auf der [Release-Seite](https://github.com/Drakune/cosmiccore/releases/latest). Nimm die, die zu deinem Spiel passt.

| Minecraft | Datei | NeoForge (Pflicht) | Java | GeckoLib *(optional)* | Rezeptanzeige *(optional)* | Quellcode |
|---|---|---|---|---|---|---|
| 1.21.1 | `cosmiccore-1.21.1-1.2.1.jar` | 21.1.259 oder neuer | 21 | 4.9.x | JEI, EMI, REI | `main` |
| 1.21.2 | `cosmiccore-1.21.2-1.2.1.jar` | 21.2.1-beta oder neuer | 21 | – | REI 17.x | `mc/1.21.2` |
| 1.21.3 | `cosmiccore-1.21.3-1.2.1.jar` | 21.3.100 oder neuer | 21 | 4.7.x | REI 17.x | `mc/1.21.3` |
| 1.21.4 | `cosmiccore-1.21.4-1.2.1.jar` | 21.4.161 oder neuer | 21 | 4.8.x | JEI 20.x, REI 18.x | `mc/1.21.4` |
| 1.21.5 | `cosmiccore-1.21.5-1.2.1.jar` | 21.5.101 oder neuer | 21 | 5.1.x | JEI 21.x, REI 19.x | `mc/1.21.5` |
| 1.21.8 | `cosmiccore-1.21.8-1.2.1.jar` | 21.8.57 oder neuer | 21 | 5.2.x | JEI 24.x, REI 20.x | `mc/1.21.8` |
| 1.21.10 | `cosmiccore-1.21.10-1.2.1.jar` | 21.10.64 oder neuer | 21 | 5.3.x | JEI 26.x, REI 21.9.x | `mc/1.21.10` |
| 1.21.11 | `cosmiccore-1.21.11-1.2.1.jar` | 21.11.48 oder neuer | 21 | 5.4.x | JEI 27.x, REI 21.11.x | `mc/1.21.11` |
| 26.1.2 | `cosmiccore-26.1.2-1.2.1.jar` | 26.1.2.122 oder neuer | 25 | 5.5.2+ | JEI 29.x, REI 26.1.x | `mc/26.1.2` |
| 26.2 | `cosmiccore-26.2-1.2.1.jar` | 26.2.0.93 oder neuer | 25 | 5.5.6+ | JEI 30.x, REI 26.2.x | `mc/26.2` |
| 26.3 | `cosmiccore-26.3-1.2.1.jar` | 26.3.0.85 oder neuer | 25 | 5.5.7+ | JEI 31.x, REI 26.3.x | `mc/26.3` |

- **NeoForge** ist die einzige Pflicht-Abhängigkeit.
- **GeckoLib** braucht es nur für die Void-Titan-Zwischensequenz. Ohne funktioniert die Mod genauso, es gibt dann nur keine Zwischensequenz. Für 1.21.2 gibt es kein GeckoLib, dort fehlt die Zwischensequenz also.
- **JEI, REI oder EMI** zeigen die Brennstoffe und Stabilisatoren des Reaktors. Nimm, was dir gefällt, oder keine. EMI gibt es nur bis 1.21.1.
- Welten lassen sich in eine neuere Minecraft-Version mitnehmen: Reaktoren, Einstellungen und gelagerte Items bleiben erhalten.

<a id="de-install"></a>

### Installation

1. NeoForge für Minecraft 1.21.1 installieren.
2. Die `.jar` von Cosmic Core in den Ordner `mods` legen.
3. Auf einem Server brauchen sowohl der Server als auch alle Spieler die Mod.

<a id="de-first"></a>

### Dein erster Reaktor

1. Stell das **Kosmische Handbuch** her (Buch und Sternenstaub). Es erklärt alles direkt im Spiel, mit Bildern.
2. Setz den **Controller**. Sofort öffnet sich ein Menü, in dem du die Größe wählst. Überleg gut, die Wahl ist endgültig.
3. Schalte die **Vorschau** ein. Geisterblöcke zeigen dir, was wohin gehört, und falsche Blöcke werden rot umrandet.
4. Bau die Struktur. Jeder Reaktor braucht mindestens **einen Energie-Port und einen Item-Port** in der Hülle.
5. Leg Brennstoff in den Controller, drück **Start** und sieh zu, wie er zum Leben erwacht.

> **Tipp:** Im Kreativmodus setzt der Knopf **Bauen** die komplette Struktur für dich, samt Ports.

<a id="de-sizes"></a>

### Reaktorgrößen

| Größe | Leistungsstufen | Leistung je Stufe | Puffer je Stufe | Overdrive-Stufen |
|-------|:---------------:|:-----------------:|:---------------:|:----------------:|
| 5×5×5 | 1–4 | ×0,6 | 1 G RF | 1 |
| 7×7×7 | 1–6 | ×0,85 | 2 G RF | 2 |
| 9×9×9 | 1–8 | ×1,1 | 3 G RF | 3 |
| **11×11×11 (Ultimativ)** | 1–10 | ×1,3 | 5 G RF | 3 + Ultimate-Modus |

Höhere Stufen bringen mehr Leistung, verbrauchen aber auch mehr Brennstoff und heizen den Kern schneller auf. Jede neue Stufe schaltest du einmalig mit einem Gegenstand frei: Sternenstaub beim 5×5×5, verdichteter Sternenstaub beim 7×7×7, Neutronenstern-Fragmente beim 9×9×9 und Quasar-Essenz beim Ultimativen Kern.

<a id="de-fuel"></a>

### Brennstoff und Stabilisatoren

**Brennstoff** hält die Reaktion am Laufen. Stärkere Brennstoffe liefern deutlich mehr RF pro Tick, manche laufen dafür heißer.

| Brennstoff | RF/t | Brenndauer |
|------------|-----:|-----------:|
| Enderperle | 400 | 60 s |
| Sculk-Katalysator | 600 | 120 s |
| Enderauge | 700 | 90 s |
| Echoscherbe | 900 | 180 s |
| Sternenstaub | 1.200 | 120 s |
| Verdichteter Sternenstaub | 2.600 | 1.200 s |
| Netherstern | 3.000 | 600 s |
| Neutronenstern-Fragment | 8.000 | 300 s *(läuft heiß)* |
| Quasar-Essenz | 20.000 | 150 s *(läuft sehr heiß)* |

**Stabilisatoren** (Obsidian, Weinender Obsidian, Netheritschrott) speisen ein Eindämmungsfeld, das die Instabilität im Zaum hält. Erreicht sie 100 %, läuft ein Countdown von 10 Sekunden. Bringst du sie rechtzeitig unter 95 %, ist der Kern gerettet, sonst kollabiert er.

Der Controller zeigt dir, wie lange Brennstoff und Stabilisatoren bei den aktuellen Einstellungen noch reichen. Die Schätzung wird jede Sekunde neu berechnet.

<a id="de-ports"></a>

### Ports, Konsole und Lager

- **Energie-Port:** gibt RF an jedes angrenzende Kabel ab.
- **Item-Port:** Über ihn können Rohre und Trichter Brennstoff, Stabilisatoren und Module einfüllen. Außerdem hat er ein eigenes **Lager mit 27 Plätzen**, in dem die ganze Asteroiden-Beute landet. Öffnen per Rechtsklick, oder die Beute per Rohr abziehen.
- Stehen die Ports **direkt neben dem Controller**, verschmelzen die drei zu einer Konsole mit einem gemeinsamen, breiten Hologramm-Bildschirm.
- Ein Anschlussstück erscheint an einem Port erst, wenn wirklich etwas angeschlossen ist, etwa ein Rohr, Kabel oder eine Truhe aus einer beliebigen Mod.

<a id="de-modules"></a>

### Module

Module kommen in den ausklappbaren Tab rechts am Controller-Menü.

| Modul | Was es tut |
|-------|------------|
| Effizienz-Modul | 15 % weniger Brennstoffverbrauch (bis zu 4) |
| Stabilitäts-Modul | 12 % weniger Instabilität (bis zu 4) |
| Anti-Asteroiden-Modul | hält Asteroiden fern, lässt sich ein- und ausschalten |
| Farb-Modul | gibt dem Kern dein eigenes Farbschema. Du kannst das Schema als Code teilen. |

<a id="de-asteroids"></a>

### Asteroiden

Nachts ziehen Asteroiden über fertig gebaute Reaktoren, in den Größen klein, mittel, groß und selten riesig. Mit einem Sternentrichter auf dem Dach und vier Kosmos-Lasern drumherum holt ein Traktorstrahl sie herunter. Am Trichter zerbrechen sie, und die Bruchstücke spiralen in deinen Item-Port. Darin stecken Erze, Sternenstaub, Neutronenstern-Fragmente und mit viel Glück Quasar-Essenz.

<a id="de-overdrive"></a>

### Overdrive und Ultimate-Modus

**Overdrive-Partikel** verdoppeln die Leistung pro Stufe, verdreifachen aber auch die Instabilität. Zerfällt der Reaktor, sind die Partikel verloren.

Der **Ultimative Kern** hat noch eine Stufe mehr: den **Ultimate-Modus**, der vier Quasar-Overdrive-Partikel kostet. Wenn du ihn zum ersten Mal einschaltest, bemerkt dich etwas aus einem anderen Universum. Mit GeckoLib siehst du es auch.

<a id="de-commands"></a>

### Befehle

| Befehl | Was er tut |
|--------|------------|
| `/cosmiccore cutscene` | Spielt die Void-Titan-Zwischensequenz für den nächsten erwachten Ultimativen Kern erneut ab (braucht GeckoLib) |

<a id="de-config"></a>

### Konfiguration

- `config/cosmiccore-common.toml`: Balancing, zum Beispiel Puffergrößen, Leistungsfaktor, Instabilität, Verhalten beim Kollaps und das Erscheinen von Asteroiden.
- `config/cosmiccore-client.toml`: Grafikqualität (`LOW` / `MEDIUM` / `HIGH`) und Detail-Entfernung.
- Brennstoffe und Stabilisatoren sind Data Maps, ein Datapack kann sie also ändern:
  `data/cosmiccore/data_maps/item/core_fuel.json` und `core_stabilizer.json`.
- Auch die Beute der Asteroiden lässt sich per Datapack ändern: `data/cosmiccore/loot_table/asteroid/`.

<a id="de-build"></a>

### Selbst bauen

```bash
./gradlew build          # die Jar liegt danach in build/libs/
./gradlew runClient      # startet einen Test-Client
./gradlew runData        # erzeugt Rezepte, Loot-Tabellen, Tags und Data Maps neu
python3 tools/generate_resources.py   # erzeugt Texturen, Modelle und Sprachdateien neu (braucht Pillow)
```

Den Test-Client kannst du mit einem Rezept-Viewer starten (`-Pviewer=jei` oder `-Pviewer=emi`) oder ohne GeckoLib (`-Pgeckolib=off`).

Die anderen Minecraft-Versionen liegen in eigenen Zweigen (`mc/1.21.2` … `mc/26.3`), siehe Tabelle oben.

<a id="de-license"></a>

### Lizenz

[MIT-Lizenz](LICENSE) · © 2026 DR4KUN3

Du darfst die Mod und ihren Code nutzen, verändern und weitergeben, auch in Modpacks und eigenen Projekten, solange der Urheberrechtshinweis erhalten bleibt.

<p align="right"><a href="#deutsch">Nach oben ↑</a> · <a href="#english">English ↑</a></p>
