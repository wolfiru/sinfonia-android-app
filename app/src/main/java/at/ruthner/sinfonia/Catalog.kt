package at.ruthner.sinfonia

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

const val BASE_URL = "https://sinfonia.ruthner.at"
const val USER_AGENT = "SinfoniaTechnicaApp/1.0 (Android)"

data class Track(
    val album: Int,
    val slug: String,
    val title: String,
    val durationSec: Int,
    val albumLabel: String,
    val albumSub: String,
) {
    val key: String get() = "$album/$slug"
    val url: String get() = "$BASE_URL/sinfonia-technica-vol$album/songs/$slug.mp3"
    val cover: String get() = "$BASE_URL/sinfonia-technica-vol$album/cover-512.jpg"

    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(key)
        .setUri(url)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist("Sinfonia Technica · $albumLabel")
                .setAlbumTitle("Sinfonia Technica $albumLabel")
                .setArtworkUri(Uri.parse(cover))
                .build()
        )
        .build()
}

data class Album(
    val number: Int,
    val label: String,
    val sub: String,
    val tracks: List<Track>,
) {
    val cover: String get() = "$BASE_URL/sinfonia-technica-vol$number/cover-512.jpg"
}

/**
 * Lädt die Albumliste von der Website (dieselbe Datei, die auch das Web-Radio nutzt).
 * Neue Alben erscheinen dadurch von selbst. Die letzte Fassung bleibt am Gerät,
 * damit die App auch ohne Netz startet.
 */
object CatalogRepo {
    private const val FILE = "catalog.json"

    fun parse(json: String): List<Album> {
        val albums = JSONObject(json).getJSONObject("albums")
        val out = ArrayList<Album>()
        for (k in albums.keys()) {
            val n = k.toIntOrNull() ?: continue
            val a = albums.getJSONObject(k)
            val label = a.optString("label", "Vol. $n")
            val sub = a.optString("sub", "")
            val arr = a.getJSONArray("tracks")
            val tracks = ArrayList<Track>(arr.length())
            for (i in 0 until arr.length()) {
                val t = arr.getJSONObject(i)
                tracks += Track(n, t.getString("slug"), t.getString("title"), t.optInt("dur", 0), label, sub)
            }
            out += Album(n, label, sub, tracks)
        }
        return out.sortedBy { it.number }
    }

    fun cached(context: Context): List<Album>? = runCatching {
        parse(File(context.filesDir, FILE).readText())
    }.getOrNull()?.takeIf { it.isNotEmpty() }

    suspend fun fetch(context: Context): List<Album> = withContext(Dispatchers.IO) {
        val conn = URL("$BASE_URL/sinfonia-catalog.json").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val albums = parse(text)
            if (albums.isEmpty()) error("Katalog ist leer")
            File(context.filesDir, FILE).writeText(text)
            albums
        } finally {
            conn.disconnect()
        }
    }
}
