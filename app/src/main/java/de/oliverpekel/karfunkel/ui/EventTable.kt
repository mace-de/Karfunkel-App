package de.oliverpekel.karfunkel.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.oliverpekel.karfunkel.data.Event
import java.util.Locale

private enum class SortColumn { DATE, ORT, NAME }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EventTable(
    events: List<Event>,
    hasPosition: (Event) -> Boolean,
    onClick: (Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sort by rememberSaveable { mutableStateOf(SortColumn.DATE) }
    var ascending by rememberSaveable { mutableStateOf(true) }

    val sorted = remember(events, sort, ascending) {
        val cmp: Comparator<Event> = when (sort) {
            SortColumn.DATE -> compareBy(nullsLast()) { it.start }
            SortColumn.ORT -> compareBy<Event> { it.ort.lowercase(Locale.GERMAN) }.thenBy { it.start }
            SortColumn.NAME -> compareBy<Event> { it.name.lowercase(Locale.GERMAN) }.thenBy { it.start }
        }
        events.sortedWith(if (ascending) cmp else cmp.reversed())
    }
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        stickyHeader {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 12.dp),
            ) {
                fun toggle(c: SortColumn) {
                    if (sort == c) ascending = !ascending else { sort = c; ascending = true }
                }
                HeaderCell("Datum", sort == SortColumn.DATE, ascending, Modifier.width(DATE_WIDTH)) { toggle(SortColumn.DATE) }
                HeaderCell("Ort", sort == SortColumn.ORT, ascending, Modifier.weight(0.9f)) { toggle(SortColumn.ORT) }
                HeaderCell("Veranstaltung", sort == SortColumn.NAME, ascending, Modifier.weight(1.3f)) { toggle(SortColumn.NAME) }
            }
            HorizontalDivider(color = GoldDim, thickness = 1.dp)
        }
        items(sorted, key = { it.id }) { e ->
            EventRow(e, hasPosition(e), onClick)
            HorizontalDivider(color = Hairline, thickness = 0.5.dp)
        }
    }
}

private val DATE_WIDTH = 100.dp

@Composable
private fun RowScope.HeaderCell(
    title: String,
    active: Boolean,
    ascending: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Text(
        text = if (active) "$title ${if (ascending) "▲" else "▼"}" else title,
        color = if (active) Gold else TextSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
    )
}

@Composable
private fun EventRow(e: Event, hasPosition: Boolean, onClick: (Event) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick(e) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.width(DATE_WIDTH).padding(horizontal = 4.dp)) {
            val start = e.start
            if (start != null) {
                Text("${start.weekday()} ${start.short()}", fontSize = 13.sp, color = TextPrimary)
                val last = e.lastDay
                if (last != null && last != start) {
                    Text("– ${last.weekday()} ${last.short()}", fontSize = 12.sp, color = TextSecondary)
                }
            } else {
                Text(e.startText, fontSize = 13.sp, color = TextSecondary)
            }
        }
        Column(Modifier.weight(0.9f).padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (e.plz.isNotEmpty()) {
                Text(e.plz, fontSize = 11.sp, color = if (hasPosition) GoldDim else TextSecondary)
            }
            Text(e.ort, fontSize = 13.sp, color = TextPrimary, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Text(
            e.name,
            fontSize = 14.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1.3f).padding(horizontal = 4.dp),
        )
    }
}
