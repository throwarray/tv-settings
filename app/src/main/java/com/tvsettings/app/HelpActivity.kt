package com.tvsettings.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class HelpActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TVSettingsTheme {
                HelpScreen { finish() }
            }
        }
    }
}

@Composable
fun HelpScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val licences = remember { Licences.load(context) }
    val open = remember { mutableStateMapOf<String, Boolean>() }
    Column(
        modifier = Modifier.fillMaxSize().background(Oled.background).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.widthIn(max = Dimens.advancedMaxWidth).fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    stringResource(R.string.about_title), color = Oled.textPrimary, fontSize = 18.sp,
                    lineHeight = 20.sp, fontWeight = FontWeight.Bold
                )
            }
            CompactIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Back") { onBack() }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Card(
            modifier = Modifier.widthIn(max = Dimens.advancedMaxWidth).fillMaxSize(),
            colors = CardDefaults.cardColors(containerColor = Oled.card),
            shape = RoundedCornerShape(14.dp)
        ) {
            // one flat list: the page is the only thing that scrolls, so the D-pad moves through every row in order
            LazyColumn(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                licences.forEach { licence ->
                    val isOpen = open[licence.title] == true
                    indentedItem {
                        LicenceHeader(licence, isOpen) { open[licence.title] = !isOpen }
                    }
                    if (isOpen) {
                        licence.url?.let { url ->
                            indentedItem {
                                Box(modifier = Modifier.padding(start = 12.dp)) { ActionRow(stringResource(R.string.lic_repo), AppIcons.ExternalLink) { openUrl(context, url) } }
                            }
                        }
                        indentedItems(licence.paragraphs.indices.toList(), key = { "${licence.title}:$it" }) { i ->
                            Box(modifier = Modifier.padding(start = 12.dp)) { FocusableParagraph(licence.paragraphs[i]) }
                        }
                    }
                    indentedItem { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }
        }
    }
}

// The header of one licence entry: its name, its copyright, and a chevron
@Composable
fun LicenceHeader(licence: Licences.Licence, isOpen: Boolean, onToggle: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (focused) Oled.rowFocused else Oled.row, shape)
            .focusable(interactionSource = interaction)
            .keyDownClick { onToggle() }
            .clickable(interactionSource = interaction, indication = null) { onToggle() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(licence.title, color = Oled.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            licence.copyright?.let { Text(it, color = Oled.textSecondary, fontSize = 10.sp, lineHeight = 12.sp) }
        }
        Icon(
            if (isOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (isOpen) "Collapse" else "Expand", tint = Oled.textSecondary, modifier = Modifier.size(18.dp),
        )
    }
}

// One paragraph of a licence text: focusable, so the D-pad moves through the text
@Composable
fun FocusableParagraph(text: String) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Text(
        text,
        color = Oled.textSecondary, fontSize = 11.sp, lineHeight = 14.sp,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (focused) Oled.rowFocused else Color.Transparent)
            .focusable(interactionSource = interaction)
            .padding(vertical = 2.dp),
    )
}
