# Changelog

Alle nennenswerten Änderungen an Cosmic Core stehen in dieser Datei.

Das Format folgt [Keep a Changelog](https://keepachangelog.com/de/1.1.0/), die Versionsnummern folgen
[Semantic Versioning](https://semver.org/lang/de/): `MAJOR.MINOR.PATCH`. MINOR steht für neue Funktionen, PATCH für Fehlerbehebungen.
Die Dateien heißen `cosmiccore-<Minecraft-Version>-<Mod-Version>.jar`.

## [1.2.1] - 2026-10-16

### Hinzugefügt
- Eigene Dateien für Minecraft 1.21.2, 1.21.3, 1.21.4, 1.21.5, 1.21.8, 1.21.10, 1.21.11, 26.1.2, 26.2 und 26.3
  (Zweige `mc/<Version>`). Welten lassen sich mitnehmen, gelagerte Items bleiben erhalten.

### Behoben
- Handbuch: Beim Blättern schienen Item-Symbole und die 3D-Vorschau der Multiblöcke von der nächsten Seite schon
  durch das umklappende Blatt. Das Blatt verdeckt sie jetzt sauber.

## [1.2.0] - 2026-10-16

### Hinzugefügt
- Das Handbuch ist jetzt ein animiertes 3D-Buch. In der Hand klappt es auf, eine Seite blättert sich regelmäßig um,
  und über der Doppelseite schweben ein Sternsiegel und Funken. Im Inventar liegt es geschlossen, das Emblem pulsiert.

### Geändert
- Neue Buchtextur: violetter Ledereinband mit Goldrahmen, Stern-Emblem und Ecknieten, beschriebene Seiten mit Diagramm,
  Goldschnitt und Goldbünde am Rücken. Die drei alten Buchtexturen wurden entfernt.

## [1.1.0] - 2026-10-15

### Hinzugefügt
- Das Handbuch-Fenster ist animiert: Das Buch schwebt geschlossen heran und klappt auf, Seiten drehen sich beim
  Blättern um den Buchrücken, beim Schließen klappt es wieder zu.
- Neue README, erst auf Englisch, dann auf Deutsch, mit Sprachlinks und Inhaltsverzeichnis.

### Behoben
- Zu lange Texte im Handbuch laufen nicht mehr über den Seitenrand, sondern gehen auf der nächsten Seite weiter.
- Die Brennstoff-Zeilen im Handbuch passen aufs Blatt.
- Blasse Hervorhebungen (Gelb, Hellblau) auf dem Papier sind jetzt kräftiger und gut lesbar.

## [1.0.0] - 2026-10-15

### Hinzugefügt
- Erste Veröffentlichung: Kerngenerator-Multiblock in vier Größen (5×5×5 bis 11×11×11) mit animiertem schwarzem Loch.
- Leistungsstufen, Brennstoffe, Stabilisatoren, Instabilität und Kollaps.
- Pflicht-Ports: Energie-Port und Item-Port mit Lager für 27 Stapel Beute. Direkt neben dem Controller verschmelzen
  sie zu einer Konsole mit breitem Hologramm-Bildschirm.
- Asteroiden mit Sternentrichter und Laser-Traktorstrahl.
- Module (Effizienz, Stabilität, Anti-Asteroiden, Farbe) im ausklappbaren Modul-Tab.
- Overdrive und Ultimate-Modus mit Void-Titan-Zwischensequenz (mit GeckoLib).
- Kosmisches Handbuch, JEI-, EMI- und REI-Anbindung.

## Entwicklungsstände 0.1.0 bis 0.34.3

Die Stände vor der ersten Veröffentlichung (13. bis 15.10.2026) liegen im Zweig `history`, jeweils mit Tag und als
Vorab-Release mit eigener Datei. MINOR steht dort für eine neue Funktion, PATCH für eine Korrektur.

[1.2.1]: https://github.com/Drakune/cosmiccore/compare/v1.2.0...v1.2.1
[1.2.0]: https://github.com/Drakune/cosmiccore/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/Drakune/cosmiccore/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/Drakune/cosmiccore/releases/tag/v1.0.0
