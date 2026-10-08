package at.ruthner.sinfonia

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import coil.request.ImageRequest
import kotlin.math.roundToInt
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

private val Ink = Color(0xFF07060D)
private val Ink2 = Color(0xFF14121F)
private val Paper = Color(0xFFF3EFE6)
private val PaperDim = Color(0xFFB3AE9F)
private val Gold = Color(0xFFD9B45A)
private val Line = Color(0x22F3EFE6)
private val Glass = Color(0x0FF3EFE6)

private val Colors = darkColorScheme(
    primary = Gold, onPrimary = Ink, background = Ink, onBackground = Paper,
    surface = Ink2, onSurface = Paper, surfaceVariant = Ink2, onSurfaceVariant = PaperDim,
    surfaceContainer = Ink2, surfaceContainerLow = Ink2, surfaceContainerHigh = Ink2,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = Colors) {
                ProvideTextStyle(TextStyle(fontFamily = Body)) { App() }
            }
        }
    }
}

private fun fmt(sec: Long): String = "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"

@Composable
private fun App(vm: MainViewModel = viewModel()) {
    // -1 = Start, 0 = Favoriten, sonst Albumnummer
    var screen by rememberSaveable { mutableStateOf(-1) }
    var showPlayer by rememberSaveable { mutableStateOf(false) }

    // Ab Android 13 braucht die Wiedergabe-Benachrichtigung eine Erlaubnis. Gespielt wird auch ohne.
    val context = LocalContext.current
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    var asked by rememberSaveable { mutableStateOf(false) }
    val start: (() -> Unit) -> Unit = { action ->
        if (!asked && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            asked = true
            askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        action()
    }

    BackHandler(enabled = screen != -1) { screen = -1 }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF15111F), Ink, Ink)))
    ) {
        CoverBackdrop(vm.currentTrack?.cover, strength = 0.55f)
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).statusBarsPadding()) {
                val album = vm.albums.firstOrNull { it.number == screen }
                when {
                    vm.albums.isEmpty() -> EmptyState(vm)
                    screen == 0 -> TrackScreen(
                        vm, title = "Meine Favoriten", sub = "${vm.favoriteTracks.size} Songs", cover = null,
                        tracks = vm.favoriteTracks, onBack = { screen = -1 }, start = start,
                    )
                    album != null -> TrackScreen(
                        vm, title = "Sinfonia Technica ${album.label}", sub = album.sub, cover = album.cover,
                        tracks = album.tracks, onBack = { screen = -1 }, start = start,
                    )
                    else -> Home(vm, open = { screen = it }, start = start)
                }
            }
            if (vm.currentTrack != null) MiniPlayer(vm, onOpen = { showPlayer = true })
            else Spacer(Modifier.navigationBarsPadding())
        }

        AnimatedVisibility(
            visible = showPlayer && vm.currentTrack != null,
            enter = slideInVertically(tween(320)) { it },
            exit = slideOutVertically(tween(260)) { it },
        ) {
            PlayerScreen(vm, onClose = { showPlayer = false })
        }
    }
    BackHandler(enabled = showPlayer) { showPlayer = false }
}

/**
 * Das Cover des laufenden Songs als weichgezeichneter, abgedunkelter Hintergrund.
 * Ab Android 12 echter Blur, davor reicht das winzig geladene, hochskalierte Bild.
 */
@Composable
private fun CoverBackdrop(cover: String?, strength: Float, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Crossfade(targetState = cover, animationSpec = tween(700), modifier = modifier.fillMaxSize(), label = "backdrop") { c ->
        if (c != null) {
            Box(Modifier.fillMaxSize().clipToBounds()) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(c).size(96).build(),
                    contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(40.dp, BlurredEdgeTreatment.Unbounded).alpha(strength),
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Ink.copy(alpha = 0.35f), Ink.copy(alpha = 0.75f), Ink.copy(alpha = 0.95f)))
                    )
                )
            }
        }
    }
}

@Composable
private fun EmptyState(vm: MainViewModel) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (vm.loading) {
            CircularProgressIndicator(color = Gold)
        } else {
            Text("Die Albumliste konnte nicht geladen werden.", color = Paper, textAlign = TextAlign.Center)
            vm.loadError?.let { Text(it, color = PaperDim, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
            Spacer(Modifier.height(20.dp))
            Pill("Nochmal versuchen", Ic.Refresh, filled = true) { vm.refresh() }
        }
    }
}

@Composable
private fun Pill(text: String, icon: ImageVector?, filled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) Gold else Glass)
            .border(1.dp, if (filled) Gold else Line, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (filled) Ink else Paper, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = if (filled) Ink else Paper, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
    }
}

@Composable
private fun Home(vm: MainViewModel, open: (Int) -> Unit, start: (() -> Unit) -> Unit) {
    val favCount = vm.favoriteTracks.size
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(top = 12.dp, bottom = 6.dp)) {
                Text("Sinfonia Technica", color = Paper, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 38.sp)
                Text(
                    "Klassik trifft Techno · ${vm.albums.size} Alben, ${vm.allTracks.size} Songs",
                    color = PaperDim, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(18.dp))
                Pill("Radio – alles gemischt, endlos", Ic.Shuffle, filled = true, modifier = Modifier.fillMaxWidth()) {
                    start { vm.playRadio() }
                }
                if (favCount > 0) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Pill("Favoriten spielen ($favCount)", Ic.Heart, filled = false, modifier = Modifier.weight(1f)) {
                            start { vm.playFavorites() }
                        }
                        Pill("Liste", null, filled = false) { open(0) }
                    }
                }
            }
        }
        items(vm.albums, key = { it.number }) { a ->
            Column(Modifier.clip(RoundedCornerShape(16.dp)).clickable { open(a.number) }) {
                Cover(
                    a.cover, "Cover ${a.label}",
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Ink2),
                )
                Text(a.label, color = Paper, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp, start = 2.dp))
                Text(
                    a.sub, color = PaperDim, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 2.dp, bottom = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun TrackScreen(
    vm: MainViewModel, title: String, sub: String, cover: String?, tracks: List<Track>,
    onBack: () -> Unit, start: (() -> Unit) -> Unit,
) {
    val listState = rememberLazyListState()
    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Box {
            if (cover != null) HeaderBackdrop(cover, listState, Modifier.matchParentSize())
            Column(Modifier.padding(horizontal = 16.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.padding(top = 4.dp)) {
                    Icon(Ic.Back, "Zurück", tint = Paper)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (cover != null) {
                        Cover(
                            cover, null,
                            modifier = Modifier.size(112.dp).clip(RoundedCornerShape(14.dp)).background(Ink2),
                        )
                        Spacer(Modifier.width(16.dp))
                    }
                    Column {
                        Text(title, color = Paper, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 32.sp)
                        Text(sub, color = PaperDim, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill("Abspielen", Ic.Play, filled = true, modifier = Modifier.weight(1f)) {
                        start { vm.play(tracks, 0, shuffled = false, repeat = false) }
                    }
                    Pill("Mischen", Ic.Shuffle, filled = false, modifier = Modifier.weight(1f)) {
                        start { if (tracks.isNotEmpty()) vm.play(tracks, tracks.indices.random(), shuffled = true, repeat = true) }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            }
        }
        itemsIndexed(tracks, key = { _, t -> t.key }) { i, t ->
            val active = vm.currentTrack?.key == t.key
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { start { vm.play(tracks, i, shuffled = false, repeat = false) } }
                    .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(24.dp), contentAlignment = Alignment.CenterEnd) {
                    if (active) EqualizerBars(vm.isPlaying, Modifier.size(16.dp))
                    else Text("${i + 1}", color = PaperDim, fontSize = 14.sp, textAlign = TextAlign.End)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        t.title, color = if (active) Gold else Paper, fontSize = 16.sp,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (cover == null) Text(t.albumLabel, color = PaperDim, fontSize = 12.sp)
                }
                Text(fmt(t.durationSec.toLong()), color = PaperDim, fontSize = 13.sp)
                FavButton(vm, t)
            }
        }
    }
}

@Composable
private fun FavButton(vm: MainViewModel, t: Track) {
    val on = t.key in vm.favorites
    IconButton(onClick = { vm.toggleFavorite(t) }) {
        Icon(
            if (on) Ic.Heart else Ic.HeartOutline,
            if (on) "Aus Favoriten entfernen" else "Zu Favoriten",
            tint = if (on) Gold else PaperDim, modifier = Modifier.size(22.dp),
        )
    }
}

/** Fragt die Abspielposition zweimal pro Sekunde ab, solange die Anzeige sichtbar ist. */
@Composable
private fun rememberPosition(vm: MainViewModel): Long {
    var pos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(vm.currentTrack?.key) {
        while (true) {
            pos = vm.controller?.currentPosition ?: 0L
            delay(500)
        }
    }
    return pos
}

@Composable
private fun MiniPlayer(vm: MainViewModel, onOpen: () -> Unit) {
    val t = vm.currentTrack ?: return
    val pos = rememberPosition(vm)
    val haptic = LocalHapticFeedback.current
    Surface(
        color = Color(0xCC1A1726),
        modifier = Modifier.fillMaxWidth().swipeSkip(
            onNext = { haptic.tick(); vm.next() }, onPrevious = { haptic.tick(); vm.previous() },
        ),
    ) {
        Column(Modifier.navigationBarsPadding()) {
            LinearProgressIndicator(
                progress = { if (vm.durationMs > 0) (pos.toFloat() / vm.durationMs).coerceIn(0f, 1f) else 0f },
                color = Gold, trackColor = Line, modifier = Modifier.fillMaxWidth().height(2.dp),
            )
            Row(
                Modifier.clickable(onClick = onOpen).padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cover(
                    t.cover, null,
                    modifier = Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)).background(Ink2),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, color = Paper, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.albumLabel, color = PaperDim, fontSize = 12.sp, maxLines = 1)
                }
                IconButton(onClick = { haptic.press(); vm.togglePlay() }) {
                    if (vm.isBuffering) CircularProgressIndicator(color = Gold, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    else Icon(if (vm.isPlaying) Ic.Pause else Ic.Play, if (vm.isPlaying) "Pause" else "Abspielen", tint = Paper, modifier = Modifier.size(30.dp))
                }
                IconButton(onClick = { haptic.tick(); vm.next() }) { Icon(Ic.Next, "Nächster Song", tint = Paper) }
            }
        }
    }
}

@Composable
private fun PlayerScreen(vm: MainViewModel, onClose: () -> Unit) {
    val t = vm.currentTrack ?: return
    val pos = rememberPosition(vm)
    val sleep by SleepTimer.state.collectAsState()
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val dur = vm.durationMs.coerceAtLeast(1L)

    // Nach unten wischen schließt den Player
    var offsetY by remember { mutableFloatStateOf(0f) }
    val dismissPx = with(androidx.compose.ui.platform.LocalDensity.current) { 140.dp.toPx() }
    val coverScale by animateFloatAsState(if (vm.isPlaying) 1f else 0.9f, tween(400), label = "coverScale")
    val haptic = LocalHapticFeedback.current
    var swipeX by remember { mutableFloatStateOf(0f) }
    var swiping by remember { mutableStateOf(false) }
    val coverShift by animateFloatAsState(swipeX, if (swiping) snap() else spring(), label = "coverShift")

    Box(
        Modifier
            .fillMaxSize()
            .offset { IntOffset(0, offsetY.roundToInt()) }
            .background(Ink)
            .pointerInput(Unit) {}
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { offsetY = (offsetY + it).coerceAtLeast(0f) },
                onDragStopped = { if (offsetY > dismissPx) onClose(); offsetY = 0f },
            )
    ) {
        CoverBackdrop(t.cover, strength = 0.8f)
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Ic.Down, "Schließen", tint = Paper, modifier = Modifier.size(30.dp)) }
                Text(
                    "Läuft gerade", color = PaperDim, fontSize = 13.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    EqualizerBars(vm.isPlaying, Modifier.size(22.dp))
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                Cover(
                    t.cover, "Cover",
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .aspectRatio(1f)
                        .graphicsLayer {
                            scaleX = coverScale; scaleY = coverScale
                            translationX = coverShift / 3f
                            alpha = 1f - (kotlin.math.abs(coverShift) / 900f).coerceAtMost(0.4f)
                        }
                        .swipeSkip(
                            onNext = { haptic.tick(); vm.next() }, onPrevious = { haptic.tick(); vm.previous() },
                            onDrag = { swiping = it != null; swipeX = it ?: 0f },
                        )
                        .shadow(28.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black, spotColor = Color.Black)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Ink2),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        t.title, color = Paper, fontFamily = Display, fontWeight = FontWeight.SemiBold,
                        fontSize = 28.sp, lineHeight = 32.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Text("${t.albumLabel} · ${t.albumSub}", color = PaperDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FavButton(vm, t)
            }
            Slider(
                value = if (dragging) dragValue else (pos.toFloat() / dur).coerceIn(0f, 1f),
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = { vm.seekTo((dragValue * dur).toLong()); dragging = false },
                colors = SliderDefaults.colors(thumbColor = Gold, activeTrackColor = Gold, inactiveTrackColor = Line),
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmt((if (dragging) (dragValue * dur).toLong() else pos) / 1000), color = PaperDim, fontSize = 12.sp)
                Text(fmt(vm.durationMs / 1000), color = PaperDim, fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.toggleShuffle() }) {
                    Icon(Ic.Shuffle, if (vm.shuffle) "Mischen aus" else "Mischen ein", tint = if (vm.shuffle) Gold else PaperDim)
                }
                IconButton(onClick = { haptic.tick(); vm.previous() }, modifier = Modifier.size(56.dp)) {
                    Icon(Ic.Prev, "Zurück", tint = Paper, modifier = Modifier.size(36.dp))
                }
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(Gold).clickable { haptic.press(); vm.togglePlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (vm.isBuffering) CircularProgressIndicator(color = Ink, strokeWidth = 3.dp, modifier = Modifier.size(30.dp))
                    else Icon(if (vm.isPlaying) Ic.Pause else Ic.Play, if (vm.isPlaying) "Pause" else "Abspielen", tint = Ink, modifier = Modifier.size(40.dp))
                }
                IconButton(onClick = { haptic.tick(); vm.next() }, modifier = Modifier.size(56.dp)) {
                    Icon(Ic.Next, "Nächster Song", tint = Paper, modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = { vm.toggleRepeat() }) {
                    Icon(Ic.Repeat, if (vm.repeatAll) "Wiederholen aus" else "Wiederholen ein", tint = if (vm.repeatAll) Gold else PaperDim)
                }
            }
            Spacer(Modifier.height(14.dp))
            SleepControl(sleep)
        }
    }
}

@Composable
private fun SleepControl(sleep: Sleep) {
    var open by remember { mutableStateOf(false) }
    // Restzeit laufend neu berechnen
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sleep) {
        while (sleep is Sleep.Until) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val label = when (sleep) {
        Sleep.Off -> "Einschlaf-Timer"
        Sleep.EndOfTrack -> "Stoppt nach diesem Song"
        is Sleep.Until -> "Stoppt in ${fmt(((sleep.atMillis - now) / 1000).coerceAtLeast(0))}"
    }
    Box {
        Pill(label, Ic.Moon, filled = false) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(15, 30, 45, 60, 90).forEach { m ->
                DropdownMenuItem(text = { Text("$m Minuten") }, onClick = {
                    SleepTimer.set(Sleep.Until(System.currentTimeMillis() + m * 60_000L)); open = false
                })
            }
            DropdownMenuItem(text = { Text("Ende des Songs") }, onClick = { SleepTimer.set(Sleep.EndOfTrack); open = false })
            if (sleep != Sleep.Off) {
                DropdownMenuItem(text = { Text("Timer aus") }, onClick = { SleepTimer.set(Sleep.Off); open = false })
            }
        }
    }
}


private fun HapticFeedback.tick() = performHapticFeedback(HapticFeedbackType.TextHandleMove)
private fun HapticFeedback.press() = performHapticFeedback(HapticFeedbackType.LongPress)

/** Wisch nach links = nächster Song, nach rechts = vorheriger. [onDrag] meldet die aktuelle Verschiebung (null = Ende). */
private fun Modifier.swipeSkip(onNext: () -> Unit, onPrevious: () -> Unit, onDrag: (Float?) -> Unit = {}): Modifier =
    pointerInput(Unit) {
        var total = 0f
        val threshold = 110.dp.toPx()
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = {
                if (total < -threshold) onNext() else if (total > threshold) onPrevious()
                onDrag(null)
            },
            onDragCancel = { onDrag(null) },
        ) { _, delta -> total += delta; onDrag(total) }
    }

/** Cover mit Schimmer, solange es lädt, und Notenzeichen als Ersatz, falls es fehlt. */
@Composable
private fun Cover(model: String, description: String?, modifier: Modifier = Modifier) {
    var state by remember(model) { mutableStateOf(0) } // 0 lädt, 1 fertig, 2 Fehler
    Box(modifier, contentAlignment = Alignment.Center) {
        if (state == 0) Shimmer(Modifier.matchParentSize())
        if (state == 2) Text("♪", color = Gold, fontSize = 28.sp)
        AsyncImage(
            model = model, contentDescription = description, contentScale = ContentScale.Crop,
            onSuccess = { state = 1 }, onError = { state = 2 },
            modifier = Modifier.matchParentSize(),
        )
    }
}

@Composable
private fun Shimmer(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(
        -400f, 1200f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "x",
    )
    Box(
        modifier.background(
            Brush.linearGradient(
                listOf(Ink2, Color(0xFF26223A), Ink2), start = Offset(x, 0f), end = Offset(x + 400f, 400f),
            )
        )
    )
}

/** Kleine Pegelanzeige: Balken tanzen, solange gespielt wird, sonst stehen sie niedrig. */
@Composable
private fun EqualizerBars(playing: Boolean, modifier: Modifier = Modifier, color: Color = Gold) {
    val levels = if (playing) {
        val t = rememberInfiniteTransition(label = "eq")
        listOf(520, 380, 610, 450).mapIndexed { i, ms ->
            t.animateFloat(
                0.2f, 1f,
                infiniteRepeatable(tween(ms, easing = LinearEasing), RepeatMode.Reverse, initialStartOffset = StartOffset(i * 90)),
                label = "bar$i",
            ).value
        }
    } else listOf(0.25f, 0.25f, 0.25f, 0.25f)
    Canvas(modifier) {
        val n = levels.size
        val gap = size.width * 0.12f
        val w = (size.width - gap * (n - 1)) / n
        levels.forEachIndexed { i, l ->
            val h = size.height * l
            drawRoundRect(color, Offset(i * (w + gap), size.height - h), Size(w, h), CornerRadius(w / 2))
        }
    }
}

/** Weichgezeichnetes Album-Cover hinter dem Kopf der Songliste, das beim Scrollen mit halber Geschwindigkeit wegblendet. */
@Composable
private fun HeaderBackdrop(cover: String, state: LazyListState, modifier: Modifier) {
    val context = LocalContext.current
    Box(modifier.clipToBounds()) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(cover).size(96).build(),
            contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val off = if (state.firstVisibleItemIndex == 0) state.firstVisibleItemScrollOffset.toFloat() else size.height
                    translationY = off * 0.5f
                    alpha = (1f - off / size.height).coerceIn(0f, 1f)
                }
                .blur(36.dp, BlurredEdgeTreatment.Unbounded)
                .alpha(0.7f),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(alpha = 0.9f)))
            )
        )
    }
}
