package de.oliverpekel.karfunkel.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.oliverpekel.karfunkel.WeekRange
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

private val MONTHS = listOf("Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez")
private val INSET = 12.dp
private val BAR_TOP = 5.dp
private val BAR_HEIGHT = 30.dp
private val TickColor = Color(0xFF2A2621)

/**
 * Zeitleiste über das kommende Jahr mit zwei Schiebern, die im Wochenraster einrasten.
 * Der gewählte Bereich wird teiltransparent hinterlegt und als Datum in der Leiste angezeigt.
 */
@Composable
fun DateRangeBar(
    range: WeekRange,
    origin: LocalDate,
    label: String,
    onChange: (WeekRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val weeks = WeekRange.WEEKS
    val measurer = rememberTextMeasurer()
    val haptic = LocalHapticFeedback.current
    val currentRange by rememberUpdatedState(range)
    val currentOnChange by rememberUpdatedState(onChange)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .semantics { contentDescription = "Zeitraum: $label" }
            .pointerInput(Unit) {
                val inset = INSET.toPx()
                val track = size.width - 2 * inset
                fun xOf(week: Int) = inset + track * week / weeks
                fun weekAt(x: Float) = ((x - inset) / track * weeks).roundToInt().coerceIn(0, weeks)

                awaitEachGesture {
                    val down = awaitFirstDown()
                    var cur = currentRange
                    val xa = xOf(cur.first)
                    val xb = xOf(cur.last)
                    val x0 = down.position.x
                    // Nächsten Schieber greifen; außerhalb des Bereichs den der jeweiligen Seite.
                    val left = when {
                        x0 <= xa -> true
                        x0 >= xb -> false
                        else -> abs(x0 - xa) <= abs(x0 - xb)
                    }

                    fun move(x: Float) {
                        val w = weekAt(x)
                        val next = if (left) {
                            WeekRange(w.coerceAtMost(cur.last - 1), cur.last)
                        } else {
                            WeekRange(cur.first, w.coerceAtLeast(cur.first + 1))
                        }
                        if (next != cur) {
                            cur = next
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentOnChange(next)
                        }
                    }

                    move(x0)
                    down.consume()
                    drag(down.id) { change ->
                        move(change.position.x)
                        change.consume()
                    }
                }
            },
    ) {
        val inset = INSET.toPx()
        val track = size.width - 2 * inset
        val top = BAR_TOP.toPx()
        val barH = BAR_HEIGHT.toPx()
        val totalDays = weeks * 7f
        fun xOfDate(d: LocalDate) = inset + track * ChronoUnit.DAYS.between(origin, d) / totalDays
        fun xOfWeek(w: Int) = inset + track * w / weeks

        // Leiste
        val corner = CornerRadius(6.dp.toPx())
        drawRoundRect(Color(0xFF0A0908), Offset(inset, top), Size(track, barH), corner)

        // Monatsgrenzen mit Namen darunter
        val monthStyle = TextStyle(color = TextSecondary.copy(alpha = 0.7f), fontSize = 10.sp)
        val yearStyle = monthStyle.copy(color = Gold.copy(alpha = 0.8f))
        var month = origin.withDayOfMonth(1).plusMonths(1)
        val end = origin.plusWeeks(weeks.toLong())
        while (month.isBefore(end)) {
            val x = xOfDate(month)
            drawLine(TickColor, Offset(x, top), Offset(x, top + barH), 1.dp.toPx())
            val name = if (month.monthValue == 1) "${month.year}" else MONTHS[month.monthValue - 1]
            val layout = measurer.measure(name, if (month.monthValue == 1) yearStyle else monthStyle)
            if (x + layout.size.width < size.width) {
                drawText(layout, topLeft = Offset(x + 2.dp.toPx(), top + barH + 3.dp.toPx()))
            }
            month = month.plusMonths(1)
        }
        drawRoundRect(Hairline, Offset(inset, top), Size(track, barH), corner, style = Stroke(1.dp.toPx()))

        // Gewählter Bereich
        val xa = xOfWeek(range.first)
        val xb = xOfWeek(range.last)
        drawRect(Gold.copy(alpha = 0.22f), Offset(xa, top), Size(xb - xa, barH))

        // Heute
        val xToday = xOfDate(LocalDate.now())
        drawLine(Karfunkel, Offset(xToday, top), Offset(xToday, top + barH), 2.dp.toPx())

        // Datum des Bereichs, teiltransparent in der Leiste
        val labelLayout = measurer.measure(
            label,
            TextStyle(color = TextPrimary.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Medium),
        )
        val tw = labelLayout.size.width
        val cx = ((xa + xb) / 2).coerceIn(inset + tw / 2f + 8.dp.toPx(), size.width - inset - tw / 2f - 8.dp.toPx())
        drawText(labelLayout, topLeft = Offset(cx - tw / 2f, top + (barH - labelLayout.size.height) / 2))

        // Schieber
        val thumbW = 6.dp.toPx()
        val thumbH = barH + 8.dp.toPx()
        for (x in listOf(xa, xb)) {
            drawRoundRect(
                Gold,
                Offset(x - thumbW / 2, top - 4.dp.toPx()),
                Size(thumbW, thumbH),
                CornerRadius(thumbW / 2),
            )
            drawLine(
                Black.copy(alpha = 0.6f),
                Offset(x, top + barH / 2 - 5.dp.toPx()),
                Offset(x, top + barH / 2 + 5.dp.toPx()),
                1.5.dp.toPx(),
            )
        }
    }
}
