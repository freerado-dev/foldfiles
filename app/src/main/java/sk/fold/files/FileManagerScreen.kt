package sk.fold.files

import android.content.ClipData
import android.content.ClipDescription
import android.content.Intent
import android.text.format.DateFormat
import android.text.format.Formatter
import android.view.View
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import java.io.File

/** Značka, podľa ktorej spoznáme ťahanie z tejto aplikácie. */
private const val CLIP_LABEL = "foldfiles-paths"

/** Šírka, od ktorej sú panely vedľa seba (rozložený Fold). */
private val WIDE_BREAKPOINT = 600.dp

private sealed interface DialogState {
    data class NewFolder(val pane: Int) : DialogState
    data class Rename(val file: File) : DialogState
    data class Delete(val pane: Int, val count: Int) : DialogState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(vm: FileManagerViewModel = viewModel()) {
    val snackbar = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<DialogState?>(null) }

    LaunchedEffect(Unit) { vm.refreshAll() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    BackHandler(enabled = vm.panes[vm.active].selected.isNotEmpty() || vm.canGoUp(vm.active)) {
        if (vm.panes[vm.active].selected.isNotEmpty()) vm.clearSelection(vm.active) else vm.up(vm.active)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fold Files") },
                actions = {
                    FilterChip(
                        selected = vm.moveOnDrop,
                        onClick = { vm.moveOnDrop = !vm.moveOnDrop },
                        label = { Text(if (vm.moveOnDrop) "Ťahanie: presun" else "Ťahanie: kópia") },
                        leadingIcon = {
                            Icon(
                                if (vm.moveOnDrop) Icons.Filled.ContentCut else Icons.Filled.ContentCopy,
                                null, Modifier.size(18.dp)
                            )
                        }
                    )
                    IconButton(onClick = { dialog = DialogState.NewFolder(vm.active) }) {
                        Icon(Icons.Filled.CreateNewFolder, "Nový priečinok")
                    }
                    IconButton(onClick = { vm.showHidden = !vm.showHidden; vm.refreshAll() }) {
                        Icon(
                            if (vm.showHidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            "Skryté súbory"
                        )
                    }
                    IconButton(onClick = { vm.refreshAll() }) {
                        Icon(Icons.Filled.Refresh, "Obnoviť")
                    }
                }
            )
        },
        bottomBar = {
            val sel = vm.panes[vm.active].selected
            if (sel.isNotEmpty()) {
                SelectionBar(
                    count = sel.size,
                    onCopy = { vm.transferSelection(vm.active, move = false) },
                    onMove = { vm.transferSelection(vm.active, move = true) },
                    onRename = if (sel.size == 1) {
                        { dialog = DialogState.Rename(File(sel.first())) }
                    } else null,
                    onDelete = { dialog = DialogState.Delete(vm.active, sel.size) },
                    onSelectAll = { vm.selectAll(vm.active) },
                    onClear = { vm.clearSelection(vm.active) }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            BoxWithConstraints(Modifier.fillMaxSize()) {
                if (maxWidth >= WIDE_BREAKPOINT) {
                    // Rozložený Fold: panely vedľa seba
                    Row(Modifier.fillMaxSize()) {
                        Pane(vm, 0, Modifier.weight(1f))
                        VerticalDivider()
                        Pane(vm, 1, Modifier.weight(1f))
                    }
                } else {
                    // Vonkajší displej: panely pod sebou, ťahanie funguje aj tu
                    Column(Modifier.fillMaxSize()) {
                        Pane(vm, 0, Modifier.weight(1f))
                        HorizontalDivider()
                        Pane(vm, 1, Modifier.weight(1f))
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        is DialogState.NewFolder -> NameDialog(
            title = "Nový priečinok", initial = "",
            onDismiss = { dialog = null },
            onConfirm = { vm.mkdir(d.pane, it); dialog = null }
        )
        is DialogState.Rename -> NameDialog(
            title = "Premenovať", initial = d.file.name,
            onDismiss = { dialog = null },
            onConfirm = { vm.rename(d.file, it); dialog = null }
        )
        is DialogState.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Zmazať ${d.count} položiek?") },
            text = { Text("Súbory sa zmažú natrvalo, nepresúvajú sa do koša.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteSelection(d.pane); dialog = null }) { Text("Zmazať") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Zrušiť") } }
        )
        null -> Unit
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Pane(vm: FileManagerViewModel, index: Int, modifier: Modifier) {
    val pane = vm.panes[index]
    val isActive = vm.active == index
    val context = LocalContext.current
    var dropHover by remember { mutableStateOf(false) }

    // Pustenie kamkoľvek do panelu = do jeho aktuálneho priečinka
    val paneTarget = rememberDropTarget(onHover = { dropHover = it }) { paths ->
        vm.active = index
        vm.drop(paths, pane.dir)
    }

    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxSize()
            .padding(4.dp)
            .border(
                width = if (isActive) 2.dp else 1.dp,
                color = if (isActive) colors.primary else colors.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .background(
                if (dropHover) colors.primaryContainer.copy(alpha = 0.5f) else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .pointerInput(index) {
                // Dotyk kdekoľvek v paneli ho aktivuje (bez zachytenia gesta)
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    vm.active = index
                }
            }
            .dragAndDropTarget(shouldStartDragAndDrop = ::isOurDrag, target = paneTarget)
    ) {
        PathHeader(
            dir = pane.dir,
            root = vm.root,
            count = pane.entries.size,
            canGoUp = vm.canGoUp(index),
            onUp = { vm.up(index) },
            onHome = { vm.open(index, vm.root) }
        )
        HorizontalDivider()
        if (pane.entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Prázdny priečinok", color = colors.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(pane.entries, key = { it.path }) { f ->
                    FileRow(
                        file = f,
                        selected = f.path in pane.selected,
                        selectionMode = pane.selected.isNotEmpty(),
                        onOpen = {
                            if (f.isDirectory) vm.open(index, f) else openFile(context, f)
                        },
                        onToggleSelect = { vm.toggleSelect(index, f) },
                        dragPaths = { vm.dragPaths(index, f) },
                        onDropInto = { paths -> vm.active = index; vm.drop(paths, f) },
                        onHoverOpen = { vm.open(index, f) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PathHeader(
    dir: File, root: File, count: Int, canGoUp: Boolean,
    onUp: () -> Unit, onHome: () -> Unit
) {
    val rel = dir.path.removePrefix(root.path).trim('/')
    val label = if (rel.isEmpty()) "Úložisko" else "Úložisko / " + rel.replace("/", " / ")
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onUp, enabled = canGoUp) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Hore")
        }
        Column(Modifier.weight(1f)) {
            Text(
                dir.name.ifEmpty { "/" }.let { if (rel.isEmpty()) "Úložisko" else it },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                "$label · $count",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onHome) { Icon(Icons.Filled.Home, "Domov") }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    file: File,
    selected: Boolean,
    selectionMode: Boolean,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
    dragPaths: () -> List<String>,
    onDropInto: (List<String>) -> Unit,
    onHoverOpen: () -> Unit
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val currentOnOpen by rememberUpdatedState(onOpen)
    val currentToggle by rememberUpdatedState(onToggleSelect)
    val currentSelectionMode by rememberUpdatedState(selectionMode)
    val currentDragPaths by rememberUpdatedState(dragPaths)

    var hover by remember { mutableStateOf(false) }
    val folderTarget = rememberDropTarget(onHover = { hover = it }, onDrop = onDropInto)

    // Podržanie súboru nad priečinkom ~1 s ho otvorí (pustiť sa dá potom do panelu)
    if (file.isDirectory) {
        LaunchedEffect(hover) {
            if (hover) {
                delay(1000)
                hover = false
                onHoverOpen()
            }
        }
    }

    val bg = when {
        hover -> colors.primaryContainer
        selected -> colors.secondaryContainer
        else -> Color.Transparent
    }

    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (file.isDirectory) Modifier.dragAndDropTarget(::isOurDrag, folderTarget)
                else Modifier
            )
            .background(bg)
            .dragAndDropSource {
                detectTapGestures(
                    onTap = { if (currentSelectionMode) currentToggle() else currentOnOpen() },
                    onLongPress = {
                        // Dlhé podržanie = začiatok ťahania (vybraných položiek alebo tejto)
                        val paths = currentDragPaths()
                        val clip = ClipData(
                            CLIP_LABEL,
                            arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN),
                            ClipData.Item(paths.joinToString("\n"))
                        )
                        startTransfer(
                            DragAndDropTransferData(
                                clipData = clip,
                                flags = View.DRAG_FLAG_GLOBAL
                            )
                        )
                    }
                )
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Ťuknutie na ikonu = označiť/odznačiť
        IconButton(onClick = onToggleSelect) {
            if (selected) {
                Icon(Icons.Filled.CheckCircle, "Označené", tint = colors.primary)
            } else {
                Icon(
                    iconFor(file), null,
                    tint = if (file.isDirectory) colors.primary else colors.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val info = buildString {
                if (!file.isDirectory) append(Formatter.formatShortFileSize(context, file.length())).append(" · ")
                append(DateFormat.getDateFormat(context).format(file.lastModified()))
            }
            Text(info, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onRename: (() -> Unit)?,
    onDelete: () -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit
) {
    BottomAppBar {
        IconButton(onClick = onClear) { Icon(Icons.Filled.Close, "Zrušiť výber") }
        Text("$count", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            IconButton(onClick = onSelectAll) { Icon(Icons.Filled.SelectAll, "Označiť všetko") }
            IconButton(onClick = onCopy) { Icon(Icons.Filled.ContentCopy, "Kopírovať do druhého panelu") }
            IconButton(onClick = onMove) { Icon(Icons.Filled.ContentCut, "Presunúť do druhého panelu") }
            if (onRename != null) IconButton(onClick = onRename) { Icon(Icons.Filled.Edit, "Premenovať") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Zmazať") }
        }
    }
}

@Composable
private fun NameDialog(
    title: String, initial: String,
    onDismiss: () -> Unit, onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušiť") } }
    )
}

/* ---------- Drag & drop pomocníci ---------- */

private fun isOurDrag(event: DragAndDropEvent): Boolean =
    event.toAndroidDragEvent().clipDescription?.label?.toString() == CLIP_LABEL

@Composable
private fun rememberDropTarget(
    onHover: (Boolean) -> Unit,
    onDrop: (List<String>) -> Unit
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
                val clip = event.toAndroidDragEvent().clipData ?: return false
                val paths = (0 until clip.itemCount)
                    .mapNotNull { clip.getItemAt(it).text?.toString() }
                    .flatMap { it.lines() }
                    .filter { it.isNotBlank() }
                if (paths.isEmpty()) return false
                drop(paths)
                return true
            }
        }
    }
}

/* ---------- Ostatné ---------- */

private fun iconFor(f: File): ImageVector {
    if (f.isDirectory) return Icons.Filled.Folder
    return when (f.extension.lowercase()) {
        "jpg", "jpeg", "png", "gif", "webp", "heic", "bmp" -> Icons.Filled.Image
        "mp4", "mkv", "mov", "avi", "webm", "3gp" -> Icons.Filled.Movie
        "mp3", "m4a", "flac", "wav", "ogg", "opus", "aac" -> Icons.Filled.MusicNote
        "pdf" -> Icons.Filled.PictureAsPdf
        "zip", "rar", "7z", "tar", "gz" -> Icons.Filled.FolderZip
        "apk" -> Icons.Filled.Android
        "txt", "md", "doc", "docx", "odt", "rtf", "csv", "xls", "xlsx" -> Icons.Filled.Description
        else -> Icons.Filled.InsertDriveFile
    }
}

private fun openFile(context: android.content.Context, f: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, f.name))
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Súbor sa nedá otvoriť", android.widget.Toast.LENGTH_SHORT).show()
    }
}
