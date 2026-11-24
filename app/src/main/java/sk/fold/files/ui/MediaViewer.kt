package sk.fold.files.ui

import android.app.Activity
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sk.fold.files.FileItem
import sk.fold.files.FilesViewModel
import sk.fold.files.Kind
import sk.fold.files.ViewerState
import sk.fold.files.formatDate

/** Celoobrazovkový prehliadač fotiek a videí – prechádzanie potiahnutím do strany. */
@Composable
fun MediaViewer(vm: FilesViewModel, state: ViewerState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val items = remember(state) { state.items.toMutableStateList() }
    val pager = rememberPagerState(initialPage = state.start) { items.size }
    var bars by remember { mutableStateOf(true) }
    val zoomed = remember { mutableStateMapOf<String, Boolean>() }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }

    BackHandler { vm.viewer = null }
    ImmersiveMode(hidden = !bars)

    if (items.isEmpty()) {
        LaunchedEffect(Unit) { vm.viewer = null }
        return
    }
    val current = items[pager.currentPage.coerceIn(0, items.lastIndex)]

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            key = { items[it].path },
            userScrollEnabled = zoomed[current.path] != true
        ) { page ->
            val item = items[page]
            if (item.kind == Kind.VIDEO) {
                VideoPage(
                    item = item,
                    isCurrent = page == pager.currentPage,
                    controlsVisible = bars,
                    onToggleControls = { bars = !bars },
                    onPrev = { scope.launch { if (page > 0) pager.animateScrollToPage(page - 1) } },
                    onNext = { scope.launch { if (page < items.lastIndex) pager.animateScrollToPage(page + 1) } }
                )
            } else {
                ZoomableImage(
                    item = item,
                    onTap = { bars = !bars },
                    onZoomChanged = { zoomed[item.path] = it }
                )
            }
        }

        // Horná lišta: názov a umiestnenie ako v Solid Explorer
        AnimatedVisibility(bars, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.viewer = null }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Späť", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(current.name, color = Color.White, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        vm.displayPath(current.file.parentFile),
                        color = Color(0xFFBDBDBD), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { shareFiles(ctx, listOf(current.file)) }) {
                    Icon(Icons.Filled.Share, "Zdieľať", tint = Color.White)
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, "Zmazať", tint = Color.White)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Viac", tint = Color.White) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Otvoriť v inej aplikácii") }, onClick = {
                            menu = false; openFile(ctx, current.file)
                        })
                        DropdownMenuItem(text = { Text("Vlastnosti") }, onClick = { menu = false; details = true })
                    }
                }
            }
        }

        // Počítadlo pre fotky
        if (current.kind != Kind.VIDEO) {
            AnimatedVisibility(bars, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
                Text(
                    "${pager.currentPage + 1} / ${items.size}",
                    color = Color.White, fontSize = 14.sp,
                    modifier = Modifier.navigationBarsPadding().padding(bottom = 16.dp)
                        .background(Color(0x66000000), CircleShape).padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Presunúť do koša?") },
            text = { Text(current.name) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.trashFiles(listOf(current.file))
                    items.remove(current)
                }) { Text("ZMAZAŤ", color = BlueText) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("ZRUŠIŤ", color = TextSecondary) } }
        )
    }
    if (details) {
        AlertDialog(
            onDismissRequest = { details = false },
            title = { Text(current.name) },
            text = {
                Text(
                    "Umiestnenie: ${current.file.parent}\n" +
                        "Veľkosť: ${Formatter.formatFileSize(ctx, current.size)}\n" +
                        "Zmenené: ${formatDate(current.modified)}"
                )
            },
            confirmButton = { TextButton(onClick = { details = false }) { Text("OK", color = BlueText) } }
        )
    }
}

/** Skryje systémové lišty, keď sú skryté aj lišty prehliadača. */
@Composable
private fun ImmersiveMode(hidden: Boolean) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window ?: return
    val controller = remember(window) { WindowCompat.getInsetsController(window, view) }
    DisposableEffect(hidden) {
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.isAppearanceLightStatusBars = false
        if (hidden) controller.hide(WindowInsetsCompat.Type.systemBars())
        else controller.show(WindowInsetsCompat.Type.systemBars())
        onDispose { }
    }
    DisposableEffect(Unit) {
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.isAppearanceLightStatusBars = true
        }
    }
}

/* ---------- Fotka so zoomom ---------- */

@Composable
private fun ZoomableImage(item: FileItem, onTap: () -> Unit, onZoomChanged: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }

    fun clamp(o: Offset): Offset {
        val maxX = box.width * (scale - 1) / 2f
        val maxY = box.height * (scale - 1) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    fun setScale(s: Float) {
        scale = s.coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else clamp(offset)
        onZoomChanged(scale > 1.01f)
    }

    Box(
        Modifier.fillMaxSize()
            .onSizeChanged { box = it }
            .pointerInput(item.path) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { setScale(if (scale > 1.01f) 1f else 2.5f) }
                )
            }
            .pointerInput(item.path) {
                // Pinch zoom; posun prstom len keď je fotka priblížená (inak listuje pager)
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val multi = event.changes.count { it.pressed } > 1
                        if (multi || scale > 1.01f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            setScale(scale * zoom)
                            offset = clamp(offset + pan)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(ctx).data(item.file).size(2560).build(),
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = offset.x; translationY = offset.y
            }
        )
    }
}

/* ---------- Prehrávač videa ---------- */

private fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPage(
    item: FileItem,
    isCurrent: Boolean,
    controlsVisible: Boolean,
    onToggleControls: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val ctx = LocalContext.current
    if (!isCurrent) {
        // Mimo aktuálnej stránky len náhľad, prehrávač beží iba pre zobrazené video
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(item.file).size(1280).build(),
                contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    val player = remember(item.path) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(item.file)))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { player.pause() }

    var playing by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var seeking by remember { mutableStateOf<Float?>(null) }
    var muted by remember { mutableStateOf(false) }
    var repeat by remember { mutableStateOf(false) }

    LaunchedEffect(player) {
        while (true) {
            playing = player.isPlaying || (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING)
            position = player.currentPosition
            duration = player.duration.coerceAtLeast(0)
            delay(250)
        }
    }

    Box(
        Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { onToggleControls() }) },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    this.player = player
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(controlsVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Color(0x33000000))) {
                // Stredné ovládanie: predchádzajúce, -10 s, play/pauza, +10 s, ďalšie
                Row(
                    Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    CtrlButton(Icons.Filled.SkipPrevious, "Predchádzajúce", 34) { onPrev() }
                    CtrlButton(Icons.Filled.Replay10, "Späť o 10 s", 34) {
                        player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
                    }
                    Box(
                        Modifier.size(72.dp).clip(CircleShape).background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = {
                            if (player.isPlaying) player.pause()
                            else {
                                if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                                player.play()
                            }
                        }) {
                            Icon(
                                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                "Prehrať/pauza", tint = Color.Black, modifier = Modifier.size(42.dp)
                            )
                        }
                    }
                    CtrlButton(Icons.Filled.Forward10, "Dopredu o 10 s", 34) {
                        player.seekTo(player.currentPosition + 10_000)
                    }
                    CtrlButton(Icons.Filled.SkipNext, "Ďalšie", 34) { onNext() }
                }

                // Spodná časť: posuvník, čas, zvuk, opakovanie
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xAA000000))))
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Slider(
                        value = seeking ?: if (duration > 0) position.toFloat() / duration else 0f,
                        onValueChange = { seeking = it },
                        onValueChangeFinished = {
                            seeking?.let { player.seekTo((it * duration).toLong()) }
                            seeking = null
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color(0x66FFFFFF)
                        )
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val shown = seeking?.let { (it * duration).toLong() } ?: position
                        Text("${fmtTime(shown)} · ${fmtTime(duration)}", color = Color.White, fontSize = 14.sp)
                        Spacer(Modifier.weight(1f))
                        CtrlButton(
                            if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            "Zvuk", 24
                        ) {
                            muted = !muted
                            player.volume = if (muted) 0f else 1f
                        }
                        Spacer(Modifier.width(4.dp))
                        CtrlButton(if (repeat) Icons.Filled.RepeatOne else Icons.Filled.Repeat, "Opakovať", 24,
                            tint = if (repeat) Amber else Color.White) {
                            repeat = !repeat
                            player.repeatMode = if (repeat) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CtrlButton(icon: ImageVector, label: String, sizeDp: Int, tint: Color = Color.White, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size((sizeDp + 16).dp)) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(sizeDp.dp))
    }
}
