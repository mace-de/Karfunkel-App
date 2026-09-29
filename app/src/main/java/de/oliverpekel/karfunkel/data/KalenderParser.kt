package de.oliverpekel.karfunkel.data

import org.jsoup.Jsoup
import java.time.LocalDate

class ParseException(message: String) : Exception(message)

/**
 * Liest die Druckansicht des Karfunkel-Kalenders:
 *
 *   <div class="kalTbZlDr">            eine Zeile (die erste ist die Überschrift)
 *     <div class="kalTbDr">…</div>     eine Zelle
 *
 * Die Spalten werden über ihre Überschrift zugeordnet, nicht über die Position.
 */
object KalenderParser {

    private val DATE = Regex("""(\d{1,2})\.(\d{1,2})\.(\d{2,4})""")
    private val PLZ = Regex("""\b\d{4,5}\b""")
    private val DEFAULT_HEADER =
        listOf("Datum Beginn", "Datum Ende", "Postleitzahl", "Veranstaltungsort", "Name der Veranstaltung")

    fun parse(html: String): List<Event> {
        val doc = Jsoup.parse(html)
        val rows = doc.select("div.kalTbZlDr").map { row ->
            row.select("> div.kalTbDr").map { it.text().replace(' ', ' ').trim() }
        }
        if (rows.isEmpty()) {
            throw ParseException("Keine Termine auf der Kalenderseite gefunden.")
        }

        val headerIndex = rows.indexOfFirst { cells -> cells.any { it.contains("Datum", ignoreCase = true) } }
        val header = if (headerIndex >= 0) rows[headerIndex] else DEFAULT_HEADER
        val col = Columns.from(header)

        return rows.drop(headerIndex + 1)
            .filter { cells -> cells.any { it.isNotBlank() } }
            .mapIndexed { i, cells ->
                fun cell(index: Int) = if (index >= 0) cells.getOrElse(index) { "" } else ""
                val ort = cell(col.ort)
                val plz = PLZ.find(cell(col.plz))?.value ?: ""
                Event(
                    id = i,
                    start = parseDate(cell(col.start)),
                    end = parseDate(cell(col.end)),
                    startText = cell(col.start),
                    endText = cell(col.end),
                    plz = plz,
                    ort = ort,
                    name = cell(col.name).replace(Regex("\\s+"), " "),
                    columns = cells.mapIndexed { c, value -> header.getOrElse(c) { "Spalte ${c + 1}" } to value },
                )
            }
    }

    fun parseDate(text: String): LocalDate? {
        val m = DATE.find(text) ?: return null
        val (d, mo, y) = m.destructured
        val year = y.toInt().let { if (it < 100) it + 2000 else it }
        return runCatching { LocalDate.of(year, mo.toInt(), d.toInt()) }.getOrNull()
    }

    private class Columns(val start: Int, val end: Int, val plz: Int, val ort: Int, val name: Int) {
        companion object {
            fun from(header: List<String>): Columns {
                val h = header.map { it.lowercase() }
                val used = mutableSetOf<Int>()
                fun find(fallback: Int, match: (String) -> Boolean): Int {
                    val i = h.indices.firstOrNull { it !in used && match(h[it]) }
                        ?: fallback.takeIf { it < h.size && it !in used }
                        ?: -1
                    if (i >= 0) used += i
                    return i
                }
                val end = find(1) { "ende" in it || "bis" in it }
                val start = find(0) { "beginn" in it || "von" in it || "datum" in it }
                val plz = find(2) { "postleitzahl" in it || "plz" in it }
                val ort = find(3) { "ort" in it }
                val name = find(4) { "name" in it || "titel" in it || "veranstaltung" in it }
                return Columns(start, end, plz, ort, name)
            }
        }
    }
}
