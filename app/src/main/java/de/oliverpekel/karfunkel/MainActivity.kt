package de.oliverpekel.karfunkel

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import de.oliverpekel.karfunkel.ui.Black
import de.oliverpekel.karfunkel.ui.EventMap
import de.oliverpekel.karfunkel.ui.EventSheet
import de.oliverpekel.karfunkel.ui.EventTable
import de.oliverpekel.karfunkel.ui.Gold
import de.oliverpekel.karfunkel.ui.GoldDim
import de.oliverpekel.karfunkel.ui.Hairline
import de.oliverpekel.karfunkel.ui.KarfunkelTheme
import de.oliverpekel.karfunkel.ui.TextPrimary
import de.oliverpekel.karfunkel.ui.TextSecondary
import de.oliverpekel.karfunkel.ui.formatStamp
import org.maplibre.android.MapLibre

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.BLACK),
            navigationBarStyle = SystemBarStyle.dark(Color.BLACK),
        )
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        setContent {
            KarfunkelTheme { MainScreen(vm) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(vm: MainViewModel) {
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.refreshIfStale() }

    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    val error = vm.error
    LaunchedEffect(error) {
        if (error != null && vm.events.isNotEmpty()) {
            snackbar.showSnackbar(error + "\nAngezeigt wird der gespeicherte Stand.")
            vm.dismissError()
        }
    }
    BackHandler(enabled = searchOpen || vm.viewMode == ViewMode.TABLE) {
        if (searchOpen) {
            searchOpen = false
            vm.query = ""
        } else {
            vm.viewMode = ViewMode.MAP
        }
    }

    Scaffold(
        containerColor = Black,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column(Modifier.background(Black)) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Black),
                    title = {
                        Column {
                            Text("Karfunkel-Karte", fontSize = 20.sp, color = Gold)
                            Text(subtitle(vm), fontSize = 12.sp, color = TextSecondary)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            searchOpen = !searchOpen
                            if (!searchOpen) vm.query = ""
                        }) {
                            Icon(if (searchOpen) Icons.Filled.Close else Icons.Filled.Search, "Suche")
                        }
                        if (vm.loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(12.dp).size(24.dp),
                                strokeWidth = 2.dp,
                                color = Gold,
                            )
                        } else {
                            IconButton(onClick = vm::refresh) { Icon(Icons.Filled.Refresh, "Aktualisieren") }
                        }
                    },
                )
                if (searchOpen) {
                    OutlinedTextField(
                        value = vm.query,
                        onValueChange = { vm.query = it },
                        placeholder = { Text("Name, Ort oder PLZ") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldDim,
                            unfocusedBorderColor = Hairline,
                            cursorColor = Gold,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RangeFilter.entries.forEach { r ->
                        FilterChip(
                            selected = vm.range == r,
                            onClick = { vm.range = r },
                            label = { Text(r.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Black,
                                selectedContainerColor = GoldDim,
                                selectedLabelColor = TextPrimary,
                                labelColor = TextSecondary,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = vm.range == r,
                                borderColor = Hairline,
                            ),
                        )
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Black, tonalElevation = 0.dp) {
                val colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Gold,
                    selectedTextColor = Gold,
                    indicatorColor = Black,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary,
                )
                NavigationBarItem(
                    selected = vm.viewMode == ViewMode.MAP,
                    onClick = { vm.viewMode = ViewMode.MAP },
                    icon = { Icon(Icons.Filled.Place, null) },
                    label = { Text("Karte") },
                    colors = colors,
                )
                NavigationBarItem(
                    selected = vm.viewMode == ViewMode.TABLE,
                    onClick = { vm.viewMode = ViewMode.TABLE },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, null) },
                    label = { Text("Tabelle") },
                    colors = colors,
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            // Die Karte bleibt immer erhalten, damit Ausschnitt und Kacheln beim Umschalten nicht verloren gehen.
            EventMap(
                groups = vm.mapGroups,
                focus = vm.focus,
                visible = vm.viewMode == ViewMode.MAP,
                onGroupClick = { ids -> vm.selection = ids },
                modifier = Modifier.fillMaxSize(),
            )
            if (vm.viewMode == ViewMode.MAP) {
                MapStatus(vm, Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
            } else {
                EventTable(
                    events = vm.visibleEvents,
                    hasPosition = { it.id in vm.positions },
                    onClick = { vm.selection = listOf(it.id) },
                    modifier = Modifier.background(Black),
                )
            }
            if (vm.events.isEmpty()) {
                EmptyState(vm, Modifier.align(Alignment.Center))
            }
        }
    }

    val selected = vm.selection.mapNotNull(vm::eventById)
    if (selected.isNotEmpty()) {
        EventSheet(
            events = selected,
            positionOf = { vm.positions[it.id] },
            showMapButton = vm.viewMode == ViewMode.TABLE,
            onShowOnMap = vm::showOnMap,
            onDismiss = { vm.selection = emptyList() },
        )
    }
}

private fun subtitle(vm: MainViewModel): String {
    val count = vm.visibleEvents.size
    val total = vm.events.size
    val n = if (count == total) "$total Termine" else "$count von $total Terminen"
    val stamp = vm.fetchedAt?.let { " · Stand ${formatStamp(it)}" } ?: ""
    val offline = if (vm.fromCache && !vm.loading) " (offline)" else ""
    return n + stamp + offline
}

@Composable
private fun MapStatus(vm: MainViewModel, modifier: Modifier) {
    val parts = buildList {
        if (vm.geocodingLeft > 0) add("Suche Orte … ${vm.geocodingLeft}")
        if (vm.withoutPosition > 0 && vm.geocodingLeft == 0) add("${vm.withoutPosition} ohne Kartenposition")
    }
    if (parts.isEmpty()) return
    Text(
        parts.joinToString(" · "),
        fontSize = 12.sp,
        color = TextSecondary,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Black.copy(alpha = 0.85f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun EmptyState(vm: MainViewModel, modifier: Modifier) {
    Column(
        modifier.padding(32.dp).background(Black),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when {
            vm.loading -> {
                CircularProgressIndicator(color = Gold)
                Text("Kalender wird geladen …", color = TextSecondary)
            }
            vm.error != null -> {
                Text(vm.error.orEmpty(), color = TextPrimary, textAlign = TextAlign.Center)
                Button(onClick = vm::refresh) { Text("Erneut versuchen") }
            }
            else -> Text("Keine Termine im Kalender.", color = TextSecondary)
        }
    }
}
