package sk.fold.files

import java.io.File

/** Operácie so súbormi – volať mimo hlavného vlákna. */
object FileOps {

    data class Result(val done: Int, val errors: List<String>)

    fun list(dir: File, showHidden: Boolean): List<File> =
        (dir.listFiles() ?: emptyArray())
            .filter { showHidden || !it.name.startsWith(".") }
            .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))

    /** Vráti voľné meno v cieľovom priečinku: "foto.jpg" -> "foto (1).jpg". */
    fun uniqueTarget(dir: File, name: String, isDir: Boolean): File {
        val first = File(dir, name)
        if (!first.exists()) return first
        val dot = name.lastIndexOf('.')
        val hasExt = !isDir && dot > 0
        val base = if (hasExt) name.substring(0, dot) else name
        val ext = if (hasExt) name.substring(dot) else ""
        var i = 1
        while (true) {
            val f = File(dir, "$base ($i)$ext")
            if (!f.exists()) return f
            i++
        }
    }

    fun isInside(child: File, parent: File): Boolean {
        val c = child.canonicalPath
        val p = parent.canonicalPath
        return c == p || c.startsWith(p + File.separator)
    }

    /** Presun alebo kópia zoznamu súborov/priečinkov do cieľa. */
    fun transfer(sources: List<File>, destDir: File, move: Boolean): Result {
        var done = 0
        val errors = mutableListOf<String>()
        for (src in sources) {
            try {
                if (!src.exists()) {
                    errors += "${src.name}: neexistuje"; continue
                }
                if (src.isDirectory && isInside(destDir, src)) {
                    errors += "${src.name}: priečinok nemožno vložiť do seba"; continue
                }
                if (move && src.parentFile?.canonicalPath == destDir.canonicalPath) continue

                val target = uniqueTarget(destDir, src.name, src.isDirectory)
                if (move) {
                    // Rýchly presun v rámci toho istého úložiska, inak kópia + zmazanie
                    if (!src.renameTo(target)) {
                        src.copyRecursively(target, overwrite = false)
                        if (!src.deleteRecursively()) {
                            errors += "${src.name}: skopírované, originál sa nepodarilo zmazať"
                        }
                    }
                } else {
                    src.copyRecursively(target, overwrite = false)
                }
                done++
            } catch (e: Exception) {
                errors += "${src.name}: ${e.message ?: e.javaClass.simpleName}"
            }
        }
        return Result(done, errors)
    }

    fun delete(files: List<File>): Result {
        var done = 0
        val errors = mutableListOf<String>()
        for (f in files) {
            if (f.deleteRecursively()) done++ else errors += "${f.name}: nepodarilo sa zmazať"
        }
        return Result(done, errors)
    }

    fun rename(file: File, newName: String): Result {
        val clean = newName.trim()
        if (clean.isEmpty() || clean.contains('/')) return Result(0, listOf("Neplatné meno"))
        val target = File(file.parentFile, clean)
        if (target.exists()) return Result(0, listOf("$clean už existuje"))
        return if (file.renameTo(target)) Result(1, emptyList())
        else Result(0, listOf("Premenovanie zlyhalo"))
    }

    fun mkdir(parent: File, name: String): Result {
        val clean = name.trim()
        if (clean.isEmpty() || clean.contains('/')) return Result(0, listOf("Neplatné meno"))
        val dir = File(parent, clean)
        if (dir.exists()) return Result(0, listOf("$clean už existuje"))
        return if (dir.mkdirs()) Result(1, emptyList()) else Result(0, listOf("Priečinok sa nepodarilo vytvoriť"))
    }
}
