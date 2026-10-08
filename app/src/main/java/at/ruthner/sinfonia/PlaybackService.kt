package at.ruthner.sinfonia

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/** Einschlaf-Timer. Oberfläche und Dienst laufen im selben Prozess und teilen sich diesen Zustand. */
sealed interface Sleep {
    data object Off : Sleep
    data object EndOfTrack : Sleep
    data class Until(val atMillis: Long) : Sleep
}

object SleepTimer {
    val state = MutableStateFlow<Sleep>(Sleep.Off)
    internal var apply: ((Sleep) -> Unit)? = null

    fun set(s: Sleep) {
        state.value = s
        apply?.invoke(s)
    }
}

@OptIn(UnstableApi::class)
private object AudioCache {
    private var cache: SimpleCache? = null

    /** Bis zu 500 MB bereits gehörter Songs bleiben am Gerät und brauchen beim nächsten Mal kein Netz. */
    @Synchronized
    fun get(context: Context): SimpleCache = cache ?: SimpleCache(
        File(context.cacheDir, "audio"),
        LeastRecentlyUsedCacheEvictor(500L * 1024 * 1024),
        StandaloneDatabaseProvider(context.applicationContext),
    ).also { cache = it }
}

/**
 * Die Wiedergabe läuft in diesem Vordergrunddienst mit Mediensitzung. Deshalb spielt die Musik
 * bei ausgeschaltetem Bildschirm und geschlossener App weiter, anders als ein Browser-Tab.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private var retries = 0
    private var skips = 0

    private val sleepRunnable = Runnable {
        player.pause()
        SleepTimer.state.value = Sleep.Off
    }
    private val retryRunnable = Runnable {
        player.prepare()
        player.play()
    }

    override fun onCreate() {
        super.onCreate()

        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(USER_AGENT)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
            .setAllowCrossProtocolRedirects(true)
        val cached = CacheDataSource.Factory()
            .setCache(AudioCache.get(this))
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(this, http))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(this)
                    .setDataSourceFactory(cached)
                    .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(6))
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            // hält CPU und WLAN wach, solange gespielt wird
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // Funkloch o. Ä.: denselben Song ein paar Mal neu versuchen, dann zum nächsten gehen.
                handler.removeCallbacks(retryRunnable)
                if (retries < 5) {
                    retries++
                    handler.postDelayed(retryRunnable, 4_000)
                } else if (skips < 10 && player.hasNextMediaItem()) {
                    skips++
                    retries = 0
                    player.seekToNextMediaItem()
                    handler.postDelayed(retryRunnable, 1_000)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    retries = 0
                    skips = 0
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                retries = 0
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady) handler.removeCallbacks(retryRunnable)
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                    // "Ende des Titels" hat ausgelöst
                    player.pauseAtEndOfMediaItems = false
                    SleepTimer.state.value = Sleep.Off
                }
            }
        })

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player).setSessionActivity(open).build()

        SleepTimer.apply = ::applySleep
        applySleep(SleepTimer.state.value)
    }

    private fun applySleep(s: Sleep) {
        handler.removeCallbacks(sleepRunnable)
        player.pauseAtEndOfMediaItems = s is Sleep.EndOfTrack
        if (s is Sleep.Until) {
            handler.postDelayed(sleepRunnable, (s.atMillis - System.currentTimeMillis()).coerceAtLeast(0))
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // App aus der Übersicht gewischt: weiterspielen, wenn gerade Musik läuft, sonst beenden.
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        SleepTimer.apply = null
        SleepTimer.state.value = Sleep.Off
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
