#!/bin/bash
# Start-Skript mit Schleife: Nach /restart oder Shutdown startet der Server automatisch neu.
# Zum endgültigen Beenden: STRG+C innerhalb von 5 Sekunden drücken.
while true; do
    java -Xms4G -Xmx4G -jar paper-1.21.11.jar --nogui
    echo "Server gestoppt. Neustart in 5 Sekunden... (STRG+C zum Abbrechen)"
    sleep 5
done
