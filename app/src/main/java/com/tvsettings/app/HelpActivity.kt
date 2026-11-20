package com.tvsettings.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Oled.background)
            .padding(14.dp),
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
            Column(
                modifier = Modifier.fillMaxSize().padding(14.dp).verticalScroll(rememberScrollState())
            ) {
                    ActionRow(stringResource(R.string.about_repository), AppIcons.ExternalLink) {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/throwarray/tv-settings"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                Spacer(modifier = Modifier.height(8.dp))
                licences.forEach { licence ->
                    LicenceBlock(licence)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

// Scrollable text: each paragraph is focusable, so the D-pad scrolls it
@Composable
fun ScrollableText(text: String, maxHeight: Dp) {
    val paragraphs = remember(text) { text.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() } }
    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
        items(paragraphs) { paragraph ->
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            Text(
                paragraph,
                color = Oled.textSecondary, fontSize = 11.sp, lineHeight = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (focused) Oled.rowFocused else Color.Transparent)
                    .focusable(interactionSource = interaction)
                    .padding(vertical = 2.dp),
            )
        }
    }
}

// One licence: its title expands the text, which scrolls
@Composable
fun LicenceBlock(licence: Licences.Licence) {
    var expanded by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(8.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Oled.row, shape)
            .padding(10.dp)
    ) {
        Text(
            licence.title,
            color = Oled.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .background(if (focused) Oled.rowFocused else Color.Transparent, shape)
                .focusable(interactionSource = interaction)
                .clickable(interactionSource = interaction, indication = null) { expanded = !expanded }
                .padding(vertical = 4.dp),
        )
        if (expanded) {
            Spacer(modifier = Modifier.height(4.dp))
            ScrollableText(licence.text, 260.dp)
        }
    }
}
