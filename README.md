# AutoRestart

Paper-Plugin (1.21.11, Java 21) für automatische und manuelle Server-Restarts mit Countdown.

## Features
- Tägliche Restart-Zeiten (z. B. 04:00, 16:00) inkl. Zeitzone
- Countdown-Warnungen im Chat, Title bei den letzten 10 Sekunden
- Alle Nachrichten per MiniMessage in `config.yml` anpassbar

## Befehle
| Befehl | Beschreibung |
|---|---|
| `/restart` | Countdown mit Standarddauer starten |
| `/restart <sekunden>` | Countdown mit eigener Dauer |
| `/restart cancel` | Countdown abbrechen |
| `/restart status` | Status und nächster Restart |
| `/restart reload` | Config neu laden |

Permission: `autorestart.admin` (Standard: OP)

## Installation
1. Jar aus den [Releases](../../releases) oder den Actions-Artifacts herunterladen
2. In `plugins/` legen und Server starten
3. `plugins/AutoRestart/config.yml` anpassen

> Der Server muss per Start-Skript mit Schleife (siehe `start.sh`) oder Hosting-Panel automatisch neu starten.

## Selbst bauen
```bash
mvn clean package
```
Die Jar liegt danach in `target/`.
