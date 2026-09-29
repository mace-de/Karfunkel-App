package de.oliverpekel.karfunkel.data

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset

/** Lädt die Kalenderseite und hält den letzten erfolgreich gelesenen Stand für den Offline-Betrieb vor. */
class KalenderRepository(context: Context) {

    data class Snapshot(val events: List<Event>, val fetchedAt: Long)

    private val cacheFile = File(context.filesDir, "kalender.html")

    fun loadCached(): Snapshot? = runCatching {
        if (!cacheFile.exists()) return null
        Snapshot(KalenderParser.parse(cacheFile.readText()), cacheFile.lastModified())
    }.getOrNull()

    /** Holt die aktuelle Seite. Der Zwischenspeicher wird nur überschrieben, wenn sie sich lesen ließ. */
    fun fetch(): Snapshot {
        val conn = (URL(SOURCE_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Server antwortet mit HTTP ${conn.responseCode}")
            }
            val charset = Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE)
                .find(conn.contentType.orEmpty())?.groupValues?.get(1)
                ?.let { runCatching { Charset.forName(it) }.getOrNull() }
                ?: Charsets.UTF_8
            val html = conn.inputStream.use { String(it.readBytes(), charset) }
            val events = KalenderParser.parse(html)

            val tmp = File(cacheFile.parentFile, cacheFile.name + ".tmp")
            tmp.writeText(html)
            if (!tmp.renameTo(cacheFile)) {
                cacheFile.delete()
                tmp.renameTo(cacheFile)
            }
            return Snapshot(events, System.currentTimeMillis())
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val SOURCE_URL =
            "https://www.karfunkel.de/Kalender/Kalender_Karfunkel/kalender.php?kal_Aktion=druck&kal_Popup=1"
        const val USER_AGENT = "KarfunkelKarte/1.0 (Android-App; de.oliverpekel.karfunkel)"
    }
}
