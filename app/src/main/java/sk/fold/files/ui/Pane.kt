package sk.fold.files.ui

import android.text.format.Formatter
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sk.fold.files.Collection
import sk.fold.files.FileItem
import sk.fold.files.FilesViewModel
import sk.fold.files.Location
import sk.fold.files.Row
import sk.fold.files.SortMode
import sk.fold.files.ViewMode
import sk.fold.files.countsLabel
import sk.fold.files.plural
import sk.fold.files.selectedLabel
import java.io.File

sealed interface PaneDialog {
    data class NewFolder(val pane: Int) : PaneDialog
    data class NewFile(val pane: Int) : PaneDialog
    data class Rename(val file: File) : PaneDialog
    data class Delete(val pane: Int, val count: Int, val forever: Boolean) : PaneDialog
    data class Details(val item: FileItem) : PaneDialog
    data object EmptyTrash : PaneDialog
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PaneView(
    vm: FilesViewModel,
    index: Int,
    wide: Boolean,
    onOpenDrawer: () -> Unit,
    onDialog: (PaneDialog) -> Unit,
    modifier: Modifier = Modifier
) {
    val p = vm.panes[index]
    val active = vm.active == index
    val dimmed = wide && !active
    val selectionMode = p.selected.isNotEmpty()
    val inTrash = (p.location as? Location.Coll)?.collection == Collection.TRASH
    var dropHover by remember { mutableStateOf(false) }

    // Pustenie do prázdneho miesta panelu = do jeho aktuálneho priečinka
    val paneTarget = rememberDropTarget(onHover = { dropHover = it }) { from, paths ->
        val dir = p.dir
        if (dir == null || from == index) false
        else { vm.requestDrop(paths, dir, from, index); true }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(PaneBg)
            .pointerInput(index) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    vm.active = index
                }
            }
            .drawWithContent {
                drawContent()
                if (dimmed) drawRect(Color(0x12000000))
            }
    ) {
        Column(Modifier.fillMaxSize()) {
            Surface(color = if (dimmed) InactiveHeader else Color.White, shadowElevation = 3.dp) {
                Column {
                    when {
                        selectionMode -> SelectionTopBar(vm, index)
                        p.searchOpen -> SearchTopBar(vm, index)
                        else -> NormalTopBar(vm, index, onOpenDrawer, onDialog)
                    }
                    Breadcrumbs(vm, index)
                }
            }

            val clip = vm.clip
            if (clip != null && p.dir != null) {
                Row(
                    Modifier.fillMaxWidth().background(Color(0xFFE3F2FD)).padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.ContentPaste, null, tint = Blue, modifier = Modifier.size(20.dp))
                    Text(
                        "${clip.paths.size} ${plural(clip.paths.size, "položka", "položky", "položiek")} " +
                            if (clip.move) "na presun" else "na kópiu",
                        fontSize = 14.sp, color = TextPrimary,
                        modifier = Modifier.weight(1f).padding(start = 12.dp)
                    )
                    TextButton(onClick = { vm.paste(index) }) { Text("VLOŽIŤ SEM", color = BlueText) }
                    IconButton(onClick = { vm.clip = null }) { Icon(Icons.Filled.Close, "Zrušiť", tint = TextSecondary) }
                }
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(if (dropHover) Blue.copy(alpha = 0.08f) else Color.Transparent)
                    .dragAndDropTarget(
                        shouldStartDragAndDrop = { e -> isOurDrag(e) && p.dir != null },
                        target = paneTarget
                    )
            ) {
                PaneContent(vm, index, selectionMode, inTrash)
                PaneFab(vm, index, selectionMode, inTrash, onDialog, Modifier.align(Alignment.BottomEnd))
            }

            if (selectionMode) SelectionBottomBar(vm, index, inTrash, onDialog)
        }
    }
}

/* ---------- Obsah: zoznam alebo mriežka ---------- */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PaneContent(vm: FilesViewModel, index: Int, selectionMode: Boolean, inTrash: Boolean) {
    val p = vm.panes[index]
    val ctx = LocalContext.current
    val rows = p.visibleRows()

    if (rows.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (p.loading) CircularProgressIndicator(color = Blue)
            else Text(
                when {
                    p.filter.isNotBlank() -> "Nič sa nenašlo"
                    p.location is Location.Search -> "Žiadne výsledky"
                    else -> "Prázdny priečinok"
                },
                color = TextSecondary
            )
        }
        return
    }

    @Composable
    fun Gestures(item: FileItem, content: @Composable (hover: Boolean) -> Unit) {
        FileGestures(
            item = item,
            pane = index,
            onTap = {
                when {
                    selectionMode || inTrash -> vm.toggleSelect(index, item.path)
                    item.isDir -> vm.open(index, item.file)
                    else -> openFile(ctx, item.file)
                }
            },
            onLongPress = { vm.select(index, item.path) },
            dragPaths = { (p.selected + item.path).toList() },
            onDropInto = if (item.isDir && !inTrash) {
                { from: Int, paths: List<String> ->
                    if (item.path in paths) false
                    else { vm.requestDrop(paths, item.file, from, index); true }
                }
            } else null,
            onHoverOpen = if (item.isDir && !inTrash) { { vm.open(index, item.file) } } else null
        ) { hover -> content(hover) }
    }

    // Pozícia posunu sa pamätá zvlášť pre každé miesto
    val listState = remember(p.location) { LazyListState() }
    val gridState = remember(p.location) { LazyGridState() }
    val bottomPad = PaddingValues(bottom = 96.dp)

    if (p.viewMode == ViewMode.LIST) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = bottomPad) {
            rows.forEach { row ->
                when (row) {
                    is Row.Header -> stickyHeader(key = row.key) { SectionHeader(row.title) }
                    is Row.Item -> item(key = row.item.path) {
                        Gestures(row.item) { hover ->
                            FileListRow(row.item, row.item.path in p.selected, hover)
                        }
                    }
                }
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(118.dp),
            modifier = Modifier.fillMaxSize(),
            state = gridState,
            contentPadding = bottomPad
        ) {
            rows.forEach { row ->
                when (row) {
                    is Row.Header -> item(key = row.key, span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(row.title)
                    }
                    is Row.Item -> item(key = row.item.path) {
                        Gestures(row.item) { hover ->
                            FileGridCell(row.item, row.item.path in p.selected, selectionMode, hover)
                        }
                    }
                }
            }
        }
    }
}

/* ---------- Hlavičky ---------- */

@Composable
private fun NormalTopBar(
    vm: FilesViewModel, index: Int,
    onOpenDrawer: () -> Unit, onDialog: (PaneDialog) -> Unit
) {
    val p = vm.panes[index]
    val (title, subtitle) = when (val l = p.location) {
        is Location.Dir -> "Moje súbory" to countsLabel(p.dirCount, p.fileCount)
        is Location.Coll -> l.collection.title to countsLabel(p.dirCount, p.fileCount)
        is Location.Search -> "„${l.query}“" to "${p.items.size} " +
            plural(p.items.size, "výsledok", "výsledky", "výsledkov")
    }
    var menu by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { vm.active = index; onOpenDrawer() }) {
            Icon(Icons.Filled.Menu, "Menu", tint = BlueText)
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 21.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 13.sp, color = TextSecondary, maxLines = 1)
        }
        IconButton(onClick = { p.searchOpen = true; vm.active = index }) {
            Icon(Icons.Filled.Search, "Hľadať", tint = BlueText)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Možnosti", tint = BlueText) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                fun check(on: Boolean): @Composable (() -> Unit)? =
                    if (on) { { Icon(Icons.Filled.Check, null, tint = BlueText) } } else null
                DropdownMenuItem(
                    text = { Text(if (p.viewMode == ViewMode.LIST) "Zobraziť ako mriežku" else "Zobraziť ako zoznam") },
                    onClick = {
                        vm.setView(index, if (p.viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST)
                        menu = false
                    }
                )
                HorizontalDivider()
                listOf(SortMode.NAME to "Triediť podľa názvu", SortMode.DATE to "Triediť podľa dátumu", SortMode.SIZE to "Triediť podľa veľkosti")
                    .forEach { (mode, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            trailingIcon = check(p.sortMode == mode),
                            onClick = { vm.setSort(index, mode); menu = false }
                        )
                    }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Skryté súbory") },
                    trailingIcon = check(vm.showHidden),
                    onClick = { vm.toggleHidden(); menu = false }
                )
                p.dir?.let { d ->
                    DropdownMenuItem(
                        text = { Text(if (vm.isBookmarked(d)) "Odobrať zo záložiek" else "Pridať do záložiek") },
                        onClick = { vm.toggleBookmark(d); menu = false }
                    )
                }
                if ((p.location as? Location.Coll)?.collection == Collection.TRASH) {
                    DropdownMenuItem(
                        text = { Text("Vyprázdniť kôš") },
                        onClick = { onDialog(PaneDialog.EmptyTrash); menu = false }
                    )
                }
                DropdownMenuItem(text = { Text("Obnoviť") }, onClick = { vm.load(index); menu = false })
            }
        }
    }
}

@Composable
private fun SearchTopBar(vm: FilesViewModel, index: Int) {
    val p = vm.panes[index]
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { p.searchOpen = false; p.filter = "" }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Späť", tint = BlueText)
        }
        TextField(
            value = p.filter,
            onValueChange = { p.filter = it },
            placeholder = { Text("Hľadať… (Enter = aj v podpriečinkoch)", maxLines = 1) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.search(index, p.filter) }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Blue
            ),
            modifier = Modifier.weight(1f).focusRequester(focus)
        )
        if (p.filter.isNotEmpty()) {
            IconButton(onClick = { p.filter = "" }) { Icon(Icons.Filled.Close, "Vymazať", tint = TextSecondary) }
        }
    }
}

@Composable
private fun SelectionTopBar(vm: FilesViewModel, index: Int) {
    val ctx = LocalContext.current
    val p = vm.panes[index]
    val size = vm.selectedItems(index).sumOf { it.size }
    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { vm.clearSelection(index) }) {
            Icon(Icons.Filled.Close, "Zrušiť výber", tint = BlueText)
        }
        Column(Modifier.weight(1f)) {
            Text(selectedLabel(p.selected.size), fontSize = 21.sp, color = TextPrimary, maxLines = 1)
            Text(Formatter.formatShortFileSize(ctx, size), fontSize = 13.sp, color = TextSecondary)
        }
        IconButton(onClick = { vm.selectAll(index) }) { Icon(Icons.Filled.SelectAll, "Označiť všetko", tint = BlueText) }
        IconButton(onClick = { vm.invertSelection(index) }) { Icon(Icons.Filled.FlipToBack, "Invertovať výber", tint = BlueText) }
    }
}

/* ---------- Cesta (breadcrumb) ---------- */

@Composable
private fun Breadcrumbs(vm: FilesViewModel, index: Int) {
    val p = vm.panes[index]
    val crumbs: List<Pair<String, (() -> Unit)?>>
    val current: Int
    when (val l = p.location) {
        is Location.Dir -> {
            crumbs = p.trail.map { f ->
                val label = if (f.path == vm.rootOf(f).path) vm.rootTitle(f) else f.name
                label to { vm.open(index, f) }
            }
            current = p.trail.indexOfFirst { it.path == l.file.path }.coerceAtLeast(0)
        }
        is Location.Coll -> {
            crumbs = listOf("Kolekcie" to null, l.collection.title to null)
            current = 1
        }
        is Location.Search -> {
            crumbs = listOf(l.dir.name.ifEmpty { "Interná pamäť" } to { vm.open(index, l.dir) }, "Hľadanie" to null)
            current = 1
        }
    }
    val state = rememberLazyListState()
    LaunchedEffect(current, crumbs.size) { if (crumbs.isNotEmpty()) state.animateScrollToItem(current) }

    LazyRow(
        state = state,
        modifier = Modifier.fillMaxWidth().height(46.dp),
        contentPadding = PaddingValues(start = 46.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(crumbs) { i, (label, onClick) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (i > 0) Icon(Icons.Filled.ChevronRight, null, tint = TextMuted, modifier = Modifier.size(20.dp))
                Text(
                    label.uppercase(),
                    fontSize = 16.sp,
                    color = if (i == current) BlueText else TextMuted,
                    maxLines = 1,
                    modifier = Modifier
                        .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
        }
    }
}

/* ---------- Spodná lišta výberu ---------- */

@Composable
private fun SelectionBottomBar(vm: FilesViewModel, index: Int, inTrash: Boolean, onDialog: (PaneDialog) -> Unit) {
    val ctx = LocalContext.current
    val p = vm.panes[index]
    val items = vm.selectedItems(index)
    var more by remember { mutableStateOf(false) }

    @Composable
    fun Action(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(icon, label, tint = if (enabled) BlueText else TextMuted)
        }
    }

    Surface(color = Color.White, shadowElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth().height(58.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (inTrash) {
                Action(Icons.Filled.RestoreFromTrash, "Obnoviť") { vm.restoreSelection(index) }
                Action(Icons.Filled.DeleteForever, "Zmazať natrvalo") {
                    onDialog(PaneDialog.Delete(index, p.selected.size, forever = true))
                }
            } else {
                Action(Icons.Filled.ContentCut, "Vystrihnúť") { vm.toClipboard(index, move = true) }
                Action(Icons.Filled.ContentCopy, "Kopírovať") { vm.toClipboard(index, move = false) }
                Action(Icons.Filled.Delete, "Zmazať") {
                    onDialog(PaneDialog.Delete(index, p.selected.size, forever = false))
                }
                Action(Icons.Filled.Share, "Zdieľať") { shareFiles(ctx, items.map { it.file }) }
                Action(Icons.Filled.Edit, "Premenovať", enabled = items.size == 1) {
                    items.firstOrNull()?.let { onDialog(PaneDialog.Rename(it.file)) }
                }
            }
            Box {
                Action(Icons.Filled.MoreVert, "Viac") { more = true }
                DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                    DropdownMenuItem(text = { Text("Označiť všetko") }, onClick = { vm.selectAll(index); more = false })
                    if (items.size == 1) {
                        DropdownMenuItem(
                            text = { Text("Vlastnosti") },
                            onClick = { onDialog(PaneDialog.Details(items[0])); more = false }
                        )
                    }
                    val single = items.singleOrNull()
                    if (single != null && single.isDir && !inTrash) {
                        DropdownMenuItem(
                            text = { Text("Otvoriť v druhom paneli") },
                            onClick = { vm.open(vm.other(index), single.file); vm.clearSelection(index); more = false }
                        )
                        DropdownMenuItem(
                            text = { Text(if (vm.isBookmarked(single.file)) "Odobrať zo záložiek" else "Pridať do záložiek") },
                            onClick = { vm.toggleBookmark(single.file); vm.clearSelection(index); more = false }
                        )
                    }
                }
            }
        }
    }
}

/* ---------- Plávajúce tlačidlo ---------- */

@Composable
private fun PaneFab(
    vm: FilesViewModel, index: Int, selectionMode: Boolean, inTrash: Boolean,
    onDialog: (PaneDialog) -> Unit, modifier: Modifier
) {
    val p = vm.panes[index]
    val color by animateColorAsState(if (p.flashDone) DoneGreen else Amber, label = "fab")
    var menu by remember { mutableStateOf(false) }

    val icon: ImageVector
    val onClick: () -> Unit
    when {
        p.flashDone -> { icon = Icons.Filled.Check; onClick = {} }
        selectionMode && !inTrash -> { icon = Icons.AutoMirrored.Filled.ArrowForward; onClick = { vm.transferToOpposite(index) } }
        p.dir != null && !selectionMode -> { icon = Icons.Filled.Add; onClick = { menu = true } }
        else -> return
    }

    Box(modifier.padding(18.dp)) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = color,
            contentColor = Color.White,
            shape = CircleShape,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
        ) { Icon(icon, null, modifier = Modifier.size(28.dp)) }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Nový priečinok") }, onClick = { onDialog(PaneDialog.NewFolder(index)); menu = false })
            DropdownMenuItem(text = { Text("Nový súbor") }, onClick = { onDialog(PaneDialog.NewFile(index)); menu = false })
        }
    }
}
