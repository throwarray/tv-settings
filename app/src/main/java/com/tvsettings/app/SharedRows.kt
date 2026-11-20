package com.tvsettings.app

import androidx.annotation.StringRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// ---------- Shared rows and buttons ----------

@Composable
fun EmptyNote(text: String) {
    Text(text, color = Oled.textSecondary, fontSize = 10.sp, modifier = Modifier.padding(8.dp))
}

// A soft pulse for a button that is working
@Composable
fun busyPulse(): Float {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "busy")
    return transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(600),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "busy",
    ).value
}

@Composable
fun ActionRow(label: String, icon: ImageVector, enabled: Boolean = true, subtitle: String? = null, busy: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pulse = busyPulse()
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (focused) Oled.rowFocused else Color.Transparent, shape)
            .alpha(if (busy) pulse else 1f)
            .then(if (focused) Modifier.border(2.dp, Oled.focusBorder, shape) else Modifier)
            .keyDownClick(enabled) { onClick() }
            .clickable(enabled = enabled, interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(label, color = Oled.textPrimary, fontSize = 12.sp, lineHeight = 14.sp)
            if (subtitle != null) {
                Text(subtitle, color = Oled.textSecondary, fontSize = 10.sp, lineHeight = 12.sp)
            }
        }
        Box(
            modifier = Modifier.size(width = 52.dp, height = 32.dp).background(Oled.row, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Oled.textPrimary, modifier = Modifier.size(12.dp))
        }
    }
}

class QuickAction(val label: String, val icon: ImageVector, val enabled: Boolean = true, val onClick: () -> Unit)

/** Actions side by side, each with its icon over its label. */
@Composable
fun QuickActionRow(actions: List<QuickAction>) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        actions.forEach { action ->
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            val shape = RoundedCornerShape(8.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(if (focused) Oled.rowFocused else Color.Transparent, shape)
                    .then(if (focused) Modifier.border(2.dp, Oled.focusBorder, shape) else Modifier)
                    .keyDownClick(action.enabled) { action.onClick() }
                    .clickable(enabled = action.enabled, interactionSource = interaction, indication = null) { action.onClick() }
                    .padding(vertical = 4.dp)
                    .alpha(if (action.enabled) 1f else 0.5f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.size(width = 52.dp, height = 32.dp).background(Oled.row, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(imageVector = action.icon, contentDescription = null, tint = Oled.textPrimary, modifier = Modifier.size(12.dp))
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(action.label, color = Oled.textPrimary, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1)
            }
        }
    }
}

data class ToggleSpec(
    val id: String,
    @StringRes val titleRes: Int,
    val checked: Boolean,
    @StringRes val subtitleRes: Int? = null,
    val subtitle: String? = null,
    val enabled: Boolean = true,
    val onToggle: (Boolean) -> Unit,
)

@Composable
fun ToggleRow(setting: ToggleSpec) {
    ToggleRow(
        label = stringResource(setting.titleRes),
        checked = setting.checked,
        subtitle = setting.subtitleRes?.let { stringResource(it) } ?: setting.subtitle,
        enabled = setting.enabled,
        onCheckedChange = setting.onToggle,
    )
}

@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    subtitle: String? = null,
    enabled: Boolean = true,
    focusableWhenDisabled: Boolean = false,
    focusDown: FocusRequester? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (focused) Oled.rowFocused else Color.Transparent, shape)
            .then(if (focused) Modifier.border(2.dp, Oled.focusBorder, shape) else Modifier)
            .then(if (!enabled && focusableWhenDisabled) Modifier.focusable(interactionSource = interaction) else Modifier)
            .keyDownClick(enabled) { onCheckedChange(!checked) }
            .then(if (focusDown != null) Modifier.focusProperties { down = focusDown } else Modifier)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = interaction,
                indication = null,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f, fill = false).widthIn(max = Dimens.textMaxWidth).padding(end = 8.dp)) {
            Text(label, color = Oled.textPrimary, fontSize = 12.sp, lineHeight = 14.sp)
            if (subtitle != null) {
                Text(subtitle, color = Oled.textSecondary, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        CompactSwitch(checked)
    }
}

@Composable
fun CompactSwitch(checked: Boolean) {
    Box(
        modifier = Modifier
            .size(width = 32.dp, height = 18.dp)
            .background(if (checked) Oled.textPrimary else Oled.switchTrackOff, CircleShape)
            .then(if (checked) Modifier else Modifier.border(1.dp, Oled.switchTrackOff, CircleShape))
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(Modifier.size(12.dp).background(if (checked) Oled.background else Oled.textSecondary, CircleShape))
    }
}

@Composable
fun MenuOption(text: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(6.dp)
    DropdownMenuItem(
        text = { Text(text, color = Oled.textPrimary, fontSize = 12.sp) },
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = 4.dp, vertical = 1.dp)
            .background(if (focused) Oled.rowFocused else if (selected) Oled.selected else Color.Transparent, shape)
            .then(if (focused) Modifier.border(2.dp, Oled.focusBorder, shape) else Modifier)
            .keyDownClick { onClick() },
        interactionSource = interaction,
    )
}

@Composable
fun DropdownRow(
    label: String,
    options: List<Pair<String, String>>,
    selectedId: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    focusDown: FocusRequester? = null,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(8.dp)
    val valueText = options.firstOrNull { it.first == selectedId }?.second ?: selectedId

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (focused) Oled.rowFocused else Color.Transparent, shape)
                .then(if (focused) Modifier.border(2.dp, Oled.focusBorder, shape) else Modifier)
                .then(if (focusDown != null) Modifier.focusProperties { down = focusDown } else Modifier)
                .keyDownClick(enabled) { expanded = true }
                .clickable(enabled = enabled, interactionSource = interaction, indication = null) { expanded = true }
                .padding(horizontal = 10.dp, vertical = 3.dp)
                .alpha(if (enabled) 1f else 0.5f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f, fill = false).widthIn(max = Dimens.textMaxWidth).padding(end = 8.dp)) {
                Text(label, color = Oled.textPrimary, fontSize = 12.sp, lineHeight = 14.sp)
                if (subtitle != null) {
                    Text(subtitle, color = Oled.textSecondary, fontSize = 10.sp, lineHeight = 12.sp)
                }
            }
            Box(
                modifier = Modifier
                    .width(132.dp)
                    .background(Oled.row, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        valueText, color = Oled.textPrimary, fontSize = 11.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                    )
                    Text("▾", color = Oled.textPrimary, fontSize = 11.sp)
                }
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.border(1.dp, Oled.rowFocused, RoundedCornerShape(4.dp))
        ) {
            options.forEach { (id, text) ->
                MenuOption(text, selected = id == selectedId) {
                    expanded = false
                    onSelect(id)
                }
            }
        }
    }
}

@Composable
fun CompactDropdown(
    options: List<Pair<String, String>>,
    selectedId: String,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(8.dp)
    val valueText = options.firstOrNull { it.first == selectedId }?.second ?: selectedId

    Box(
        modifier = modifier
            .height(32.dp)
            .background(if (focused) Oled.rowFocused else Oled.row, shape)
            .then(if (focused) Modifier.border(2.dp, Oled.focusBorder, shape) else Modifier)
            .keyDownClick { expanded = true }
            .clickable(interactionSource = interaction, indication = null) { expanded = true }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text("$valueText ▾", color = Oled.textPrimary, fontSize = 11.sp, maxLines = 1)
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.border(1.dp, Oled.rowFocused, RoundedCornerShape(4.dp))
        ) {
            options.forEach { (id, text) ->
                MenuOption(text, selected = id == selectedId) {
                    expanded = false
                    onSelect(id)
                }
            }
        }
    }
}

/** Fixed size so neighbouring buttons always match. */
@Composable
fun CompactIconButton(
    icon: ImageVector,
    description: String,
    container: Color = Oled.row,
    tint: Color = Oled.textPrimary,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .size(32.dp)
            .background(if (focused) Oled.rowFocused else container, shape)
            .then(if (focused) Modifier.border(2.dp, Oled.focusBorder, shape) else Modifier)
            .keyDownClick { onClick() }
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = description, tint = tint, modifier = Modifier.size(16.dp))
    }
}

/** Authorise if Shizuku runs but isn't authorised, else open the Shizuku app. */
@Composable
fun ShizukuStatusButton(state: ShizukuManager.State, onClick: () -> Unit) {
    val icon: ImageVector
    val container: Color
    val tint: Color
    val description: String
    when (state) {
        ShizukuManager.State.READY -> {
            icon = Icons.Default.CheckCircle
            container = Oled.row
            tint = Oled.okGreen
            description = "Shizuku connected. Tap to open Shizuku"
        }
        ShizukuManager.State.CONNECTING -> {
            icon = Icons.Default.Info
            container = Oled.row
            tint = Oled.textTertiary
            description = "Shizuku connecting"
        }
        ShizukuManager.State.NEEDS_PERMISSION -> {
            icon = Icons.Default.Lock
            container = Oled.row
            tint = Color(0xFFFFC27A)
            description = "Shizuku needs permission. Tap to authorize"
        }
        ShizukuManager.State.NOT_RUNNING -> {
            icon = Icons.Default.Warning
            container = Oled.row
            tint = Color(0xFFFFC27A)
            description = "Shizuku not running. Tap to open Shizuku"
        }
    }
    CompactIconButton(icon, description, container, tint, onClick = onClick)
}

@Composable
fun CompactTextButton(label: String, description: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(if (focused) Oled.rowFocused else Oled.row, shape)
            .then(if (focused) Modifier.border(2.dp, Oled.focusBorder, shape) else Modifier)
            .keyDownClick { onClick() }
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Oled.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

// MemAvailable from /proc/meminfo, in kB (0 if it can't be read)
fun memAvailableKb(): Long =
    Regex("""MemAvailable:\s+(\d+)""").find(Shell.meminfo().output)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

@Composable
fun UserTag(color: Color) {
    Box(
        modifier = Modifier
            .width(40.dp)
            .background(color.copy(alpha = 0.22f), RoundedCornerShape(4.dp))
            .padding(horizontal = 2.dp, vertical = 0.dp)
    ) {
        Text(stringResource(R.string.user), color = color, fontWeight = FontWeight.Medium, fontSize = 8.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun SystemTag(color: Color) {
    Box(
        modifier = Modifier
            .width(40.dp)
            .background(color.copy(alpha = 0.22f), RoundedCornerShape(4.dp))
            .padding(horizontal = 2.dp, vertical = 0.dp)
    ) {
        Text(stringResource(R.string.system), color = color, fontWeight = FontWeight.Medium, fontSize = 8.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

// Scroll thumb over the app list; hidden when everything fits.
fun DrawScope.drawListThumb(state: LazyListState) {
    val info = state.layoutInfo
    val total = info.totalItemsCount
    val visible = info.visibleItemsInfo.size
    if (total <= visible || visible == 0) return
    val thumbH = maxOf(24.dp.toPx(), size.height * visible / total)
    val fraction = state.firstVisibleItemIndex.toFloat() / (total - visible)
    drawRoundRect(
        color = Oled.thumb,
        topLeft = Offset(size.width - 4.dp.toPx(), (size.height - thumbH) * fraction),
        size = Size(3.dp.toPx(), thumbH),
        cornerRadius = CornerRadius(2.dp.toPx())
    )
}

// One-line label and value for the info section
@Composable
fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Oled.textSecondary, fontSize = 11.sp, modifier = Modifier.width(96.dp))
        Text(value, color = Oled.textPrimary, fontSize = 11.sp, lineHeight = 13.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Oled.textPrimary) },
        text = { Text(text, color = Oled.textSecondary) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, color = Oled.textPrimary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = Oled.textPrimary) } },
        containerColor = Oled.card,
    )
}

@Composable
fun TextDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Oled.textPrimary) },
        text = { ScrollableText(text, 360.dp) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close), color = Oled.textPrimary) } },
        containerColor = Oled.card,
    )
}

// A permission backed by an AppOp: toggles the op through Shizuku, or opens its Settings page without Shizuku.
@Composable
fun AppOpPermRow(app: AppRecord, perm: PermissionEntry, scope: CoroutineScope, shizukuReady: Boolean) {
    val context = LocalContext.current
    val op = AppInspector.opForPermission(perm.name)
    val mode = if (shizukuReady) {
        app.appOps.firstOrNull { it.op == op }?.mode ?: "default"
    } else {
        AppInspector.appOpMode(context, app.packageName, perm.name)
            ?: app.appOps.firstOrNull { it.op == op }?.mode
            ?: "default"
    }
    val granted = when (mode) {
        "allow" -> true
        "ignore", "deny" -> false
        else -> perm.granted
    }
    ToggleRow(perm.label, granted, subtitle = perm.description, enabled = true) { want ->
        if (shizukuReady) {
            scope.launch {
                val ok = onIo { Shell.appOpsSet(app.packageName, op, if (want) "allow" else "ignore").ok }
                if (!ok) context.toast("Couldn't change ${perm.label}")
                val fresh = onIo { AppInspector.loadAppOps(context.packageManager, app.packageName) }
                app.appOps.clear()
                app.appOps.addAll(fresh)
            }
        } else {
            AppSettingsLinks.open(context, app.packageName, perm.name)
        }
    }
}

// A permission the user can't change: name and description full width, state at the right edge.
@Composable
fun FixedPermRow(perm: PermissionEntry) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (focused) Oled.rowFocused else Color.Transparent, RoundedCornerShape(8.dp))
            .focusable(interactionSource = interaction)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(perm.label, color = Oled.textPrimary, fontSize = 12.sp, lineHeight = 14.sp)
            perm.description?.let { Text(it, color = Oled.textSecondary, fontSize = 10.sp, lineHeight = 12.sp) }
        }
        Text(if (perm.granted) "granted" else "not granted", color = Oled.textSecondary, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
fun DebloatNote(info: Debloat.Info) {
    val label = stringResource(
        when (info.tone) {
            Debloat.Tone.RECOMMENDED -> R.string.tone_recommended
            Debloat.Tone.KEEP -> R.string.tone_keep
            Debloat.Tone.PROTECTED -> R.string.tone_protected
            Debloat.Tone.ADVANCED -> R.string.tone_advanced
            Debloat.Tone.UNSAFE -> R.string.tone_unsafe
        }
    )
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(label, color = Debloat.toneColor(info.tone), fontSize = 12.sp, lineHeight = 14.sp)
        info.note?.let { Text(it, color = Oled.textSecondary, fontSize = 10.sp, lineHeight = 12.sp) }
    }
}

// The bolt: focus highlight like the other buttons, white like the other icons
@Composable
fun BoostButton(enabled: Boolean, busy: Boolean = false, onClick: () -> Unit) {
    val pulse = busyPulse()
    CompactIconButton(
        AppIcons.Bolt,
        stringResource(R.string.boost_action),
        modifier = Modifier.alpha(if (busy) pulse else if (enabled) 1f else 0.5f),
    ) { if (enabled) onClick() }
}
