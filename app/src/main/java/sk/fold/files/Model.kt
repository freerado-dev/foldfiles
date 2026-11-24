package sk.fold.files

import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class Kind { FOLDER, IMAGE, VIDEO, AUDIO, APK, PDF, ARCHIVE, DOC, OTHER }

data class FileItem(
    val file: File,
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
    val childCount: Int,
    val kind: Kind
) {
    val path: String get() = file.path
    val isMedia: Boolean get() = kind == Kind.IMAGE || kind == Kind.VIDEO
}

enum class Collection(val title: String) {
    RECENT("Posledné súbory"),
    PHOTOS("Fotografie"),
    MUSIC("Hudba"),
    VIDEOS("Videá"),
    DOCS("Dokumenty"),
    APPS("Aplikácie"),
    TRASH("Odpadkový kôš")
}

sealed interface Location {
    data class Dir(val file: File) : Location
    data class Coll(val collection: Collection) : Location
    data class Search(val dir: File, val query: String) : Location
}

enum class SortMode { NAME, DATE, SIZE }
enum class ViewMode { LIST, GRID }

sealed interface Row {
    data class Header(val title: String, val key: String) : Row
    data class Item(val item: FileItem) : Row
}

fun kindOf(name: String, isDir: Boolean): Kind {
    if (isDir) return Kind.FOLDER
    return when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "dng" -> Kind.IMAGE
        "mp4", "mkv", "mov", "avi", "webm", "3gp", "m4v" -> Kind.VIDEO
        "mp3", "m4a", "flac", "wav", "ogg", "opus", "aac", "amr" -> Kind.AUDIO
        "apk", "apks", "xapk" -> Kind.APK
        "pdf" -> Kind.PDF
        "zip", "rar", "7z", "tar", "gz", "xz" -> Kind.ARCHIVE
        "txt", "md", "doc", "docx", "odt", "rtf", "csv", "xls", "xlsx", "ppt", "pptx", "json", "xml", "log" -> Kind.DOC
        else -> Kind.OTHER
    }
}

/* ---------- Slovenské tvary ---------- */

fun plural(n: Int, one: String, few: String, many: String) = when {
    n == 1 -> one
    n in 2..4 -> few
    else -> many
}

fun childLabel(n: Int) = if (n == 0) "Prázdny" else "$n ${plural(n, "položka", "položky", "položiek")}"

fun countsLabel(dirs: Int, files: Int): String {
    val d = "$dirs ${plural(dirs, "zložka", "zložky", "zložiek")}"
    val f = "$files ${plural(files, "súbor", "súbory", "súborov")}"
    return when {
        dirs == 0 && files == 0 -> "Prázdny"
        files == 0 -> d
        dirs == 0 -> f
        else -> "$d, $f"
    }
}

fun selectedLabel(n: Int) = "$n " + plural(n, "položka vybraná", "položky vybrané", "položiek vybraných")

private val dateFmt = SimpleDateFormat("d. M. yyyy, H:mm:ss", Locale.forLanguageTag("sk"))
fun formatDate(ts: Long): String = dateFmt.format(Date(ts))

private val months = listOf(
    "Január", "Február", "Marec", "Apríl", "Máj", "Jún",
    "Júl", "August", "September", "Október", "November", "December"
)

fun dateGroup(ts: Long, now: Long = System.currentTimeMillis()): String {
    if (now - ts in 0 until 3_600_000L) return "Posledná hodina"
    val today = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    if (ts >= today.timeInMillis) return "Dnes"
    if (ts >= today.timeInMillis - 86_400_000L) return "Včera"
    val c = Calendar.getInstance().apply { timeInMillis = ts }
    val sameYear = c.get(Calendar.YEAR) == today.get(Calendar.YEAR)
    if (sameYear && c.get(Calendar.MONTH) == today.get(Calendar.MONTH)) return "Tento mesiac"
    val m = months[c.get(Calendar.MONTH)]
    return if (sameYear) m else "$m ${c.get(Calendar.YEAR)}"
}

/** Zoradí položky a pri triedení podľa dátumu ich rozdelí do skupín ako Solid Explorer. */
fun buildRows(items: List<FileItem>, sort: SortMode): List<Row> {
    val dirs = items.filter { it.isDir }
    val files = items.filter { !it.isDir }
    val byName = compareBy<FileItem> { it.name.lowercase() }
    return when (sort) {
        SortMode.NAME -> (dirs.sortedWith(byName) + files.sortedWith(byName)).map { Row.Item(it) }
        SortMode.SIZE -> (dirs.sortedWith(byName) + files.sortedByDescending { it.size }).map { Row.Item(it) }
        SortMode.DATE -> {
            val out = mutableListOf<Row>()
            var n = 0
            for (part in listOf(dirs, files)) {
                var last: String? = null
                for (it in part.sortedByDescending { f -> f.modified }) {
                    val g = dateGroup(it.modified)
                    if (g != last) {
                        out += Row.Header(g, "h${n++}")
                        last = g
                    }
                    out += Row.Item(it)
                }
            }
            out
        }
    }
}
