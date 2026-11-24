package sk.fold.files

import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PaneState(start: File) {
    var dir by mutableStateOf(start)
    var entries by mutableStateOf<List<File>>(emptyList())
    var selected by mutableStateOf<Set<String>>(emptySet())
}

class FileManagerViewModel : ViewModel() {

    val root: File = Environment.getExternalStorageDirectory()

    val panes = listOf(
        PaneState(root),
        PaneState(
            File(root, Environment.DIRECTORY_DOWNLOADS).takeIf { it.isDirectory } ?: root
        )
    )

    /** Ktorý panel je aktívny (0 = ľavý/horný, 1 = pravý/dolný). */
    var active by mutableIntStateOf(0)

    /** true = pustenie súboru ho presunie, false = skopíruje. */
    var moveOnDrop by mutableStateOf(true)
    var showHidden by mutableStateOf(false)
    var busy by mutableStateOf(false)
        private set

    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    fun other(i: Int) = 1 - i

    fun refresh(i: Int) {
        val pane = panes[i]
        viewModelScope.launch {
            val dir = pane.dir
            val list = withContext(Dispatchers.IO) { FileOps.list(dir, showHidden) }
            if (pane.dir == dir) {
                pane.entries = list
                val paths = list.map { it.path }.toSet()
                pane.selected = pane.selected.filter { it in paths }.toSet()
            }
        }
    }

    fun refreshAll() = panes.indices.forEach(::refresh)

    fun open(i: Int, dir: File) {
        if (!dir.isDirectory || !dir.canRead()) {
            messages.tryEmit("Priečinok ${dir.name} nie je dostupný"); return
        }
        panes[i].dir = dir
        panes[i].selected = emptySet()
        panes[i].entries = emptyList()
        refresh(i)
    }

    fun canGoUp(i: Int): Boolean =
        panes[i].dir.canonicalPath != root.canonicalPath && panes[i].dir.parentFile != null

    fun up(i: Int): Boolean {
        if (!canGoUp(i)) return false
        open(i, panes[i].dir.parentFile!!)
        return true
    }

    fun toggleSelect(i: Int, f: File) {
        val p = panes[i]
        p.selected = if (f.path in p.selected) p.selected - f.path else p.selected + f.path
    }

    fun selectAll(i: Int) {
        panes[i].selected = panes[i].entries.map { it.path }.toSet()
    }

    fun clearSelection(i: Int) {
        panes[i].selected = emptySet()
    }

    /** Čo sa ťahá: ak je položka označená, celý výber, inak len ona. */
    fun dragPaths(i: Int, f: File): List<String> {
        val sel = panes[i].selected
        return if (f.path in sel) sel.toList() else listOf(f.path)
    }

    fun drop(paths: List<String>, destDir: File) {
        val sources = paths.map(::File).filter { it.parentFile?.path != destDir.path }
        if (sources.isEmpty()) return
        val move = moveOnDrop
        runOp(if (move) "Presunuté" else "Skopírované") {
            FileOps.transfer(sources, destDir, move)
        }
    }

    fun transferSelection(from: Int, move: Boolean) {
        val sources = panes[from].selected.map(::File)
        val dest = panes[other(from)].dir
        if (sources.isEmpty()) return
        runOp(if (move) "Presunuté" else "Skopírované") { FileOps.transfer(sources, dest, move) }
    }

    fun deleteSelection(i: Int) {
        val files = panes[i].selected.map(::File)
        runOp("Zmazané") { FileOps.delete(files) }
    }

    fun rename(file: File, newName: String) = runOp("Premenované") { FileOps.rename(file, newName) }

    fun mkdir(i: Int, name: String) = runOp("Vytvorené") { FileOps.mkdir(panes[i].dir, name) }

    private fun runOp(label: String, block: () -> FileOps.Result) {
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
                    append("Chyby: ").append(r.errors.take(3).joinToString("; "))
                    if (r.errors.size > 3) append(" …")
                }
            }
            if (msg.isNotEmpty()) messages.emit(msg)
        }
    }
}
