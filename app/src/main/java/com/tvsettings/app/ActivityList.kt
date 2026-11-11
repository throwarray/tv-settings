package com.tvsettings.app

import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// starred: not exported (or needs a permission), so only Shizuku's shell can start it
data class ActivityEntry(val className: String, val label: String, val starred: Boolean)

private val SWATCHES = listOf(0xFF37474F.toInt(), 0xFF1565C0.toInt(), 0xFF2E7D32.toInt(), 0xFF6A1B9A.toInt())

// Stored per activity under "pkg/class": label:<key>, icon:<key> (app:<pkg> or emoji:<text>:<argb hex>), shortcut:<key>.
object ActivityPrefs {
    private const val PREFS = "activity_list"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun label(context: Context, key: String): String? = prefs(context).getString("label:$key", null)

    fun icon(context: Context, key: String): String? = prefs(context).getString("icon:$key", null)

    fun setLabel(context: Context, key: String, value: String?) {
        val editor = prefs(context).edit()
        if (value == null) editor.remove("label:$key") else editor.putString("label:$key", value)
        editor.apply()
    }

    fun setIcon(context: Context, key: String, value: String?) {
        val editor = prefs(context).edit()
        if (value == null) editor.remove("icon:$key") else editor.putString("icon:$key", value)
        editor.apply()
    }

    fun showUnexported(context: Context): Boolean = prefs(context).getBoolean("show_unexported", false)

    fun setShowUnexported(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("show_unexported", on).apply()
    }

    fun allowShortcut(context: Context, key: String) {
        prefs(context).edit().putBoolean("shortcut:$key", true).apply()
    }

    fun shortcutAllowed(context: Context, key: String): Boolean = prefs(context).getBoolean("shortcut:$key", false)
}

object ActivityList {
    // Every enabled activity the package declares, A to Z.
    fun load(context: Context, pkg: String): List<ActivityEntry> = try {
        val info = context.packageManager.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
        (info.activities ?: emptyArray())
            .filter { it.enabled }
            .map { ActivityEntry(it.name, it.name.substringAfterLast('.'), starred = !it.exported || it.permission != null) }
            .sortedBy { it.label.lowercase() }
    } catch (_: Exception) {
        emptyList()
    }

    fun launch(context: Context, pkg: String, cls: String, starred: Boolean) {
        if (starred) {
            if (!ShizukuManager.isReady) {
                context.toast("Needs Shizuku running")
                return
            }
            Thread { Shell.startActivity(pkg, cls) }.start()
            return
        }
        try {
            context.startActivity(Intent().setComponent(ComponentName(pkg, cls)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            context.toast("Can't open this activity")
        }
    }

    // apps the user can open, A to Z: (package, label)
    fun appChoices(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        return iconPackages(context)
            .map { pkg -> pkg to try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg } }
            .sortedBy { it.second.lowercase() }
    }

    // packages with a launcher icon, for the icon picker
    fun iconPackages(context: Context): List<String> =
        context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .sorted()
            .take(60)

    private val iconCache = android.util.LruCache<String, Bitmap>(64)

    fun resolveIcon(context: Context, key: String, pkg: String, cls: String, override: String? = null): Bitmap {
        val pm = context.packageManager
        val spec = override ?: ActivityPrefs.icon(context, key)
        val cacheKey = "$key|$spec"
        iconCache.get(cacheKey)?.let { return it }
        val bitmap = try {
            when {
                spec != null && spec.startsWith("app:") ->
                    drawableBitmap(pm.getApplicationIcon(spec.removePrefix("app:")))
                spec != null && spec.startsWith("file:") ->
                    android.graphics.BitmapFactory.decodeFile(spec.removePrefix("file:"))
                        ?.let { Bitmap.createScaledBitmap(it, 96, 96, true) }
                        ?: emojiBitmap("?", 0xFF37474F.toInt())
                spec != null && spec.startsWith("emoji:") -> {
                    val parts = spec.removePrefix("emoji:").split(':')
                    emojiBitmap(parts[0], parts.getOrNull(1)?.toLongOrNull(16)?.toInt() ?: 0xFF37474F.toInt())
                }
                else -> drawableBitmap(pm.getActivityIcon(ComponentName(pkg, cls)))
            }
        } catch (_: Exception) {
            emojiBitmap("?", 0xFF37474F.toInt())
        }
        iconCache.put(cacheKey, bitmap)
        return bitmap
    }

    // Starred activities go through LaunchTrampolineActivity, which only starts shortcut-allowed activities.
    fun createShortcut(context: Context, pkg: String, cls: String, label: String, iconSpec: String?, starred: Boolean) {
        val sm = if (Build.VERSION.SDK_INT >= 26) context.getSystemService(ShortcutManager::class.java) else null
        if (sm == null || !sm.isRequestPinShortcutSupported) {
            context.toast("This launcher doesn't accept shortcuts")
            return
        }
        val key = "$pkg/$cls"
        if (starred && !ShizukuManager.isReady) {
            context.toast("Shortcuts to * activities need Shizuku running")
            return
        }
        val intent = if (starred) {
            ActivityPrefs.allowShortcut(context, key)
            Intent(context, LaunchTrampolineActivity::class.java)
                .setAction(LaunchTrampolineActivity.ACTION)
                .putExtra("pkg", pkg)
                .putExtra("cls", cls)
        } else {
            Intent(Intent.ACTION_MAIN).setComponent(ComponentName(pkg, cls))
        }
        val info = ShortcutInfo.Builder(context, key)
            .setShortLabel(label)
            .setIcon(Icon.createWithBitmap(resolveIcon(context, key, pkg, cls, iconSpec)))
            .setIntent(intent)
            .build()
        val ok = sm.requestPinShortcut(info, null)
        context.toast(if (ok) "Shortcut requested" else "The launcher refused the shortcut")
    }

    private fun drawableBitmap(d: Drawable, px: Int = 96): Bitmap {
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, px, px)
        d.draw(Canvas(bitmap))
        return bitmap
    }

    private fun emojiBitmap(text: String, argb: Int, px: Int = 96): Bitmap {
        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = argb }
        canvas.drawRoundRect(RectF(0f, 0f, px.toFloat(), px.toFloat()), px * 0.2f, px * 0.2f, bg)
        val fg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = px * 0.6f
            textAlign = Paint.Align.CENTER
        }
        val fm = fg.fontMetrics
        canvas.drawText(text, px / 2f, px / 2f - (fm.ascent + fm.descent) / 2f, fg)
        return bitmap
    }
}

@Composable
fun ActivityRow(
    pkg: String,
    entry: ActivityEntry,
    open: Boolean,
    onToggle: () -> Unit,
    pendingIcon: String?,
    onPendingIcon: (String?) -> Unit,
    pendingLabel: String?,
    onPendingLabel: (String?) -> Unit,
) {
    val context = LocalContext.current
    val key = "$pkg/${entry.className}"
    val shownLabel = pendingLabel ?: ActivityPrefs.label(context, key) ?: entry.label
    var sub by remember(key) { mutableStateOf<String?>(null) }
    var emojiText by remember(key) { mutableStateOf("") }
    var swatch by remember(key) { mutableStateOf(SWATCHES[0]) }
    val bitmap = remember(key, pendingIcon) {
        ActivityList.resolveIcon(context, key, pkg, entry.className, pendingIcon).asImageBitmap()
    }
    val launcherPackages = remember(sub) { if (sub == "icon") ActivityList.iconPackages(context) else emptyList() }
    val rowScope = rememberCoroutineScope()
    val iconRow = rememberLazyListState()
    val dragScope = rememberCoroutineScope()
    val firstIcon = remember { FocusRequester() }
    val launchFocus = remember { FocusRequester() }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(8.dp)

    fun pickEmoji(text: String, argb: Int) {
        onPendingIcon("emoji:${text.trim()}:${Integer.toHexString(argb)}")
        sub = null
    }

    // Opening a row lands on Launch. The icon picker opens at its first icon.
    LaunchedEffect(open) {
        if (open) runCatching { launchFocus.requestFocus() }
    }
    LaunchedEffect(sub) {
        if (sub == "icon") {
            iconRow.scrollToItem(0)
            runCatching { firstIcon.requestFocus() }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (focused) Oled.rowFocused else Color.Transparent, shape)
                .then(if (focused) Modifier.border(2.dp, Oled.focusBorder, shape) else Modifier)
                .focusable(interactionSource = interaction)
                .keyDownClick { onToggle() }
                .clickable(interactionSource = interaction, indication = null) { onToggle() }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(26.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (entry.starred) "$shownLabel *" else shownLabel,
                    color = Oled.textPrimary, fontSize = 12.sp, lineHeight = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    entry.className, color = Oled.textTertiary, fontSize = 9.sp, lineHeight = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (open) "Hide options" else "Show options",
                tint = Oled.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
        if (open) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 34.dp, top = 2.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(modifier = Modifier.focusRequester(launchFocus)) {
                    ActionRow(
                        "Launch", Icons.Default.PlayArrow,
                        subtitle = if (entry.starred) "* needs Shizuku running" else null,
                    ) { ActivityList.launch(context, pkg, entry.className, entry.starred) }
                }
                ActionRow(
                    "Create shortcut", Icons.Default.Add,
                    subtitle = if (entry.starred) "* needs Shizuku running" else null,
                ) {
                    ActivityList.createShortcut(context, pkg, entry.className, shownLabel, pendingIcon, entry.starred)
                }
                ActionRow("Rename", Icons.Default.Edit) { sub = if (sub == "rename") null else "rename" }
                ActionRow("Change icon to an app icon", Icons.Default.Star) { sub = if (sub == "icon") null else "icon" }
                ActionRow("Change icon to an image", AppIcons.Card) { sub = if (sub == "browse") null else "browse" }
                ActionRow("Emoji icon", Icons.Default.Star) { sub = if (sub == "emoji") null else "emoji" }
                ActionRow("Reset", AppIcons.Undo, subtitle = "Clears the name and icon") {
                    onPendingIcon(null)
                    onPendingLabel(null)
                    ActivityPrefs.setLabel(context, key, null)
                    ActivityPrefs.setIcon(context, key, null)
                    sub = null
                }
                when (sub) {
                    "rename" -> InlineField(
                        value = shownLabel, enabled = true, placeholder = entry.label, modifier = Modifier.fillMaxWidth(),
                    ) { text ->
                        val clean = text.trim()
                        onPendingLabel(if (clean.isEmpty() || clean == entry.label) null else clean)
                        sub = null
                    }
                    "icon" -> LazyRow(
                        state = iconRow,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.rowDrag(iconRow, dragScope),
                    ) {
                        itemsIndexed(launcherPackages) { index, p ->
                            val pick = {
                                onPendingIcon("app:$p")
                                sub = null
                            }
                            Box(
                                modifier = Modifier
                                    .then(if (index == 0) Modifier.focusRequester(firstIcon) else Modifier)
                                    .size(40.dp)
                                    .background(Oled.row, RoundedCornerShape(8.dp))
                                    .keyDownClick { pick() }
                                    .clickable { pick() },
                                contentAlignment = Alignment.Center
                            ) {
                                AppIcon(p, 30.dp)
                                if (pendingIcon == "app:$p") {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .width(24.dp)
                                            .height(3.dp)
                                            .background(Oled.textPrimary)
                                    )
                                }
                            }
                        }
                    }
                    "browse" -> FileBrowser(
                        title = "Choose an image",
                        start = "/sdcard",
                        dirMode = false,
                        exts = listOf(".png", ".jpg", ".jpeg", ".webp"),
                        onPick = { path ->
                            rowScope.launch { onIo { Shell.permission(context.packageName, "android.permission.READ_EXTERNAL_STORAGE", true) } }
                            onPendingIcon("file:$path")
                            sub = null
                        },
                        onDismiss = { sub = null },
                    )
                    "emoji" -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        InlineField(
                            value = emojiText, enabled = true, placeholder = "OK, then type an emoji",
                            modifier = Modifier.weight(1f),
                        ) { text ->
                            emojiText = text.trim()
                            if (emojiText.isNotEmpty()) pickEmoji(emojiText, swatch)
                        }
                        SWATCHES.forEach { argb ->
                            val choose = {
                                swatch = argb
                                if (emojiText.isNotEmpty()) pickEmoji(emojiText, argb)
                            }
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(Color(argb), CircleShape)
                                    .keyDownClick { choose() }
                                    .clickable { choose() }
                            )
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
}

