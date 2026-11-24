package sk.fold.files.ui

import android.content.ClipData
import android.content.ClipDescription
import android.view.View
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import sk.fold.files.FileItem
import sk.fold.files.Kind
import sk.fold.files.Repo
import sk.fold.files.childLabel
import sk.fold.files.formatDate
import android.text.format.Formatter
import java.io.File

/* ---------- Drag & drop ---------- */

const val CLIP_LABEL = "foldfiles-paths"

fun isOurDrag(e: DragAndDropEvent): Boolean =
    e.toAndroidDragEvent().clipDescription?.label?.toString() == CLIP_LABEL

private fun clipFor(pane: Int, paths: List<String>) = ClipData(
    CLIP_LABEL,
    arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN),
    ClipData.Item("pane=$pane\n" + paths.joinToString("\n"))
)

/** Vráti (panel, z ktorého sa ťahá; cesty). */
fun parseDrop(e: DragAndDropEvent): Pair<Int, List<String>>? {
    val clip = e.toAndroidDragEvent().clipData ?: return null
    val lines = (0 until clip.itemCount)
        .mapNotNull { clip.getItemAt(it).text?.toString() }
        .flatMap { it.lines() }
        .filter { it.isNotBlank() }
    val pane = lines.firstOrNull()?.takeIf { it.startsWith("pane=") }?.removePrefix("pane=")?.toIntOrNull() ?: return null
    val paths = lines.drop(1)
    return if (paths.isEmpty()) null else pane to paths
}

@Composable
fun rememberDropTarget(
    onHover: (Boolean) -> Unit,
    onDrop: (fromPane: Int, paths: List<String>) -> Boolean
): DragAndDropTarget {
    val hover by rememberUpdatedState(onHover)
    val drop by rememberUpdatedState(onDrop)
    return remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) = hover(true)
            override fun onExited(event: DragAndDropEvent) = hover(false)
            override fun onEnded(event: DragAndDropEvent) = hover(false)
            override fun onDrop(event: DragAndDropEvent): Boolean {
                hover(false)
                val (pane, paths) = parseDrop(event) ?: return false
                return drop(pane, paths)
            }
        }
    }
}

/** Ťahaný „tieň“: modrý krúžok s ikonou a počtom, ako v Solid Explorer. */
private fun DrawScope.drawDragBadge(painter: VectorPainter, count: Int, tm: TextMeasurer) {
    val r = 30.dp.toPx()
    val c = center
    drawCircle(Blue, r, c)
    val s = 28.dp.toPx()
    translate(c.x - s / 2, c.y - s / 2) {
        with(painter) { draw(Size(s, s), colorFilter = ColorFilter.tint(Color.White)) }
    }
    if (count > 1) {
        val br = 12.dp.toPx()
        val bc = Offset(c.x + r * 0.72f, c.y - r * 0.72f)
        drawCircle(Amber, br, bc)
        val layout = tm.measure(
            count.toString(),
            TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        )
        drawText(layout, topLeft = Offset(bc.x - layout.size.width / 2f, bc.y - layout.size.height / 2f))
    }
}

/**
 * Spoločné gestá položky: ťuknutie, dlhé podržanie (označí a začne ťahanie),
 * pri priečinku aj pustenie dovnútra a otvorenie po podržaní nad ním.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGestures(
    item: FileItem,
    pane: Int,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    dragPaths: () -> List<String>,
    onDropInto: ((fromPane: Int, paths: List<String>) -> Boolean)?,
    onHoverOpen: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(hover: Boolean) -> Unit
) {
    val tap by rememberUpdatedState(onTap)
    val longPress by rememberUpdatedState(onLongPress)
    val paths by rememberUpdatedState(dragPaths)
    val hoverOpen by rememberUpdatedState(onHoverOpen)
    var hover by remember { mutableStateOf(false) }
    var dragCount by remember { mutableIntStateOf(1) }

    val target = if (onDropInto != null) {
        rememberDropTarget(onHover = { hover = it }, onDrop = onDropInto)
    } else null

    if (onHoverOpen != null) {
        LaunchedEffect(hover) {
            if (hover) {
                delay(1000)
                hover = false
                hoverOpen?.invoke()
            }
        }
    }

    val painter = rememberVectorPainter(if (item.isDir) Icons.Filled.Folder else Icons.Filled.InsertDriveFile)
    val tm = rememberTextMeasurer()

    Box(
        modifier
            .then(if (target != null) Modifier.dragAndDropTarget(::isOurDrag, target) else Modifier)
            .dragAndDropSource(drawDragDecoration = { drawDragBadge(painter, dragCount, tm) }) {
                detectTapGestures(
                    onTap = { tap() },
                    onLongPress = {
                        longPress()
                        val p = paths()
                        dragCount = p.size
                        startTransfer(
                            DragAndDropTransferData(clipFor(pane, p), flags = View.DRAG_FLAG_GLOBAL)
                        )
                    }
                )
            }
    ) { content(hover) }
}

/* ---------- Náhľady ---------- */

private fun kindIcon(k: Kind): ImageVector = when (k) {
    Kind.FOLDER -> Icons.Outlined.Folder
    Kind.IMAGE -> Icons.Filled.Image
    Kind.VIDEO -> Icons.Filled.Movie
    Kind.AUDIO -> Icons.Filled.MusicNote
    Kind.APK -> Icons.Filled.Android
    Kind.PDF -> Icons.Filled.PictureAsPdf
    Kind.ARCHIVE -> Icons.Filled.FolderZip
    Kind.DOC -> Icons.Filled.Description
    Kind.OTHER -> Icons.Filled.InsertDriveFile
}

private fun kindColor(k: Kind): Color = when (k) {
    Kind.FOLDER -> Blue
    Kind.AUDIO -> Color(0xFF8E24AA)
    Kind.PDF -> Color(0xFFE53935)
    Kind.ARCHIVE -> Color(0xFF6D4C41)
    Kind.DOC -> Color(0xFF546E7A)
    Kind.APK -> Color(0xFF7CB342)
    Kind.IMAGE, Kind.VIDEO -> Color(0xFF26A69A)
    Kind.OTHER -> Color(0xFF90A4AE)
}

@Composable
private fun rememberApkIcon(file: File): ImageBitmap? {
    val ctx = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, file.path, file.lastModified()) {
        value = withContext(Dispatchers.IO) { Repo.apkIcon(ctx, file) }
    }
    return icon
}

@Composable
fun Thumb(item: FileItem, size: Dp) {
    val ctx = LocalContext.current
    when {
        item.isMedia -> Box(
            Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(Color(0xFFDDDDDD)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(item.file).size(240).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (item.kind == Kind.VIDEO) PlayBadge(size * 0.42f)
        }
        item.kind == Kind.APK -> {
            val icon = rememberApkIcon(item.file)
            if (icon != null) {
                Image(icon, null, Modifier.size(size))
            } else KindBox(item.kind, size)
        }
        else -> KindBox(item.kind, size)
    }
}

@Composable
private fun KindBox(kind: Kind, size: Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.24f)).background(kindColor(kind)),
        contentAlignment = Alignment.Center
    ) {
        Icon(kindIcon(kind), null, tint = Color.White, modifier = Modifier.size(size * 0.54f))
    }
}

@Composable
private fun PlayBadge(size: Dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Color(0x99000000)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(size * 0.7f))
    }
}

/* ---------- Riadok zoznamu ---------- */

@Composable
fun SectionHeader(title: String) {
    Text(
        title,
        color = Color(0xFF555555),
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().background(SectionBg).padding(horizontal = 14.dp, vertical = 7.dp)
    )
}

@Composable
fun FileListRow(item: FileItem, selected: Boolean, hover: Boolean) {
    val ctx = LocalContext.current
    val bg = when {
        hover -> Color(0xFFBFE3F7)
        selected -> SelectedBg
        else -> Color.Transparent
    }
    Column(Modifier.fillMaxWidth().background(bg)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                Thumb(item, 44.dp)
                if (selected) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Blue),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Check, null, tint = Color.White) }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.name, color = TextPrimary, fontSize = 17.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 21.sp
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (item.isDir) childLabel(item.childCount)
                        else Formatter.formatShortFileSize(ctx, item.size),
                        color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1
                    )
                    Text(formatDate(item.modified), color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
        HorizontalDivider(Modifier.padding(start = 72.dp), color = DividerColor)
    }
}

/* ---------- Bunka mriežky ---------- */

@Composable
fun FileGridCell(item: FileItem, selected: Boolean, selectionMode: Boolean, hover: Boolean) {
    val ctx = LocalContext.current
    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).padding(1.dp)
            .background(if (item.isMedia) Color(0xFFDDDDDD) else Color.White)
    ) {
        if (item.isMedia) {
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(item.file).size(360).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (item.kind == Kind.VIDEO) {
                Box(Modifier.align(Alignment.Center)) { PlayBadge(40.dp) }
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xAA000000))))
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text(item.name, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Thumb(item, 52.dp)
                Spacer(Modifier.height(6.dp))
                Text(
                    item.name, color = TextPrimary, fontSize = 13.sp, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, lineHeight = 16.sp
                )
            }
        }
        if (selected) Box(Modifier.fillMaxSize().background(Blue.copy(alpha = 0.28f)))
        if (hover) Box(Modifier.fillMaxSize().background(Blue.copy(alpha = 0.35f)))
        if (selectionMode) {
            Box(
                Modifier.align(Alignment.TopStart).padding(7.dp).size(20.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (selected) Blue else Color(0x33000000))
                    .border(1.5.dp, if (selected) Blue else Color.White, RoundedCornerShape(3.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (selected) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}
