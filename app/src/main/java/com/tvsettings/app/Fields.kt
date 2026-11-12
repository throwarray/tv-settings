package com.tvsettings.app

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.focusable
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Text edited in place. It is read-only until OK, so moving with the D-pad never brings up the keyboard.
 * OK starts editing and shows the keyboard; Done, or leaving the field, commits.
 */
@Composable
fun InlineField(
    value: String,
    enabled: Boolean,
    placeholder: String,
    modifier: Modifier = Modifier,
    commitOnChange: Boolean = false,
    focusRequester: FocusRequester? = null,
    focusDown: FocusRequester? = null,
    onCommit: (String) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var text by remember(value) { mutableStateOf(value) }
    val ownRequester = remember { FocusRequester() }
    val requester = focusRequester ?: ownRequester
    val keyboard = LocalSoftwareKeyboardController.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(8.dp)

    fun finish() {
        if (!editing) return
        editing = false
        keyboard?.hide()
        if (text.trim() != value) onCommit(text.trim())
    }

    LaunchedEffect(editing) {
        if (editing) {
            requester.requestFocus()
            keyboard?.show()
        }
    }

    BasicTextField(
        value = text,
        onValueChange = {
            text = it
            if (commitOnChange) onCommit(it)
        },
        enabled = enabled,
        readOnly = !editing,
        singleLine = true,
        textStyle = TextStyle(color = Oled.textPrimary, fontSize = 11.sp),
        cursorBrush = SolidColor(Oled.textPrimary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { finish() }),
        interactionSource = interaction,
        modifier = modifier
            .height(32.dp)
            .focusRequester(requester)
            .then(if (focusDown != null) Modifier.focusProperties { down = focusDown } else Modifier)
            .onFocusChanged { if (!it.isFocused) finish() }
            .keyDownClick(enabled && !editing) { editing = true }
            .background(Oled.row, shape)
            .then(if (focused) Modifier.border(2.dp, Oled.focusBorder, shape) else Modifier),
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize().padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
                if (text.isEmpty()) {
                    Text(placeholder, color = Oled.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                inner()
            }
        }
    )
}

/** Label, optional subtitle, then an inline field on the right. */
@Composable
fun TextFieldRow(
    label: String,
    value: String,
    enabled: Boolean = true,
    subtitle: String? = null,
    focusRequester: FocusRequester? = null,
    focusDown: FocusRequester? = null,
    onCommit: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
        InlineField(
            value = value, enabled = enabled, placeholder = "-", modifier = Modifier.width(132.dp),
            focusRequester = focusRequester, focusDown = focusDown, onCommit = onCommit
        )
    }
}

/** Shell command with a title, edited in place, and a run button beside it. */
@Composable
fun CommandRow(command: String, enabled: Boolean, onChange: (String) -> Unit, onRun: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .alpha(if (enabled) 1f else 0.5f)
    ) {
        Text(stringResource(R.string.shell_command), color = Oled.textPrimary, fontSize = 12.sp, lineHeight = 14.sp)
        Spacer(modifier = Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            InlineField(
                value = command, enabled = enabled, placeholder = stringResource(R.string.shell_command_example),
                modifier = Modifier.weight(1f), onCommit = onChange
            )
            CompactIconButton(Icons.Default.PlayArrow, stringResource(R.string.run)) { if (enabled) onRun() }
        }
    }
}

/** Command output: scrolls, highlights when focused, and OK or the icon copies it. */
@Composable
fun OutputBox(output: String) {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(8.dp)
    fun copy() {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("output", output))
        context.toast(context.getString(R.string.copied))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .background(if (focused) Oled.rowFocused else Oled.row, shape)
            .then(if (focused) Modifier.border(2.dp, Oled.focusBorder, shape) else Modifier)
            .padding(8.dp)
            .keyDownClick { copy() }
            .clickable(interactionSource = interaction, indication = null) { copy() },
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            output, color = Oled.textPrimary, fontSize = 11.sp, lineHeight = 14.sp,
            modifier = Modifier.weight(1f).heightIn(max = 160.dp).verticalScroll(rememberScrollState())
        )
        CopyButton { copy() }
    }
}

/** Text button for copying, same size as the icon buttons. */
@Composable
fun CopyButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .height(32.dp)
            .background(if (focused) Oled.rowFocused else Oled.row, shape)
            .then(if (focused) Modifier.border(2.dp, Oled.focusBorder, shape) else Modifier)
            .keyDownClick { onClick() }
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(stringResource(R.string.copy), color = Oled.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Search edited in place; the list filters as you type. */
@Composable
fun CompactSearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    InlineField(
        value = value, enabled = true, placeholder = stringResource(R.string.search),
        modifier = modifier, commitOnChange = true, onCommit = onValueChange
    )
}

/** Title, value as subtitle, and a Copy label; OK or a click copies the value. */
@Composable
fun CopyRow(label: String, value: String) {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(8.dp)
    fun copy() {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, value))
        context.toast(context.getString(R.string.copied))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (focused) Oled.rowFocused else Color.Transparent, shape)
            .then(if (focused) Modifier.border(2.dp, Oled.focusBorder, shape) else Modifier)
            .focusable(interactionSource = interaction)
            .keyDownClick { copy() }
            .clickable(interactionSource = interaction, indication = null) { copy() }
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = Oled.textPrimary, fontSize = 12.sp, lineHeight = 14.sp)
            Text(value, color = Oled.textSecondary, fontSize = 10.sp, lineHeight = 12.sp)
        }
        Box(modifier = Modifier.background(Oled.row, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(stringResource(R.string.copy), color = Oled.textPrimary, fontSize = 10.sp)
        }
    }
}
