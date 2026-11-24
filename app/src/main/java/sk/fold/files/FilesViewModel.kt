package sk.fold.files

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PaneState(start: File) {
    var location by mutableStateOf<Location>(Location.Dir(start))
    /** Cesta pre breadcrumb – drží aj priečinky „vpred“, ako Solid Explorer. */
    var trail by mutableStateOf<List<File>>(emptyList())
    var items by mutableStateOf<List<FileItem>>(emptyList())
    var rows by mutableStateOf<List<Row>>(emptyList())
    var selected by mutableStateOf<Set<String>>(emptySet())
    var viewMode by mutableStateOf(ViewMode.LIST)
    var sortMode by mutableStateOf(SortMode.NAME)
    var loading by mutableStateOf(false)
    var searchOpen by mutableStateOf(false)
    var filter by mutableStateOf("")
    var flashDone by mutableStateOf(false)
    /** Posledný priečinok – kam sa vrátiť z kolekcie alebo hľadania. */
    var lastDir: File = start

    val dir: File? get() = (location as? Location.Dir)?.file
    val dirCount get() = items.count { it.isDir }
    val fileCount get() = items.count { !it.isDir }

    fun visibleRows(): List<Row> {
        val q = filter.trim().lowercase()
        if (q.isEmpty()) return rows
        return rows.filter { it is Row.Item && it.item.name.lowercase().contains(q) }
    }
}

data class ViewerState(val items: List<FileItem>, val start: Int)
data class Clip(val paths: List<String>, val move: Boolean)
data class PendingTransfer(val paths: List<String>, val dest: File, val opposite: Boolean, val fromPane: Int)

class FilesViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("foldfiles", Context.MODE_PRIVATE)
    val root: File = Repo.internalRoot

    val panes = listOf(
        PaneState(root),
        PaneState(File(root, "Download").takeIf { it.isDirectory } ?: root)
    )
    private val jobs = arrayOfNulls<Job>(2)

    var active by mutableIntStateOf(0)
    var showHidden by mutableStateOf(prefs.getBoolean("hidden", false))
        private set
    var busy by mutableStateOf(false)
        private set
    var clip by mutableStateOf<Clip?>(null)
    var pending by mutableStateOf<PendingTransfer?>(null)
    var viewer by mutableStateOf<ViewerState?>(null)
    var roots by mutableStateOf<List<StorageRoot>>(emptyList())
        private set
    var bookmarks by mutableStateOf(loadBookmarks())
        private set

    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    init {
        panes.forEach { it.trail = crumbsFor(it.dir!!) }
    }

    fun other(i: Int) = 1 - i

    /* ---------- Načítanie ---------- */

    fun load(i: Int) {
        val p = panes[i]
        val loc = p.location
        jobs[i]?.cancel()
        p.loading = true
        jobs[i] = viewModelScope.launch {
            val ctx = getApplication<Application>()
            val items = withContext(Dispatchers.IO) {
                when (loc) {
                    is Location.Dir -> Repo.listDir(loc.file, showHidden)
                    is Location.Coll -> Repo.collection(ctx, loc.collection)
                    is Location.Search -> Repo.search(loc.dir, loc.query, showHidden)
                }
            }
            val (view, sort) = when (loc) {
                is Location.Dir -> {
                    val auto = Repo.autoView(loc.file, items)
                    val v = prefs.getString("view:${loc.file.path}", null)?.let { ViewMode.valueOf(it) } ?: auto.first
                    val s = prefs.getString("sort:${loc.file.path}", null)?.let { SortMode.valueOf(it) } ?: auto.second
                    v to s
                }
                is Location.Coll -> when (loc.collection) {
                    Collection.PHOTOS, Collection.VIDEOS -> ViewMode.GRID to SortMode.DATE
                    else -> ViewMode.LIST to SortMode.DATE
                }
                is Location.Search -> ViewMode.LIST to SortMode.NAME
            }
            if (p.location != loc) return@launch
            p.items = items
            p.viewMode = view
            p.sortMode = sort
            p.rows = buildRows(items, sort)
            val paths = items.map { it.path }.toSet()
            p.selected = p.selected.filter { it in paths }.toSet()
            p.loading = false
        }
    }

    fun refreshAll() {
        panes.indices.forEach(::load)
        viewModelScope.launch {
            roots = withContext(Dispatchers.IO) { Repo.roots(getApplication()) }
        }
    }

    /* ---------- Navigácia ---------- */

    fun rootOf(f: File): File {
        val all = roots.map { it.file } + root
        return all.filter { f.path == it.path || f.path.startsWith(it.path + "/") }
            .maxByOrNull { it.path.length } ?: root
    }

    fun rootTitle(r: File) = roots.firstOrNull { it.file.path == r.path }?.title ?: "Interná pamäť"

    private fun crumbsFor(dir: File): List<File> {
        val r = rootOf(dir)
        val list = mutableListOf<File>()
        var cur: File? = dir
        while (cur != null) {
            list += cur
            if (cur.path == r.path) break
            cur = cur.parentFile
        }
        return list.reversed()
    }

    fun open(i: Int, dir: File) {
        if (!dir.isDirectory || !dir.canRead()) {
            messages.tryEmit("Priečinok ${dir.name} nie je dostupný"); return
        }
        val p = panes[i]
        val keep = p.trail.any { it.path == dir.path }
        if (!keep) p.trail = crumbsFor(dir)
        p.location = Location.Dir(dir)
        p.lastDir = dir
        resetView(p)
        active = i
        load(i)
    }

    fun openCollection(i: Int, c: Collection) {
        val p = panes[i]
        p.location = Location.Coll(c)
        resetView(p)
        active = i
        load(i)
    }

    fun search(i: Int, query: String) {
        val p = panes[i]
        if (query.isBlank()) return
        p.location = Location.Search(p.lastDir, query.trim())
        p.selected = emptySet()
        p.filter = ""
        load(i)
    }

    private fun resetView(p: PaneState) {
        p.selected = emptySet()
        p.items = emptyList()
        p.rows = emptyList()
        p.filter = ""
        p.searchOpen = false
    }

    fun canGoUp(i: Int): Boolean {
        val p = panes[i]
        return when (val l = p.location) {
            is Location.Dir -> l.file.path != rootOf(l.file).path
            else -> true
        }
    }

    fun up(i: Int): Boolean {
        val p = panes[i]
        return when (val l = p.location) {
            is Location.Dir -> {
                if (!canGoUp(i)) false else { open(i, l.file.parentFile!!); true }
            }
            else -> { open(i, p.lastDir); true }
        }
    }

    /* ---------- Zobrazenie ---------- */

    fun setView(i: Int, v: ViewMode) {
        val p = panes[i]
        p.viewMode = v
        p.dir?.let { prefs.edit().putString("view:${it.path}", v.name).apply() }
    }

    fun setSort(i: Int, s: SortMode) {
        val p = panes[i]
        p.sortMode = s
        p.rows = buildRows(p.items, s)
        p.dir?.let { prefs.edit().putString("sort:${it.path}", s.name).apply() }
    }

    fun toggleHidden() {
        showHidden = !showHidden
        prefs.edit().putBoolean("hidden", showHidden).apply()
        panes.indices.forEach(::load)
    }

    /* ---------- Záložky ---------- */

    private fun loadBookmarks(): List<File> {
        val raw = prefs.getString("bookmarks", null)
            ?: return listOfNotNull(File(root, "Download").takeIf { it.isDirectory })
        return raw.split('\n').filter { it.isNotBlank() }.map(::File)
    }

    fun isBookmarked(f: File) = bookmarks.any { it.path == f.path }

    fun toggleBookmark(f: File) {
        bookmarks = if (isBookmarked(f)) bookmarks.filter { it.path != f.path } else bookmarks + f
        prefs.edit().putString("bookmarks", bookmarks.joinToString("\n") { it.path }).apply()
    }

    /* ---------- Výber ---------- */

    fun toggleSelect(i: Int, path: String) {
        val p = panes[i]
        p.selected = if (path in p.selected) p.selected - path else p.selected + path
        active = i
    }

    fun select(i: Int, path: String) {
        panes[i].selected = panes[i].selected + path
        active = i
    }

    fun selectAll(i: Int) {
        panes[i].selected = panes[i].visibleRows().filterIsInstance<Row.Item>().map { it.item.path }.toSet()
    }

    fun invertSelection(i: Int) {
        val all = panes[i].visibleRows().filterIsInstance<Row.Item>().map { it.item.path }.toSet()
        panes[i].selected = all - panes[i].selected
    }

    fun clearSelection(i: Int) {
        panes[i].selected = emptySet()
    }

    fun selectedItems(i: Int): List<FileItem> {
        val sel = panes[i].selected
        return panes[i].items.filter { it.path in sel }
    }

    /* ---------- Presun / kópia ---------- */

    /** Šípka na tlačidle: preniesť výber do panelu oproti. */
    fun transferToOpposite(i: Int) {
        val dest = panes[other(i)].dir
        if (dest == null) {
            messages.tryEmit("Druhý panel nie je priečinok"); return
        }
        val paths = panes[i].selected.toList()
        if (paths.isNotEmpty()) pending = PendingTransfer(paths, dest, true, i)
    }

    /** Pustenie ťahaných položiek: do panelu alebo na priečinok. */
    fun requestDrop(paths: List<String>, dest: File, fromPane: Int, toPane: Int) {
        val valid = paths.filter { p -> p != dest.path && File(p).parentFile?.path != dest.path }
        if (valid.isEmpty()) return
        pending = PendingTransfer(valid, dest, fromPane != toPane && panes[toPane].dir?.path == dest.path, fromPane)
    }

    fun confirmTransfer(move: Boolean) {
        val t = pending ?: return
        pending = null
        runOp(if (move) "Presunuté" else "Skopírované", flashPane = t.fromPane) {
            FileOps.transfer(t.paths.map(::File), t.dest, move)
        }
    }

    fun toClipboard(i: Int, move: Boolean) {
        val paths = panes[i].selected.toList()
        if (paths.isEmpty()) return
        clip = Clip(paths, move)
        clearSelection(i)
        messages.tryEmit("${paths.size} ${plural(paths.size, "položka", "položky", "položiek")} v schránke – otvor cieľ a daj Vložiť")
    }

    fun paste(i: Int) {
        val c = clip ?: return
        val dest = panes[i].dir ?: return
        clip = null
        runOp(if (c.move) "Presunuté" else "Skopírované", flashPane = i) {
            FileOps.transfer(c.paths.map(::File), dest, c.move)
        }
    }

    /* ---------- Prehliadač ---------- */

    fun openViewer(i: Int, item: FileItem) {
        val media = panes[i].visibleRows().filterIsInstance<Row.Item>().map { it.item }.filter { it.isMedia }
        val start = media.indexOfFirst { it.path == item.path }.coerceAtLeast(0)
        if (media.isNotEmpty()) viewer = ViewerState(media, start)
    }

    /** „Interná pamäť/DCIM/Camera“ */
    fun displayPath(dir: File?): String {
        if (dir == null) return ""
        val r = rootOf(dir)
        val rel = dir.path.removePrefix(r.path).trim('/')
        return if (rel.isEmpty()) rootTitle(r) else rootTitle(r) + "/" + rel
    }

    fun trashFiles(files: List<File>) = runOp("Presunuté do koša") { Trash.moveToTrash(files) }

    /* ---------- Ďalšie operácie ---------- */

    fun deleteSelection(i: Int) {
        val files = panes[i].selected.map(::File)
        val inTrash = (panes[i].location as? Location.Coll)?.collection == Collection.TRASH
        runOp(if (inTrash) "Natrvalo zmazané" else "Presunuté do koša") {
            if (inTrash) Trash.deleteForever(files) else Trash.moveToTrash(files)
        }
    }

    fun restoreSelection(i: Int) {
        val files = panes[i].selected.map(::File)
        runOp("Obnovené") { Trash.restore(files) }
    }

    fun emptyTrash() = runOp("Kôš vyprázdnený") { Trash.empty() }

    fun rename(file: File, newName: String) = runOp("Premenované") { FileOps.rename(file, newName) }

    fun mkdir(i: Int, name: String) {
        val dir = panes[i].dir ?: return
        runOp("Priečinok vytvorený") { FileOps.mkdir(dir, name) }
    }

    fun mkfile(i: Int, name: String) {
        val dir = panes[i].dir ?: return
        runOp("Súbor vytvorený") { FileOps.mkfile(dir, name) }
    }

    private fun runOp(label: String, flashPane: Int? = null, block: () -> FileOps.Result) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { block() }
            busy = false
            panes.forEach { it.selected = emptySet() }
            refreshAll()
            val msg = buildString {
                if (r.done > 0) append("$label: ${r.done}")
                if (r.errors.isNotEmpty()) {
                    if (isNotEmpty()) append(" · ")
                    append("Chyby: ").append(r.errors.take(2).joinToString("; "))
                    if (r.errors.size > 2) append(" …")
                }
            }
            if (msg.isNotEmpty()) messages.emit(msg)
            if (flashPane != null && r.done > 0) {
                panes[flashPane].flashDone = true
                delay(1500)
                panes[flashPane].flashDone = false
            }
        }
    }
}
