package com.tvsettings.app

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Folder or APK picker over the shell's listing. The stick's own file picker doesn't work.
@Composable
fun FileBrowser(
    title: String,
    start: String,
    dirMode: Boolean,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    exts: List<String> = listOf(".apk"),
) {
    var lastNav by remember { mutableStateOf(0L) }
    // a press that lands while the previous folder is still loading is dropped
    fun once(action: () -> Unit) {
        val now = SystemClock.uptimeMillis()
        if (now - lastNav < 600) return
        lastNav = now
        action()
    }
    var path by remember { mutableStateOf(start) }
    var dirs by remember { mutableStateOf<List<String>>(emptyList()) }
    var files by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(path) {
        val listing = onIo { Shell.exec("ls -1Ap '$path' 2>/dev/null").output.lines().filter { it.isNotBlank() } }
        dirs = listing.filter { it.endsWith("/") }.map { it.trimEnd('/') }.sorted()
        files = listing.filter { !it.endsWith("/") && exts.any { ext -> it.endsWith(ext, ignoreCase = true) } }.sorted()
    }
    val parent = path.substringBeforeLast('/', "").ifEmpty { "/" }
    fun child(name: String) = if (path == "/") "/$name" else "$path/$name"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Oled.textPrimary) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(path, color = Oled.textSecondary, fontSize = 10.sp)
                if (path != "/") DirRow("..") { once { path = parent } }
                dirs.forEach { d -> DirRow("$d/") { once { path = child(d) } } }
                if (!dirMode) files.forEach { f -> DirRow(f) { once { onPick(child(f)) } } }
            }
        },
        confirmButton = {
            if (dirMode) TextButton(onClick = { onPick(path) }) { Text(stringResource(R.string.use_this_folder), color = Oled.textPrimary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = Oled.textPrimary) } },
        containerColor = Oled.card,
    )
}

@Composable
private fun DirRow(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Text(
        label, color = Oled.textPrimary, fontSize = 12.sp, lineHeight = 14.sp,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (focused) Oled.rowFocused else Color.Transparent)
            .focusable(interactionSource = interaction)
            .clickOrHold(onClick = onClick, onHold = {})
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}
