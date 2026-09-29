package de.oliverpekel.karfunkel.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Ermittelt Kartenpositionen.
 *
 * 1. PLZ-Tabelle aus den Assets (GeoNames, CC BY 4.0) – offline, deckt fast alle Termine ab.
 * 2. Für fehlende oder unbekannte PLZ: Nominatim (OpenStreetMap) anhand des Ortsnamens.
 *    Ergebnisse werden dauerhaft gespeichert; höchstens eine Anfrage pro Sekunde (Nutzungsbedingungen).
 */
class Geocoder(private val context: Context) {

    sealed interface Result {
        data class Found(val pos: LatLon) : Result
        data object NotFound : Result
        data object NeedsOnline : Result
    }

    private val plzTable: Map<String, LatLon> by lazy {
        context.assets.open("plz_de.csv").bufferedReader().useLines { lines ->
            lines.mapNotNull { line ->
                val p = line.split(';')
                if (p.size < 3) null
                else p[0] to LatLon(p[1].toDouble(), p[2].toDouble())
            }.toMap()
        }
    }

    private val cacheFile = File(context.filesDir, "geocache.json")
    private val cache: MutableMap<String, CacheEntry> by lazy { readCache() }
    private val cacheLock = Any()
    private val netMutex = Mutex()
    private var lastRequestAt = 0L

    private data class CacheEntry(val pos: LatLon?, val time: Long)

    fun lookupOffline(e: Event): Result {
        plzTable[e.plz]?.let { return Result.Found(it) }
        if (queries(e).isEmpty()) return Result.NotFound
        val entry = synchronized(cacheLock) { cache[e.locationKey] } ?: return Result.NeedsOnline
        return when {
            entry.pos != null -> Result.Found(entry.pos)
            System.currentTimeMillis() - entry.time > RETRY_NOT_FOUND_MS -> Result.NeedsOnline
            else -> Result.NotFound
        }
    }

    /** Sucht online; null = nicht gefunden (auch das wird gespeichert). Wirft bei Netzwerkfehlern. */
    suspend fun lookupOnline(e: Event): LatLon? = withContext(Dispatchers.IO) {
        var result: LatLon? = null
        for (q in queries(e)) {
            result = nominatim(q)
            if (result != null) break
        }
        synchronized(cacheLock) {
            cache[e.locationKey] = CacheEntry(result, System.currentTimeMillis())
            writeCache()
        }
        result
    }

    /** Suchanfragen vom genauesten zum gröbsten. Leere Liste = nicht auf der Karte darstellbar. */
    private fun queries(e: Event): List<String> {
        val ort = e.ort.trim()
        val lower = ort.lowercase()
        if (ort.isEmpty() && e.plz.isEmpty()) return emptyList()
        if (ONLINE_WORDS.any { it in lower }) return emptyList()

        val q = linkedSetOf<String>()
        if (e.plz.isNotEmpty() && ort.isNotEmpty()) q += "${e.plz} $ort"
        if (ort.isNotEmpty()) q += ort
        ort.split(PART_SEPARATOR).map { it.trim() }.filter { it.length > 2 }.forEach { q += it }
        if (e.plz.isNotEmpty()) q += e.plz
        return q.toList()
    }

    private suspend fun nominatim(query: String): LatLon? = netMutex.withLock {
        val wait = lastRequestAt + MIN_INTERVAL_MS - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        lastRequestAt = System.currentTimeMillis()

        val url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1" +
            "&countrycodes=de,at,ch&accept-language=de&q=" + URLEncoder.encode(query, "UTF-8")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", KalenderRepository.USER_AGENT)
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw java.io.IOException("Nominatim HTTP ${conn.responseCode}")
            }
            val arr = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
            if (arr.length() == 0) return@withLock null
            val o = arr.getJSONObject(0)
            LatLon(o.getString("lat").toDouble(), o.getString("lon").toDouble())
        } finally {
            conn.disconnect()
        }
    }

    private fun readCache(): MutableMap<String, CacheEntry> = runCatching {
        val json = JSONObject(cacheFile.readText())
        json.keys().asSequence().associateWith { key ->
            val o = json.getJSONObject(key)
            val pos = if (o.has("lat")) LatLon(o.getDouble("lat"), o.getDouble("lon")) else null
            CacheEntry(pos, o.getLong("t"))
        }.toMutableMap()
    }.getOrElse { mutableMapOf() }

    private fun writeCache() {
        val json = JSONObject()
        cache.forEach { (key, entry) ->
            json.put(key, JSONObject().apply {
                entry.pos?.let { put("lat", it.lat); put("lon", it.lon) }
                put("t", entry.time)
            })
        }
        val tmp = File(cacheFile.parentFile, cacheFile.name + ".tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(cacheFile)) {
            cacheFile.delete()
            tmp.renameTo(cacheFile)
        }
    }

    companion object {
        private const val MIN_INTERVAL_MS = 1_100L
        private const val RETRY_NOT_FOUND_MS = 7L * 24 * 3600 * 1000
        // "Leipzig, Plagwitz" / "Altenburg - Schloss"; Bindestriche in Ortsnamen (Baden-Baden) bleiben erhalten.
        private val PART_SEPARATOR = Regex(",|/| - | – ")
        private val ONLINE_WORDS = listOf("online", "livestream", "virtuell", "digital")
    }
}
