package de.oliverpekel.karfunkel.data

import java.time.LocalDate

/** Eine Zeile aus der Karfunkel-Gesamtliste. */
data class Event(
    val id: Int,
    val start: LocalDate?,
    val end: LocalDate?,
    val startText: String,
    val endText: String,
    val plz: String,
    val ort: String,
    val name: String,
    /** Alle Spalten der Zeile in Originalreihenfolge (Überschrift → Inhalt). */
    val columns: List<Pair<String, String>>,
) {
    /** Letzter Veranstaltungstag; ein Ende vor dem Beginn (Tippfehler in der Quelle) wird ignoriert. */
    val lastDay: LocalDate?
        get() = end?.takeIf { start == null || !it.isBefore(start) } ?: start

    /** Schlüssel für Geocoding: gleiche PLZ + gleicher Ort = gleiche Position. */
    val locationKey: String
        get() = "$plz|${ort.trim().lowercase()}"
}

data class LatLon(val lat: Double, val lon: Double)
