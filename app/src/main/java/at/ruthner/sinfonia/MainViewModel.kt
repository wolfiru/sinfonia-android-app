package at.ruthner.sinfonia

import android.app.Application
import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    // ---- Katalog ----
    var albums by mutableStateOf<List<Album>>(emptyList()); private set
    var loading by mutableStateOf(true); private set
    var loadError by mutableStateOf<String?>(null); private set
    private var byKey: Map<String, Track> = emptyMap()

    val allTracks: List<Track> get() = albums.flatMap { it.tracks }

    // ---- Favoriten ----
    private val prefs = app.getSharedPreferences("sinfonia", Context.MODE_PRIVATE)
    var favorites by mutableStateOf(prefs.getStringSet("favs", emptySet())!!.toSet()); private set
    val favoriteTracks: List<Track> get() = allTracks.filter { it.key in favorites }

    fun toggleFavorite(t: Track) {
        favorites = if (t.key in favorites) favorites - t.key else favorites + t.key
        prefs.edit().putStringSet("favs", favorites).apply()
    }

    // ---- Wiedergabezustand (gespiegelt vom Dienst) ----
    var controller: MediaController? = null; private set
    private var future: ListenableFuture<MediaController>? = null
    var currentTrack by mutableStateOf<Track?>(null); private set
    var isPlaying by mutableStateOf(false); private set
    var isBuffering by mutableStateOf(false); private set
    var shuffle by mutableStateOf(false); private set
    var repeatAll by mutableStateOf(false); private set
    var durationMs by mutableStateOf(0L); private set

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync(player)
    }

    init {
        applyAlbums(CatalogRepo.cached(app))
        refresh()
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val f = MediaController.Builder(app, token).buildAsync()
        future = f
        f.addListener({
            runCatching { f.get() }.getOrNull()?.let { c ->
                controller = c
                c.addListener(listener)
                sync(c)
            }
        }, ContextCompat.getMainExecutor(app))
    }

    private fun applyAlbums(list: List<Album>?) {
        if (list == null) return
        albums = list
        byKey = list.flatMap { it.tracks }.associateBy { it.key }
        controller?.let { sync(it) }
    }

    fun refresh() {
        loading = true
        loadError = null
        viewModelScope.launch {
            runCatching { CatalogRepo.fetch(getApplication()) }
                .onSuccess { applyAlbums(it) }
                .onFailure { if (albums.isEmpty()) loadError = it.message ?: it.javaClass.simpleName }
            loading = false
        }
    }

    private fun sync(p: Player) {
        currentTrack = p.currentMediaItem?.mediaId?.let { byKey[it] }
        isPlaying = p.isPlaying
        isBuffering = p.playbackState == Player.STATE_BUFFERING && p.playWhenReady
        shuffle = p.shuffleModeEnabled
        repeatAll = p.repeatMode == Player.REPEAT_MODE_ALL
        durationMs = p.duration.takeIf { it > 0 } ?: ((currentTrack?.durationSec ?: 0) * 1000L)
    }

    // ---- Steuerung ----
    fun play(tracks: List<Track>, startIndex: Int, shuffled: Boolean, repeat: Boolean) {
        val c = controller ?: return
        if (tracks.isEmpty()) return
        val items: List<MediaItem> = tracks.map { it.toMediaItem() }
        c.shuffleModeEnabled = shuffled
        c.repeatMode = if (repeat) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        c.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
        c.prepare()
        c.play()
    }

    /** Radio: alle Songs gemischt, endlos. */
    fun playRadio() {
        val all = allTracks
        if (all.isNotEmpty()) play(all, all.indices.random(), shuffled = true, repeat = true)
    }

    fun playFavorites() {
        val f = favoriteTracks
        if (f.isNotEmpty()) play(f, f.indices.random(), shuffled = true, repeat = true)
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.playWhenReady && c.playbackState != Player.STATE_ENDED) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(0)
            c.play()
        }
    }

    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPrevious() }
    fun seekTo(ms: Long) { controller?.seekTo(ms) }
    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun toggleRepeat() {
        controller?.let {
            it.repeatMode = if (it.repeatMode == Player.REPEAT_MODE_ALL) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ALL
        }
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        controller = null
    }
}
