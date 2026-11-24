package sk.fold.files.ui

import android.text.format.Formatter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sk.fold.files.Collection
import sk.fold.files.FilesViewModel

private fun collectionIcon(c: Collection): ImageVector = when (c) {
    Collection.RECENT -> Icons.Filled.History
    Collection.PHOTOS -> Icons.Filled.Image
    Collection.MUSIC -> Icons.Filled.MusicNote
    Collection.VIDEOS -> Icons.Filled.Movie
    Collection.DOCS -> Icons.Filled.Description
    Collection.APPS -> Icons.Filled.Android
    Collection.TRASH -> Icons.Filled.Delete
}

@Composable
fun DrawerContent(vm: FilesViewModel, close: () -> Unit) {
    val ctx = LocalContext.current
    var storageOpen by rememberSaveable { mutableStateOf(true) }
    var bookmarksOpen by rememberSaveable { mutableStateOf(true) }
    var collectionsOpen by rememberSaveable { mutableStateOf(true) }

    ModalDrawerSheet(
        drawerContainerColor = Color.White,
        drawerShape = RectangleShape,
        modifier = Modifier.width(310.dp)
    ) {
        DrawerHeader()
        LazyColumn(Modifier.fillMaxSize()) {
            item { SectionTitle("Úložiská", storageOpen) { storageOpen = !storageOpen } }
            if (storageOpen) {
                items(vm.roots, key = { "r" + it.file.path }) { r ->
                    val used = if (r.total > 0) 1f - r.free.toFloat() / r.total else 0f
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { vm.open(vm.active, r.file); close() }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SmallIcon(if (r.title == "SD karta") Icons.Filled.SdCard else Icons.Filled.PhoneAndroid)
                        Column(Modifier.weight(1f).padding(start = 16.dp)) {
                            Text(r.title, fontSize = 16.sp, color = TextPrimary)
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { used },
                                color = Amber,
                                trackColor = Color(0xFFE0E0E0),
                                strokeCap = StrokeCap.Butt,
                                modifier = Modifier.fillMaxWidth().height(3.dp)
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "${Formatter.formatShortFileSize(ctx, r.free)} voľné z ${Formatter.formatShortFileSize(ctx, r.total)}" +
                                    "  ·  ${(used * 100).toInt()} %",
                                fontSize = 12.sp, color = TextSecondary
                            )
                        }
                    }
                }
            }

            item { SectionTitle("Záložky", bookmarksOpen) { bookmarksOpen = !bookmarksOpen } }
            if (bookmarksOpen) {
                items(vm.bookmarks, key = { "b" + it.path }) { b ->
                    DrawerRow(Icons.Filled.Folder, b.name.ifEmpty { "/" }, vm.rootTitle(vm.rootOf(b))) {
                        vm.open(vm.active, b); close()
                    }
                }
            }

            item { SectionTitle("Kolekcie", collectionsOpen) { collectionsOpen = !collectionsOpen } }
            if (collectionsOpen) {
                items(Collection.entries, key = { "c" + it.name }) { c ->
                    DrawerRow(collectionIcon(c), c.title, null) {
                        vm.openCollection(vm.active, c); close()
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** Hlavička s diagonálnymi plochami v štýle Solid Explorer. */
@Composable
private fun DrawerHeader() {
    Box(Modifier.fillMaxWidth().height(132.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(Color.White)
            drawPath(Path().apply {
                moveTo(0f, 0f); lineTo(w * 0.62f, 0f); lineTo(0f, h * 0.75f); close()
            }, Color(0xFF80DEEA))
            drawPath(Path().apply {
                moveTo(w * 0.30f, 0f); lineTo(w * 0.62f, 0f); lineTo(w * 0.12f, h * 0.55f); close()
            }, Color(0xFFB2EBF2))
            drawPath(Path().apply {
                moveTo(0f, h * 0.22f); lineTo(w * 0.80f, h); lineTo(0f, h); close()
            }, Color(0xFF039BE5))
            drawLine(Color(0x22000000), Offset(0f, h - 1), Offset(w, h - 1), 1f)
        }
        Text(
            "Fold Files",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 14.dp)
        )
    }
}

@Composable
private fun SectionTitle(title: String, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onToggle() }.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 14.sp, color = TextSecondary, modifier = Modifier.weight(1f))
        Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = TextSecondary)
    }
}

@Composable
private fun SmallIcon(icon: ImageVector) {
    Box(
        Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).background(Blue),
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(17.dp)) }
}

@Composable
private fun DrawerRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SmallIcon(icon)
        Column(Modifier.padding(start = 16.dp)) {
            Text(title, fontSize = 16.sp, color = TextPrimary)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = TextSecondary)
        }
    }
}
