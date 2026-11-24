package sk.fold.files.ui

import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.core.content.FileProvider
import java.io.File

/* Farby podľa Solid Explorer */
val Blue = Color(0xFF0288D1)
val BlueText = Color(0xFF039BE5)
val Amber = Color(0xFFFFB100)
val DoneGreen = Color(0xFF7CB342)
val PaneBg = Color(0xFFFAFAFA)
val SectionBg = Color(0xFFEDEDED)
val InactiveHeader = Color(0xFFEDEDED)
val TextPrimary = Color(0xFF212121)
val TextSecondary = Color(0xFF757575)
val TextMuted = Color(0xFFAAAAAA)
val SelectedBg = Color(0xFFD6ECF8)
val DividerColor = Color(0xFFE6E6E6)

@Composable
fun FoldFilesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Blue,
            onPrimary = Color.White,
            secondary = Amber,
            background = PaneBg,
            surface = Color.White,
            onSurface = TextPrimary,
            surfaceVariant = SectionBg,
            onSurfaceVariant = TextSecondary
        ),
        content = content
    )
}

private fun uriFor(ctx: Context, f: File) =
    FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)

private fun mimeOf(f: File) =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"

fun openFile(ctx: Context, f: File) {
    try {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uriFor(ctx, f), mimeOf(f))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(ctx, "Žiadna aplikácia nevie otvoriť ${f.name}", Toast.LENGTH_SHORT).show()
    }
}

fun shareFiles(ctx: Context, files: List<File>) {
    val list = files.filter { it.isFile }
    if (list.isEmpty()) {
        Toast.makeText(ctx, "Priečinky sa nedajú zdieľať", Toast.LENGTH_SHORT).show(); return
    }
    try {
        val uris = ArrayList(list.map { uriFor(ctx, it) })
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).setType(mimeOf(list[0])).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(intent, "Zdieľať").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(ctx, "Zdieľanie zlyhalo", Toast.LENGTH_SHORT).show()
    }
}
