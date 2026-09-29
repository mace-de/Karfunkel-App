# Karfunkel-Karte

Android-App, die die Termine aus der Druckansicht des Karfunkel-Kalenders
(https://www.karfunkel.de/Kalender/Kalender_Karfunkel/kalender.php?kal_Aktion=druck&kal_Popup=1)
auf einer zoombaren Karte zeigt. Umschaltbar auf eine sortierbare Tabellenansicht; dunkles OLED-Design.

## Download

[apk/Karfunkel-Karte.apk](https://github.com/mace-de/Karfunkel-App/raw/main/apk/Karfunkel-Karte.apk) (Android 8+, ARM).
Zum Installieren auf dem Handy „Unbekannte Apps installieren“ für den Browser bzw. Dateimanager erlauben.

## Funktionsweise

- Die Seite wird bei jedem Start geladen und erneut, wenn der Stand älter als 3 Stunden ist (oder über ↻).
  Die Spalten werden über ihre Überschriften erkannt. Neue Termine, PLZ und Orte funktionieren ohne App-Update.
- Der letzte erfolgreich geladene Stand wird gespeichert und offline angezeigt.
- Positionen: PLZ-Tabelle `app/src/main/assets/plz_de.csv` (GeoNames, CC BY 4.0).
  Fehlende PLZ oder Orte ohne PLZ werden über Nominatim (OpenStreetMap) gesucht und dauerhaft zwischengespeichert.
- Karte: MapLibre mit OpenFreeMap-Vektorkacheln (Stil „dark“, Hintergrund reines Schwarz), frei nutzbar, ohne API-Schlüssel.
  Kartendaten © OpenStreetMap-Mitwirkende.

## Bauen

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-23"
& "C:\Users\op\Documents\Mittelalter App\gradlew.bat" -p "C:\Users\op\Documents\Mittelalter App" assembleRelease
```

Ergebnis: `app\build\outputs\apk\release\app-release.apk`, mit dem Debug-Schlüssel dieses Rechners signiert.
Updates lassen sich nur über eine Version mit demselben Schlüssel installieren.
