package sk.fold.files.ui

import android.app.Activity
import android.os.SystemClock
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import sk.fold.files.FilesViewModel
import sk.fold.files.PendingTransfer
import sk.fold.files.childLabel
import sk.fold.files.formatDate
import sk.fold.files.plural

/** Šírka, od ktorej sú panely vedľa seba (rozložený Fold). */
private val WIDE = 600.dp

@Composable
fun FilesApp(vm: FilesViewModel = viewModel()) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val snackbar = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<PaneDialog?>(null) }
    var lastBack by remember { mutableLongStateOf(0L) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshAll() }
    LaunchedEffect(Unit) {
        vm.messages.collect {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(it)
        }
    }

    BackHandler {
        val i = vm.active
        val p = vm.panes[i]
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            p.selected.isNotEmpty() -> vm.clearSelection(i)
            p.searchOpen -> { p.searchOpen = false; p.filter = "" }
            vm.up(i) -> Unit
            else -> {
                val now = SystemClock.elapsedRealtime()
                if (now - lastBack < 2000) (ctx as? Activity)?.finish()
                else {
                    lastBack = now
                    Toast.makeText(ctx, "Stlačte ešte raz pre opustenie", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = { DrawerContent(vm) { scope.launch { drawerState.close() } } }
    ) {
        Box(Modifier.fillMaxSize().background(Color.White)) {
            BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                if (maxWidth >= WIDE) {
                    Row(Modifier.fillMaxSize()) {
                        PaneView(vm, 0, true, openDrawer, { dialog = it }, Modifier.weight(1f))
                        VerticalDivider(color = Color(0xFFCFCFCF))
                        PaneView(vm, 1, true, openDrawer, { dialog = it }, Modifier.weight(1f))
                    }
                } else {
                    // Vonkajší displej: jeden panel, medzi panelmi sa prepína potiahnutím
                    val pager = rememberPagerState(initialPage = vm.active) { 2 }
                    LaunchedEffect(pager.currentPage) { vm.active = pager.currentPage }
                    LaunchedEffect(vm.active) {
                        if (pager.currentPage != vm.active) pager.animateScrollToPage(vm.active)
                    }
                    HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                        PaneView(vm, page, false, openDrawer, { dialog = it })
                    }
                    if (vm.panes[vm.active].selected.isEmpty()) {
                        Row(
                            Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            repeat(2) { i ->
                                Box(
                                    Modifier.size(7.dp).clip(CircleShape)
                                        .background(if (pager.currentPage == i) BlueText else Color(0xFFBDBDBD))
                                )
                            }
                        }
                    }
                }
                if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter), color = Amber)
                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp))
            }
            // Prehliadač fotiek a videí cez celú obrazovku
            vm.viewer?.let { MediaViewer(vm, it) }
        }
    }

    vm.pending?.let { t ->
        TransferDialog(
            t,
            onCopy = { vm.confirmTransfer(move = false) },
            onMove = { vm.confirmTransfer(move = true) },
            onDismiss = { vm.pending = null }
        )
    }

    when (val d = dialog) {
        is PaneDialog.NewFolder -> NameDialog("Nový priečinok", "", { dialog = null }) {
            vm.mkdir(d.pane, it); dialog = null
        }
        is PaneDialog.NewFile -> NameDialog("Nový súbor", "", { dialog = null }) {
            vm.mkfile(d.pane, it); dialog = null
        }
        is PaneDialog.Rename -> NameDialog("Premenovať", d.file.name, { dialog = null }) {
            vm.rename(d.file, it); dialog = null
        }
        is PaneDialog.Delete -> {
            val what = "${d.count} ${plural(d.count, "položku", "položky", "položiek")}"
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(if (d.forever) "Natrvalo zmazať $what?" else "Presunúť $what do koša?") },
                text = {
                    Text(
                        if (d.forever) "Túto akciu nie je možné vrátiť."
                        else "Z koša ich môžeš obnoviť (Menu › Kolekcie › Odpadkový kôš)."
                    )
                },
                confirmButton = {
                    TextButton(onClick = { vm.deleteSelection(d.pane); dialog = null }) {
                        Text("ZMAZAŤ", color = BlueText)
                    }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("ZRUŠIŤ", color = TextSecondary) } }
            )
        }
        is PaneDialog.Details -> {
            val it = d.item
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(it.name) },
                text = {
                    Column {
                        DetailLine("Umiestnenie", it.file.parent ?: "")
                        DetailLine(
                            if (it.isDir) "Obsah" else "Veľkosť",
                            if (it.isDir) childLabel(it.childCount) else Formatter.formatFileSize(ctx, it.size)
                        )
                        DetailLine("Zmenené", formatDate(it.modified))
                    }
                },
                confirmButton = { TextButton(onClick = { dialog = null }) { Text("OK", color = BlueText) } }
            )
        }
        PaneDialog.EmptyTrash -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Vyprázdniť kôš?") },
            text = { Text("Všetky položky v koši sa natrvalo zmažú.") },
            confirmButton = {
                TextButton(onClick = { vm.emptyTrash(); dialog = null }) { Text("VYPRÁZDNIŤ", color = BlueText) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("ZRUŠIŤ", color = TextSecondary) } }
        )
        null -> Unit
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, fontSize = 12.sp, color = TextSecondary)
        Text(value, fontSize = 15.sp, color = TextPrimary)
    }
}

/** Dialóg ako v Solid Explorer: „Previesť výber do panelu oproti“. */
@Composable
private fun TransferDialog(t: PendingTransfer, onCopy: () -> Unit, onMove: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(18.dp), color = Color.White) {
            Column(Modifier.padding(top = 22.dp, start = 22.dp, end = 22.dp, bottom = 8.dp)) {
                Text(
                    if (t.opposite) "Previesť výber do panelu oproti" else "Previesť výber do priečinka",
                    fontSize = 18.sp, color = TextPrimary
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PhoneAndroid, null, tint = TextSecondary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Moje súbory : ${t.dest.path}", fontSize = 15.sp, color = TextPrimary)
                }
                Text(
                    "${t.paths.size} ${plural(t.paths.size, "položka", "položky", "položiek")}",
                    fontSize = 13.sp, color = TextSecondary, modifier = Modifier.padding(start = 32.dp, top = 2.dp)
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onCopy) {
                        Icon(Icons.Filled.ContentCopy, null, tint = BlueText)
                        Spacer(Modifier.width(8.dp))
                        Text("KOPÍROVAŤ DO...", color = TextPrimary, fontSize = 15.sp)
                    }
                    TextButton(onClick = onMove) {
                        Icon(Icons.Filled.ContentCut, null, tint = BlueText)
                        Spacer(Modifier.width(8.dp))
                        Text("PRESUNÚŤ DO...", color = TextPrimary, fontSize = 15.sp)
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("ZRUŠIŤ", color = TextPrimary, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    // Pri premenovaní označí meno bez prípony
    val end = initial.lastIndexOf('.').takeIf { it > 0 } ?: initial.length
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, end))) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.text) }, enabled = value.text.isNotBlank()) {
                Text("OK", color = BlueText)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ZRUŠIŤ", color = TextSecondary) } }
    )
}
