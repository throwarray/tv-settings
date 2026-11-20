package com.tvsettings.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- Skeletons ----------

@Composable
fun rememberSkeletonAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 800), repeatMode = RepeatMode.Reverse),
        label = "skeletonAlpha"
    )
    return alpha
}

@Composable
fun SkeletonBox(alpha: Float, modifier: Modifier = Modifier) {
    Box(modifier = modifier.alpha(alpha).background(Oled.skeleton, RoundedCornerShape(8.dp)))
}

@Composable
fun AppListSkeleton() {
    val a = rememberSkeletonAlpha()
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(12) { SkeletonBox(a, Modifier.fillMaxWidth().height(36.dp)) }
    }
}

@Composable
fun DetailSkeleton(modifier: Modifier = Modifier) {
    val a = rememberSkeletonAlpha()
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Oled.card),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            SkeletonBox(a, Modifier.width(90.dp).height(10.dp))
            repeat(3) { SkeletonBox(a, Modifier.fillMaxWidth().height(32.dp)) }
            Spacer(modifier = Modifier.height(6.dp))
            SkeletonBox(a, Modifier.width(70.dp).height(10.dp))
            repeat(6) { SkeletonBox(a, Modifier.fillMaxWidth().height(32.dp)) }
        }
    }
}

// ---------- App icons (small bitmaps, LRU-cached, only loaded for visible rows) ----------

private val iconCache = LruCache<String, ImageBitmap>(128)

private fun loadAppIcon(context: Context, packageName: String): ImageBitmap? = try {
    val drawable = context.packageManager.getApplicationIcon(packageName)
    val px = 64
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    drawable.setBounds(0, 0, px, px)
    drawable.draw(Canvas(bitmap))
    bitmap.asImageBitmap().also { iconCache.put(packageName, it) }
} catch (_: Throwable) {
    null
}

@Composable
fun AppIcon(packageName: String, size: Dp) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(initialValue = iconCache.get(packageName), packageName) {
        if (value == null) value = onIo { loadAppIcon(context, packageName) }
    }
    val shape = RoundedCornerShape(6.dp)
    val bitmap = icon
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(size).clip(shape))
    } else {
        Box(modifier = Modifier.size(size).background(Oled.skeleton, shape))
    }
}

// ---------- App list ----------

@Composable
fun AppListRow(app: AppRecord, selected: Boolean, onSelect: () -> Unit, onFocus: () -> Unit = {}) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focused) { if (focused) onFocus() }
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) Oled.selected else Color.Transparent, shape)
            .drawBehind {
                if (selected) {
                    drawRoundRect(
                        Oled.textPrimary,
                        topLeft = Offset(0f, size.height * 0.2f),
                        size = Size(3.dp.toPx(), size.height * 0.6f),
                        cornerRadius = CornerRadius(2.dp.toPx())
                    )
                }
            }
            .then(if (focused) Modifier.border(2.dp, Oled.focusBorder, shape) else Modifier)
            .keyDownClick { onSelect() }
            .clickable(interactionSource = interaction, indication = null) { onSelect() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // disabled = faded
        Row(
            modifier = Modifier.weight(1f).alpha(if (app.isEnabled) 1f else 0.4f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(app.packageName, 26.dp)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    app.appName, color = Oled.textPrimary, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    app.packageName, color = Oled.textTertiary, fontSize = 9.sp, lineHeight = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        Box(modifier = Modifier.width(72.dp), contentAlignment = Alignment.CenterEnd) {
            UsageFeed.snapshot?.perPackage?.get(app.packageName)?.let { usage ->
                Text(
                    "${formatCpu(usage.cpuPercent)}  ${formatKb(usage.rssKb)}",
                    color = Oled.textTertiary, fontSize = 9.sp, maxLines = 1,
                )
            }
        }
        Box(modifier = Modifier.width(56.dp), contentAlignment = Alignment.CenterEnd) {
            val tagColor = Debloat.toneColor(Debloat.infoFor(app.packageName)?.tone)
            if (app.isSystem) SystemTag(tagColor) else UserTag(tagColor)
        }
        Box(modifier = Modifier.width(20.dp), contentAlignment = Alignment.Center) {
            if (app.isActive && app.isEnabled) {
                Box(modifier = Modifier.size(6.dp).background(Oled.okGreen, CircleShape))
            }
        }
    }
}
