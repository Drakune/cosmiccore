# Cosmic Core - Kerngenerator (NeoForge 1.21.1)

7x7x7-Multiblock mit animierter Singularität, der aus kosmischem Brennstoff RF erzeugt.

## Bauen (wichtig: Das Projekt ist hier NICHT kompiliert worden)

1. JDK 21 installieren.
2. Im Projektordner: `./gradlew build` (Windows: `gradlew.bat build`)
3. Die fertige Jar liegt danach unter `build/libs/cosmiccore-1.0.0.jar`.
   Testen mit `./gradlew runClient`. Auf einen Server gehört die Jar in `mods/` (Client und Server brauchen sie).

Falls der Build Fehler meldet: komplette Ausgabe kopieren, dann lassen sie sich schnell beheben.

## Aufbau (Ansicht von unten nach oben, Reihe 1 = vorne)

```
H = Leerengehäuse / Energie-Port / Item-Port     C = wie H, hier sitzt der Controller (Mitte einer Bodenkante)
F = Runenrahmen   G = Fokusglas   R = Resonanzspule   X = Fokuskristall   K = Singularitätskern
a = Luft (muss frei bleiben)      . = egal

y=0 Boden        y=1 / y=5        y=2 / y=4        y=3 Äquator      y=6 Decke
HHHCHHH          F.....F          F.....F          F.....F          HHHHHHH
HHHHHHH          .aaaaa.          .aaaaa.          .aaRaa.          HGGGGGH
HHHHHHH          .aaaaa.          .aaaaa.          .aaaaa.          HGHHHGH
CHHHHHC          .aaXaa.          .aaaaa.          .RaKaR.          HGHHHGH
HHHHHHH          .aaaaa.          .aaaaa.          .aaaaa.          HGHHHGH
HHHHHHH          .aaaaa.          .aaaaa.          .aaRaa.          HGGGGGH
HHHCHHH          F.....F          F.....F          F.....F          HHHHHHH
```

125 Blöcke, die Seiten sind offen, damit man die Animation sieht. Der Controller richtet sich beim Bilden
automatisch nach außen aus. Rechtsklick auf den Controller (ohne Brennstoff in der Hand) zeigt Status bzw.
den ersten Baufehler (mit Koordinate, erwartetem Block und Partikel an der Stelle).

## Spielmechanik

- **Zündung:** Brennstoff (Rechtsklick auf Controller oder per Trichter/Rohr in den Item-Port) startet den Kern.
- **Resonanz:** Die Leistung steigt über ~30 s von 20 % auf 100 % (`rampUpTicks`).
- **Kosmische Boni:** Overworld nachts +25 % (Vollmond +50 %), The End dauerhaft +40 %. Neumond und End machen den Kern aber auch instabiler.
- **Instabilität:** Jede Verbrennung erhöht sie, stärkere Brennstoffe schneller. Stabilisatoren (Obsidian, Weinender Obsidian, Netherit-Schrott)
  werden automatisch verbraucht und halten sie unten. Ab 70 % wird der Kern rot und unruhig, bei 100 % **kollabiert** er:
  Dunkelheit/Übelkeit/Schaden im Umkreis, Pause (`collapseCooldownTicks`). Blockzerstörung ist standardmäßig aus.
- **Ausgabe:** Energie-Ports schieben RF aktiv in angrenzende Blöcke und bieten die Energy-Capability an (nur Entnahme).

## Balancing ohne Neukompilieren

- `config/cosmiccore-common.toml` (Kapazität, Port-Rate, globaler `outputMultiplier`, Instabilitätsrate, Kollaps ...)
- Data Maps (auch per Datapack überschreibbar):
  `data/cosmiccore/data_maps/item/core_fuel.json` und `core_stabilizer.json`. Fuel-Tooltips zeigen die Werte im Spiel an.

## Texturen/Modelle anpassen

`python3 tools/generate_resources.py` (braucht Pillow) erzeugt alle Texturen, Animationen, Modelle, Rezepte, Loot-Tables und
Sprachdateien neu. Die Singularität (Kugel, Ringe, Sterne, Blitze) wird prozedural in
`client/SingularityCoreRenderer.java` gezeichnet.
