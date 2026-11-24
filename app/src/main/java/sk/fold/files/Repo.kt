package sk.fold.files

import android.annotation.SuppressLint
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.io.File

data class StorageRoot(val file: File, val title: String, val free: Long, val total: Long)

/** Čítanie súborov – všetko volať mimo hlavného vlákna. */
object Repo {

    val internalRoot: File = Environment.getExternalStorageDirectory()
    const val TRASH_DIR = ".FoldFilesTrash"

    fun toItem(f: File, showHidden: Boolean, displayName: String = f.name): FileItem {
        val isDir = f.isDirectory
        val children = if (isDir) {
            f.list()?.count { showHidden || !it.startsWith(".") } ?: 0
        } else 0
        return FileItem(
            file = f, name = displayName, isDir = isDir,
            size = if (isDir) 0 else f.length(),
            modified = f.lastModified(), childCount = children,
            kind = kindOf(displayName, isDir)
        )
    }

    fun listDir(dir: File, showHidden: Boolean): List<FileItem> =
        (dir.listFiles() ?: emptyArray())
            .filter { showHidden || !it.name.startsWith(".") }
            .filter { it.name != TRASH_DIR }
            .map { toItem(it, showHidden) }

    fun search(dir: File, query: String, showHidden: Boolean): List<FileItem> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return dir.walkTopDown()
            .onEnter { showHidden || !it.name.startsWith(".") || it == dir }
            .filter { it != dir && it.name.lowercase().contains(q) }
            .filter { showHidden || !it.name.startsWith(".") }
            .take(500)
            .map { toItem(it, showHidden) }
            .toList()
    }

    fun roots(ctx: Context): List<StorageRoot> {
        val list = mutableListOf(stat(internalRoot, "Interná pamäť"))
        ctx.getExternalFilesDirs(null).drop(1).filterNotNull().forEach { d ->
            val root = File(d.path.substringBefore("/Android/data"))
            if (root.canRead()) list += stat(root, "SD karta")
        }
        return list
    }

    private fun stat(f: File, title: String): StorageRoot = try {
        val s = StatFs(f.path)
        StorageRoot(f, title, s.availableBytes, s.totalBytes)
    } catch (e: Exception) {
        StorageRoot(f, title, 0, 0)
    }

    /* ---------- Kolekcie cez MediaStore ---------- */

    private val docMimes = arrayOf(
        "application/pdf", "text/plain", "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/vnd.oasis.opendocument.text", "application/rtf", "text/csv"
    )

    @SuppressLint("InlinedApi")
    @Suppress("DEPRECATION")
    fun collection(ctx: Context, c: Collection): List<FileItem> {
        if (c == Collection.TRASH) return Trash.list()
        val t = MediaStore.Files.FileColumns.MEDIA_TYPE
        val mime = MediaStore.Files.FileColumns.MIME_TYPE
        val data = MediaStore.MediaColumns.DATA
        val (sel, args) = when (c) {
            Collection.PHOTOS -> "$t=?" to arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString())
            Collection.VIDEOS -> "$t=?" to arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
            Collection.MUSIC -> "$t=?" to arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO.toString())
            Collection.DOCS -> "$mime IN (${docMimes.joinToString(",") { "?" }})" to docMimes
            Collection.APPS -> "$data LIKE ?" to arrayOf("%.apk")
            else -> "$mime IS NOT NULL" to emptyArray()
        }
        val limit = if (c == Collection.RECENT) 300 else 5000
        val out = mutableListOf<FileItem>()
        ctx.contentResolver.query(
            MediaStore.Files.getContentUri("external"), arrayOf(data), sel, args,
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        )?.use { cur ->
            val col = cur.getColumnIndexOrThrow(data)
            while (cur.moveToNext() && out.size < limit) {
                val path = cur.getString(col) ?: continue
                if (path.contains("/.")) continue
                val f = File(path)
                if (f.isFile) out += toItem(f, false)
            }
        }
        return out
    }

    /* ---------- Automatické zobrazenie ako v Solid Explorer ---------- */

    private val dateDirs = setOf("download", "camera", "screenshots", "screen recordings", "whatsapp images")

    fun autoView(dir: File, items: List<FileItem>): Pair<ViewMode, SortMode> {
        val files = items.filter { !it.isDir }
        val media = files.count { it.isMedia }
        val grid = files.size >= 4 && media * 10 >= files.size * 7
        val sort = if (grid || dir.name.lowercase() in dateDirs) SortMode.DATE else SortMode.NAME
        return (if (grid) ViewMode.GRID else ViewMode.LIST) to sort
    }

    /* ---------- Ikony APK ---------- */

    private val apkCache = LruCache<String, ImageBitmap>(120)

    fun apkIcon(ctx: Context, f: File): ImageBitmap? {
        val key = f.path + f.lastModified()
        apkCache.get(key)?.let { return it }
        return try {
            val pm = ctx.packageManager
            val info = pm.getPackageArchiveInfo(f.path, 0) ?: return null
            val ai = info.applicationInfo ?: return null
            ai.sourceDir = f.path
            ai.publicSourceDir = f.path
            val bmp = ai.loadIcon(pm).toBitmap(144, 144).asImageBitmap()
            apkCache.put(key, bmp)
            bmp
        } catch (e: Exception) {
            null
        }
    }
}

/** Odpadkový kôš: zmazané súbory sa presunú do skrytého priečinka a dajú sa obnoviť. */
object Trash {
    private val dir get() = File(Repo.internalRoot, Repo.TRASH_DIR).apply { mkdirs() }
    private val meta get() = File(dir, ".meta").apply { mkdirs() }

    private fun displayName(f: File) = f.name.substringAfter('-', f.name)

    fun list(): List<FileItem> =
        (dir.listFiles() ?: emptyArray())
            .filter { it.name != ".meta" }
            .map { Repo.toItem(it, true, displayName(it)) }

    fun isTrashed(f: File) = f.parentFile?.name == Repo.TRASH_DIR

    fun moveToTrash(files: List<File>): FileOps.Result {
        var done = 0
        val errors = mutableListOf<String>()
        for (f in files) {
            try {
                val target = File(dir, "${System.nanoTime()}-${f.name}")
                if (!f.renameTo(target)) {
                    f.copyRecursively(target)
                    f.deleteRecursively()
                }
                File(meta, target.name).writeText(f.path)
                done++
            } catch (e: Exception) {
                errors += "${f.name}: ${e.message}"
            }
        }
        return FileOps.Result(done, errors)
    }

    fun restore(files: List<File>): FileOps.Result {
        var done = 0
        val errors = mutableListOf<String>()
        for (f in files) {
            val m = File(meta, f.name)
            val original = if (m.exists()) File(m.readText()) else File(Repo.internalRoot, displayName(f))
            val parent = original.parentFile ?: Repo.internalRoot
            parent.mkdirs()
            val target = FileOps.uniqueTarget(parent, original.name, f.isDirectory)
            if (f.renameTo(target)) {
                m.delete(); done++
            } else errors += "${displayName(f)}: obnovenie zlyhalo"
        }
        return FileOps.Result(done, errors)
    }

    fun deleteForever(files: List<File>): FileOps.Result {
        files.forEach { File(meta, it.name).delete() }
        return FileOps.delete(files)
    }

    fun empty(): FileOps.Result = deleteForever((dir.listFiles() ?: emptyArray()).filter { it.name != ".meta" })
}
