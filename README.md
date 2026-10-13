# Cosmic Core - Kerngenerator (NeoForge 1.21.1)

Multiblock-Reaktor (5x5x5, 7x7x7 oder 9x9x9) mit animierter Singularität, der aus kosmischem Brennstoff RF erzeugt.
Leistungsstufe hoch = mehr Strom, aber mehr Verbrauch und mehr Risiko.

## Bauen (wichtig: Das Projekt ist hier NICHT kompiliert worden)

1. JDK 21 installieren.
2. Im Projektordner: `./gradlew build` (Windows: `gradlew.bat build`)
3. Die fertige Jar liegt danach unter `build/libs/cosmiccore-1.0.0.jar`.
   Testen mit `./gradlew runClient`. Auf einen Server gehört die Jar in `mods/` (Client und Server brauchen sie).

Falls der Build Fehler meldet: komplette Ausgabe kopieren, dann lassen sie sich schnell beheben.

## Controller & GUI

- **Kosmisches Handbuch** (Buch + Sternenstaub): erklärt Aufbau, Stufen, Instabilität und Kosmos-Boni im Spiel.

- **Platzieren:** Direkt nach dem Platzieren öffnet sich die (einmalige) Wahl der Baugröße 5x5x5 / 7x7x7 / 9x9x9
  (angezeigt wird die RF/t-Spanne mit Sternenstaub von Stufe 1 bis zur Maximalstufe).
- **Gebildete Struktur:** Die Blöcke werden (als Animation) in den Kern gesaugt, daraus entsteht ein schwarzes Loch mit
  Akkretionsscheibe, Photonenring und Jets, zusammengehalten von einem Netz aus Partikeln, die über leuchtende Fesseln
  verbunden sind. Der Resonanzschild-Emitter unter dem
  Kern projiziert ein Eindämmungsfeld um das Loch, dessen Stärke den Stabilisator-Vorrat zeigt.
- **Rechtsklick:** GUI mit Brennstoff- und Stabilisator-Slot (frei befüllen und wieder herausnehmen), Balken für
  Energie / Instabilität / Resonanz, Leistungsstufe (-/+), Zünden/Stopp, Bauvorschau und (nur Kreativ) **Bauen**.
- **Schleichen + Rechtsklick:** Status im Chat inkl. erstem Baufehler (mit Partikel an der Stelle).
- **Bauvorschau:** zeigt fehlende Blöcke als halbtransparente Geister, falsche Blöcke rot umrandet.
- **Bauen (Kreativ):** setzt die komplette Struktur in Blickrichtung des Controllers (inkl. Energie- und Item-Port).

| Größe | Stufen | Leistung/Stufe | Instabilität/Stufe | Stabilisator-Wirkung | Overdrive |
|-------|-------:|---------------:|-------------------:|---------------------:|----------:|
| 5x5x5 | 1–10 | x0,6 | x1,0 | x1,0 | 1 |
| 7x7x7 | 1–10 | x1,0 | x0,8 | x1,25 | 2 |
| 9x9x9 | 1–10 | x1,6 | x0,6 | x1,6 | 3 |

**Energiepuffer:** 250 Mio. RF pro Leistungsstufe (Stufe 10 = 2,5 Mrd. RF, `energyPerLevel` in der Config).
Ist der Puffer voll, pausiert der Reaktor (kein Brennstoffverbrauch), bis Energie abfließt.

Stufe s: Leistung x s^1,3, Brennstoffverbrauch x s, Instabilität x s^1,35 (Exponenten in der Config).
Der Kern wird mit der Stufe sichtbar größer und färbt sich mit steigender Instabilität von Blau über Orange nach Rot.

## Aufbau (Ansicht von unten nach oben, Reihe 1 = vorne)

```
H = Leerengehäuse / Energie-Port / Item-Port     C = wie H, hier sitzt der Controller (Mitte einer Bodenkante)
F = Runenrahmen   G = Fokusglas   R = Resonanzschild-Emitter   X = Fokuskristall   K = Singularitätskern
a = Luft (muss frei bleiben)      . = egal      ? = R in y=1, X in y=5

y=0 Boden    y=1 (R) / y=5 (X)  y=2 / y=4        y=3 Äquator      y=6 Decke
HHHCHHH          F.....F          F.....F          F.....F          HHHHHHH
HHHHHHH          .aaaaa.          .aaaaa.          .aaaaa.          HGGGGGH
HHHHHHH          .aaaaa.          .aaaaa.          .aaaaa.          HGHHHGH
CHHHHHC          .aa?aa.          .aaaaa.          .aaKaa.          HGHHHGH
HHHHHHH          .aaaaa.          .aaaaa.          .aaaaa.          HGHHHGH
HHHHHHH          .aaaaa.          .aaaaa.          .aaaaa.          HGGGGGH
HHHCHHH          F.....F          F.....F          F.....F          HHHHHHH
HHHHHHH          .aaaaa.          .aaaaa.          .aaRaa.          HGGGGGH
HHHHHHH          .aaaaa.          .aaaaa.          .aaaaa.          HGHHHGH
CHHHHHC          .aaXaa.          .aaaaa.          .RaKaR.          HGHHHGH
HHHHHHH          .aaaaa.          .aaaaa.          .aaaaa.          HGHHHGH
HHHHHHH          .aaaaa.          .aaaaa.          .aaRaa.          HGGGGGH
HHHCHHH          F.....F          F.....F          F.....F          HHHHHHH
```

Der **Resonanzschild-Emitter** sitzt unter dem Kern, der **Fokuskristall** darüber (7x7x7: 2 Blöcke Abstand,
9x9x9: 3 Blöcke). **5x5x5:** Die Kammer ist zu eng - Emitter und Kristall sitzen in der Mitte von Boden bzw. Decke.
Die Bauvorschau zeigt alles genau an.

7x7x7: 125 Blöcke, die Seiten sind offen, damit man die Animation sieht. Der Controller richtet sich beim Bilden
automatisch nach außen aus. Rechtsklick auf den Controller (ohne Brennstoff in der Hand) zeigt Status bzw.
den ersten Baufehler (mit Koordinate, erwartetem Block und Partikel an der Stelle).

## Kosmos-Erweiterungen

- **Sternentrichter** (2x Fokusglas oben, Sternenstaub, Leerengehäuse): direkt auf die Deckenmitte setzen.
  Mit freiem Himmel verdoppelt er den Himmelsbonus (+50 %) und saugt sichtbar Energie aus dem All.
- **Asteroiden:** Nachts (Overworld, freier Himmel) und im End ziehen kleine, mittlere und große Asteroiden vorbei
  (`asteroids`, `asteroidChance` in der Config). Energie: 50k / 250k / 1M RF.
- **Kosmos-Laser** (Block: Fokuskristall, Sternenstaub, Eisen, Leerengehäuse): 4 Stück auf die Decke setzen - je einer
  nördlich, östlich, südlich und westlich der Deckenmitte (der Sternentrichter darf in der Mitte sitzen).
  Bei gebildeter Struktur werden sie zu vier Energiepartikeln, die um die Trichter-Position kreisen. Erfassen sie
  einen Asteroiden, bündeln sie sich (Aufladen), feuern einen Strahl, bis er zerbricht, und leiten die freigesetzte
  Energie direkt in den Puffer.
- **Phantom-Blöcke:** Hüllenblöcke außerhalb der sichtbaren Kugel haben bei gebildeter Struktur keine Kollision und
  sind nicht anwählbar. Ports bleiben sichtbar, damit Rohre/Kabel anderer Mods sauber andocken.
## Overdrive

- **Overdrive-Partikel** (4 Endkristalle um einen Netherstern): in der GUI mit „Overdrive +“ installieren (Warnung vorab).
  Pro Stufe: RF x2, Instabilität x3. Maximal 1 / 2 / 3 Stufen bei 5x5x5 / 7x7x7 / 9x9x9.
- Nur am gebildeten Reaktor einstellbar. „Entfernen“ oder Abbau/Zerfall des Reaktors setzt Overdrive zurück - die
  Partikel sind dabei verloren.
- Jede Stufe steigert das Bild: Überladung (blau, zweite Scheibe, Korona), Supernova (Gold, Ringstruktur,
  Strahlenkranz), Quasar (Weiß/Violett, atmende Aura).

## Spielmechanik

- **Zündung:** Brennstoff (Rechtsklick auf Controller oder per Trichter/Rohr in den Item-Port) startet den Kern.
- **Resonanz:** Die Leistung steigt über ~30 s von 20 % auf 100 % (`rampUpTicks`).
- **Sternenlicht:** Freie Sicht zum Himmel über der Decke = +25 % (`skyLinkBonus`), ein Lichtstrahl schießt in den Himmel,
  Sternenstaub regnet herab und eine Akkretionsscheibe kreist über dem Reaktor. Nur mit Himmelssicht gibt es im Overworld
  den Nachtbonus (+25 %, Vollmond +50 %). The End: dauerhaft +40 %. Neumond und End machen den Kern instabiler.
- **Instabilität:** Jede Verbrennung erhöht sie, stärkere Brennstoffe schneller. Stabilisatoren (Obsidian, Weinender Obsidian, Netherit-Schrott)
  werden automatisch verbraucht und halten sie unten. Ab 70 % wird der Kern rot und unruhig, ab 85 % schlägt ein Herzschlag-Alarm, bei 100 % **kollabiert** er:
  Dunkelheit/Übelkeit/Schaden im Umkreis und der **Singularitätskern zerbricht** (`collapseDestroysCore`). Blockzerstörung ist standardmäßig aus.
- **Ausgabe:** Energie-Ports schieben RF aktiv in angrenzende Blöcke und bieten die Energy-Capability an (nur Entnahme).

## Balancing ohne Neukompilieren

- `config/cosmiccore-common.toml` (Puffer pro Stufe, Port-Rate, globaler `outputMultiplier`, Instabilitätsrate, Kollaps ...)
- Data Maps (auch per Datapack überschreibbar):
  `data/cosmiccore/data_maps/item/core_fuel.json` und `core_stabilizer.json`. Fuel-Tooltips zeigen die Werte im Spiel an.

## Texturen/Modelle anpassen

`python3 tools/generate_resources.py` (braucht Pillow) erzeugt alle Texturen, Animationen, Modelle, Rezepte, Loot-Tables und
Sprachdateien neu. Die Singularität (Kugel, Ringe, Sterne, Blitze) wird prozedural in
`client/SingularityCoreRenderer.java` gezeichnet.
