package de.oliverpekel.karfunkel.ui

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.oliverpekel.karfunkel.data.Event
import de.oliverpekel.karfunkel.data.LatLon
import java.time.ZoneOffset

/** Details zu einem oder mehreren Terminen (mehrere, wenn sie am selben Ort stattfinden). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventSheet(
    events: List<Event>,
    positionOf: (Event) -> LatLon?,
    showMapButton: Boolean,
    onShowOnMap: (Event) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = events.size == 1),
        containerColor = Black,
        contentColor = TextPrimary,
        scrimColor = Black.copy(alpha = 0.6f),
        tonalElevation = 0.dp,
    ) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding()) {
            if (events.size > 1) {
                item {
                    Text(
                        "${events.size} Termine an diesem Ort",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
            }
            items(events, key = { it.id }) { e ->
                EventDetails(e, positionOf(e), showMapButton, onShowOnMap)
                HorizontalDivider(color = Hairline, modifier = Modifier.padding(horizontal = 20.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventDetails(e: Event, pos: LatLon?, showMapButton: Boolean, onShowOnMap: (Event) -> Unit) {
    val context = LocalContext.current
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(e.name, color = Gold, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp)

        InfoLine(Icons.Filled.DateRange, e.dateRange())
        InfoLine(Icons.Filled.LocationOn, listOf(e.plz, e.ort).filter { it.isNotBlank() }.joinToString(" "))
        if (pos == null) {
            Text("Keine Kartenposition gefunden", color = TextSecondary, fontSize = 12.sp)
        }

        // Spalten, die die App nicht gesondert darstellt (falls die Quelle neue hinzufügt)
        val known = setOf(e.startText, e.endText, e.ort, e.name)
        e.columns.filter { (h, v) -> v.isNotBlank() && v !in known && !h.contains("Postleitzahl", true) }
            .forEach { (h, v) ->
                Text("$h: $v", color = TextSecondary, fontSize = 13.sp)
            }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (showMapButton && pos != null) {
                Action(Icons.Filled.Place, "Auf Karte") { onShowOnMap(e) }
            }
            if (pos != null) {
                Action(Icons.Filled.LocationOn, "Navigation") { openInMaps(context, e, pos) }
            }
            if (e.start != null) {
                Action(Icons.Filled.DateRange, "Kalender") { addToCalendar(context, e) }
            }
            Action(Icons.Filled.Search, "Websuche") { webSearch(context, e) }
        }
    }
}

@Composable
private fun InfoLine(icon: ImageVector, text: String) {
    Row {
        Icon(icon, contentDescription = null, tint = GoldDim, modifier = Modifier.size(18.dp))
        Text(text, fontSize = 15.sp, color = TextPrimary, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun Action(icon: ImageVector, label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Text(label, modifier = Modifier.padding(start = 6.dp), fontSize = 13.sp)
    }
}

private fun Context.launch(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, "Keine passende App gefunden", Toast.LENGTH_SHORT).show()
    }
}

private fun openInMaps(context: Context, e: Event, pos: LatLon) {
    val label = Uri.encode("${e.name} (${e.ort})")
    context.launch(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${pos.lat},${pos.lon}?q=${pos.lat},${pos.lon}($label)")))
}

private fun addToCalendar(context: Context, e: Event) {
    val start = e.start ?: return
    val last = e.lastDay ?: start
    // Ganztägige Termine erwartet der Kalender in UTC-Mitternacht, Ende exklusiv.
    val begin = start.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    val end = last.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    context.launch(
        Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, e.name)
            .putExtra(CalendarContract.Events.EVENT_LOCATION, listOf(e.plz, e.ort).filter { it.isNotBlank() }.joinToString(" "))
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
    )
}

private fun webSearch(context: Context, e: Event) {
    val query = "${e.name} ${e.ort}"
    try {
        context.startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query))
    } catch (_: ActivityNotFoundException) {
        context.launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://duckduckgo.com/?q=" + Uri.encode(query))))
    }
}
