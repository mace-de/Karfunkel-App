package de.oliverpekel.karfunkel.ui

import de.oliverpekel.karfunkel.data.Event
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY = DateTimeFormatter.ofPattern("EE dd.MM.yyyy", Locale.GERMAN)
private val DAY_SHORT = DateTimeFormatter.ofPattern("dd.MM.yy", Locale.GERMAN)
private val WEEKDAY = DateTimeFormatter.ofPattern("EE", Locale.GERMAN)
private val STAMP = DateTimeFormatter.ofPattern("dd.MM. HH:mm", Locale.GERMAN)

/** "Sa 26.09.2026 – So 27.09.2026" oder bei eintägigen Terminen nur ein Datum. */
fun Event.dateRange(): String {
    val s = start?.format(DAY) ?: startText.ifBlank { "?" }
    val e = end?.format(DAY) ?: endText
    return if (e.isBlank() || end == start) s else "$s – $e"
}

fun LocalDate.short(): String = format(DAY_SHORT)
fun LocalDate.weekday(): String = format(WEEKDAY).removeSuffix(".")

fun formatStamp(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(STAMP)
