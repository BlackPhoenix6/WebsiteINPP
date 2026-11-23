# Getränke-Tracker (Kasse des Vertrauens)

Kleine Android-App plus Homescreen-Widget zum Mitzählen, wie viel man getrunken
und wie viel man in die Kasse des Vertrauens eingeworfen hat.

- **Spezi** 1,00 € · **Sterni** 1,00 € · **Mate** 1,50 € (Preise in der App änderbar)
- Kontostand = eingezahlt − getrunken → rot „Noch zu zahlen“ oder grün „Guthaben“
- Widget: ein Tipp auf Spezi/Sterni/Mate bucht ein Getränk, „€ zahlen“ öffnet die Einzahlung
- In der App: 1/2/5/10 € oder beliebiger Betrag, „Schulden komplett bezahlt“, Rückgängig,
  Verlauf (Eintrag antippen → löschen)
- **Statistik**: Ausgaben pro Woche/Monat als Balkendiagramm (nach Getränk gestapelt, Ø-Linie),
  diese Woche vs. letzte, Monats-Hochrechnung, 4-Wochen-Trend, Lieblingsgetränk, Wochentage
- **Mensa-Tab**: Speiseplan der TU Mensa Hardenbergstraße und der veganen Mensa (Veggie 2.0)
  über die OpenMensa-API, Gericht antippen = mit Preis (Studierende/Beschäftigte/Gäste) eintragen,
  eigene Essen, Ausgaben pro Woche/Monat, Diagramm; Speiseplan wird alle paar Stunden im Hintergrund geladen
- **Mensa-Widget**: heutige Gerichte als Liste, Antippen trägt ein, Titel antippen wechselt die Mensa
- Alle Einträge bleiben lokal auf dem Handy; Internet wird nur für den Speiseplan genutzt

## Installieren
`Getraenke-Tracker.apk` aufs Handy laden, öffnen, Installation aus unbekannten Quellen erlauben.
Danach lange auf den Homescreen drücken → Widgets → „Getränke-Konto“ bzw. „Mensa heute“.

## Selbst bauen
```
gradle assembleRelease   # braucht Android SDK (platform 35) in local.properties / ANDROID_HOME
```
Ergebnis: `app/build/outputs/apk/release/app-release.apk`
