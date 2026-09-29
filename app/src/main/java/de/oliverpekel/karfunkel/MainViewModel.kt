package de.oliverpekel.karfunkel

import android.app.Application
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.oliverpekel.karfunkel.data.Event
import de.oliverpekel.karfunkel.data.Geocoder
import de.oliverpekel.karfunkel.data.KalenderRepository
import de.oliverpekel.karfunkel.data.LatLon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
import java.util.Locale

enum class ViewMode { MAP, TABLE }

enum class RangeFilter(val label: String, val days: Long?) {
    ALL("Alle", null),
    WEEK("7 Tage", 7),
    MONTH("30 Tage", 30),
    QUARTER("3 Monate", 91),
}

/** Termine an derselben Kartenposition. */
data class MapGroup(val pos: LatLon, val eventIds: List<Int>)

/** Kamerafahrt zur Position; [seq] macht wiederholte Anfragen zum selben Ort unterscheidbar. */
data class FocusRequest(val pos: LatLon, val seq: Long)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = KalenderRepository(app)
    private val geocoder = Geocoder(app)

    var events by mutableStateOf<List<Event>>(emptyList())
        private set
    var positions by mutableStateOf<Map<Int, LatLon>>(emptyMap())
        private set
    var loading by mutableStateOf(false)
        private set
    var fetchedAt by mutableStateOf<Long?>(null)
        private set
    /** true, solange nur der gespeicherte Stand angezeigt wird. */
    var fromCache by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var geocodingLeft by mutableStateOf(0)
        private set

    var viewMode by mutableStateOf(ViewMode.MAP)
    var range by mutableStateOf(RangeFilter.ALL)
    var query by mutableStateOf("")
    var selection by mutableStateOf<List<Int>>(emptyList())
    var focus by mutableStateOf<FocusRequest?>(null)
        private set

    private var refreshJob: Job? = null
    private var geocodeJob: Job? = null

    val visibleEvents: List<Event> by derivedStateOf {
        val today = LocalDate.now()
        val limit = range.days?.let { today.plusDays(it) }
        val words = query.trim().lowercase(Locale.GERMAN).split(Regex("\\s+")).filter { it.isNotEmpty() }
        events.filter { e ->
            val inRange = limit == null || run {
                val start = e.start ?: return@run false
                val last = e.lastDay ?: start
                !start.isAfter(limit) && !last.isBefore(today)
            }
            inRange && words.all { w -> e.columns.any { (_, v) -> w in v.lowercase(Locale.GERMAN) } }
        }
    }

    val mapGroups: List<MapGroup> by derivedStateOf {
        visibleEvents
            .mapNotNull { e -> positions[e.id]?.let { it to e.id } }
            .groupBy({ it.first }, { it.second })
            .map { (pos, ids) -> MapGroup(pos, ids) }
    }

    val withoutPosition: Int by derivedStateOf {
        visibleEvents.count { it.id !in positions }
    }

    fun eventById(id: Int): Event? = events.getOrNull(id)?.takeIf { it.id == id }

    init {
        viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) { repository.loadCached() }
            if (cached != null && events.isEmpty()) {
                applyEvents(cached.events)
                fetchedAt = cached.fetchedAt
                fromCache = true
            }
        }
        refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            loading = true
            error = null
            try {
                val snapshot = withContext(Dispatchers.IO) { repository.fetch() }
                applyEvents(snapshot.events)
                fetchedAt = snapshot.fetchedAt
                fromCache = false
            } catch (e: IOException) {
                error = "Kalender nicht erreichbar (${e.message ?: e.javaClass.simpleName})"
            } catch (e: Exception) {
                error = "Kalender konnte nicht gelesen werden: ${e.message}"
            } finally {
                loading = false
            }
        }
    }

    /** Beim Zurückkehren in die App: neu laden, wenn der Stand alt ist oder nur aus dem Speicher kommt. */
    fun refreshIfStale() {
        val age = fetchedAt?.let { System.currentTimeMillis() - it }
        if (fromCache || age == null || age > STALE_AFTER_MS) refresh()
    }

    fun dismissError() {
        error = null
    }

    fun showOnMap(e: Event) {
        val pos = positions[e.id] ?: return
        selection = emptyList()
        viewMode = ViewMode.MAP
        focus = FocusRequest(pos, System.nanoTime())
    }

    private fun applyEvents(list: List<Event>) {
        events = list.sortedWith(compareBy(nullsLast()) { it.start })
            .mapIndexed { i, e -> e.copy(id = i) }
        selection = emptyList()
        geocode()
    }

    private fun geocode() {
        geocodeJob?.cancel()
        val list = events
        geocodeJob = viewModelScope.launch {
            val found = HashMap<Int, LatLon>()
            val online = LinkedHashMap<String, MutableList<Event>>()
            withContext(Dispatchers.Default) {
                for (e in list) {
                    when (val r = geocoder.lookupOffline(e)) {
                        is Geocoder.Result.Found -> found[e.id] = r.pos
                        Geocoder.Result.NeedsOnline -> online.getOrPut(e.locationKey) { mutableListOf() } += e
                        Geocoder.Result.NotFound -> Unit
                    }
                }
            }
            positions = found
            geocodingLeft = online.size

            for (group in online.values) {
                val pos = try {
                    geocoder.lookupOnline(group.first())
                } catch (e: IOException) {
                    null // offline: beim nächsten Laden erneut versuchen
                }
                if (pos != null) positions = positions + group.associate { it.id to pos }
                geocodingLeft--
            }
        }
    }

    companion object {
        private const val STALE_AFTER_MS = 3L * 3600 * 1000
    }
}
