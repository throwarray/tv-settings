package com.tvsettings.app

import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.window.Dialog
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.util.LruCache
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var themeSeen = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TVSettingsTheme {
                TVSettingsScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // the theme was changed in Advanced: rebuild so the palette follows it
        val mode = AppTheme.mode(this)
        if (themeSeen.isEmpty()) themeSeen = mode else if (themeSeen != mode) {
            themeSeen = mode
            recreate()
        }
        // refresh Shizuku state when returning from its app
        ShizukuManager.refresh()
        UsageFeed.active = true
    }

    override fun onPause() {
        UsageFeed.active = false
        super.onPause()
    }
}

@Composable
fun TVSettingsScreen() {
    val activity = LocalContext.current as Activity
    val apps = remember { mutableStateListOf<AppRecord>() }
    var loading by remember { mutableStateOf(true) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var selectedPackage by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf(AppFilter.ALL) }
    var sort by remember { mutableStateOf(AppSort.RAM) }
    var sortAscending by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val shizukuState by ShizukuManager.state
    val backFocus = remember { FocusRequester() }
    var backFocused by remember { mutableStateOf(false) }

    // CPU and RAM for the header and the rows, sampled while this screen is in front
    val usageReady = shizukuState == ShizukuManager.State.READY
    LaunchedEffect(UsageFeed.active, usageReady) {
        if (!UsageFeed.active || !usageReady) return@LaunchedEffect
        while (true) {
            onIo { ResourceUsage.sample() }?.let { UsageFeed.snapshot = it }
            delay(3_000)
        }
    }

    val visibleApps by remember {
        derivedStateOf {
            apps.filter {
                val q = query.trim()
                (q.isEmpty() || it.appName.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true)) &&
                when (filter) {
                    AppFilter.ALL -> true
                    AppFilter.USER -> !it.isSystem
                    AppFilter.SYSTEM -> it.isSystem
                }
            }.sortedWith(
                (when (sort) {
                    AppSort.CPU -> compareByDescending<AppRecord> { UsageFeed.snapshot?.perPackage?.get(it.packageName)?.cpuPercent ?: 0f }
                    AppSort.RAM -> compareByDescending<AppRecord> { UsageFeed.snapshot?.perPackage?.get(it.packageName)?.rssKb ?: 0L }
                    else -> sort.comparator
                }).let { c -> if (sortAscending) c.reversed() else c }
            )
        }
    }


    LaunchedEffect(reloadTick) {
        loading = true
        val loaded = onIo { AppRegistry(activity).loadInstalledApps() }
        apps.clear()
        apps.addAll(loaded)
        if (apps.none { it.packageName == selectedPackage }) {
            selectedPackage = (visibleApps.firstOrNull() ?: apps.firstOrNull())?.packageName
        }
        loading = false
    }

    // self-grant WRITE_SECURE_SETTINGS once connected
    LaunchedEffect(shizukuState) {
        if (shizukuState == ShizukuManager.State.READY) {
            onIo { ShizukuManager.grantWriteSecureSettings() }
        }
    }

    // running apps; other apps' processes are only visible via Shizuku
    LaunchedEffect(reloadTick, shizukuState, loading) {
        if (!loading && shizukuState == ShizukuManager.State.READY) {
            val running = onIo { AppActionController.runningPackages() }
            apps.forEach { it.isActive = it.packageName in running }
        }
    }

    val selectedApp = apps.firstOrNull { it.packageName == selectedPackage }
    val showSkeleton = loading && apps.isEmpty()

    // Back: first press focuses the < button, second exits
    BackHandler(enabled = !showSkeleton && !backFocused) { backFocus.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Oled.background)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Row(modifier = Modifier.widthIn(max = Dimens.contentMaxWidth).fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    "TV Settings", color = Oled.textPrimary, fontSize = 18.sp, lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("App controls", color = Oled.textSecondary, fontSize = 10.sp, lineHeight = 12.sp)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f).padding(start = 8.dp, end = 8.dp)) {
                    if (selectedApp != null) {
                        Text(
                            selectedApp.appName, color = Oled.textPrimary, fontSize = 16.sp, lineHeight = 20.sp,
                            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            selectedApp.packageName, color = Oled.textSecondary, fontSize = 10.sp, lineHeight = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    } else if (showSkeleton) {
                        val a = rememberSkeletonAlpha()
                        SkeletonBox(a, Modifier.width(140.dp).height(14.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        SkeletonBox(a, Modifier.width(200.dp).height(9.dp))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
                    ShizukuStatusButton(shizukuState) { ShizukuManager.requestPermission(activity) }
                    CompactIconButton(Icons.Default.Settings, "Android settings", Oled.row) {
                        AppActionController.openSystemSettings(activity)
                    }
                    CompactIconButton(Icons.Default.Build, "Advanced settings", Oled.row) {
                        activity.startActivity(Intent(activity, AdvancedSettingsActivity::class.java))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(modifier = Modifier.widthIn(max = Dimens.contentMaxWidth).fillMaxSize()) {
            Card(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = Oled.card),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    if (showSkeleton) {
                        AppListSkeleton()
                    } else {
                        // Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CompactIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Back", modifier = Modifier.focusRequester(backFocus).onFocusChanged { backFocused = it.isFocused }) { activity.finish() }
                            CompactSearchField(query, { query = it }, Modifier.weight(1.3f))
                            CompactDropdown(
                                options = AppFilter.values().map { it.name to it.label },
                                selectedId = filter.name,
                                modifier = Modifier.weight(1f),
                            ) { filter = AppFilter.valueOf(it) }
                            CompactDropdown(
                                options = AppSort.values().map { it.name to it.label },
                                selectedId = sort.name,
                                modifier = Modifier.weight(1f),
                            ) { sort = AppSort.valueOf(it) }
                            CompactIconButton(
                                if (sortAscending) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                if (sortAscending) "Ascending" else "Descending"
                            ) { sortAscending = !sortAscending }
                            CompactIconButton(Icons.Default.Refresh, "Refresh") {
                                ShizukuManager.refresh()
                                reloadTick++
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Apps (${visibleApps.size})",
                                color = Oled.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            UsageFeed.snapshot?.let { usage ->
                                Text(
                                    "CPU ${formatCpu(usage.cpuPercent)}   RAM ${"%.1f / %.1f GB".format(usage.memUsedKb / 1048576.0, usage.memTotalKb / 1048576.0)}",
                                    color = Oled.textSecondary, fontSize = 9.sp,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        if (visibleApps.isEmpty()) EmptyNote("No apps")
                        val listState = key(filter, sort, sortAscending, query, reloadTick) { rememberLazyListState() }
                        val thumbScope = rememberCoroutineScope()
                        LazyColumn(state = listState, contentPadding = PaddingValues(end = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxSize().drawWithContent { drawContent(); drawListThumb(listState) }.thumbDrag(listState, thumbScope)) {
                            items(visibleApps, key = { it.packageName }) { app ->
                                AppListRow(
                                    app = app,
                                    selected = app.packageName == selectedPackage,
                                    onSelect = { selectedPackage = app.packageName }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            val paneSections = remember { mutableStateMapOf<String, Boolean>() }
            if (selectedApp != null) {
                // key(record): a reload replaces records with defaults, so rebuild the pane
                key(selectedApp) {
                    AppDetailPane(
                        app = selectedApp,
                        paneSections = paneSections,
                        context = activity,
                        shizukuState = shizukuState,
                        onHomeHolderChanged = { pinned ->
                            apps.forEach { if (it.isLauncher) it.defaultLauncher = it.packageName == pinned }
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            } else if (showSkeleton) {
                DetailSkeleton(Modifier.weight(1f).fillMaxHeight())
            } else {
                Card(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    colors = CardDefaults.cardColors(containerColor = Oled.card),
                    shape = RoundedCornerShape(14.dp)
                ) { EmptyNote("No apps found") }
            }
        }
    }
}

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
fun AppListRow(app: AppRecord, selected: Boolean, onSelect: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
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
            if (app.isSystem) SystemTag()
        }
        Box(modifier = Modifier.width(20.dp), contentAlignment = Alignment.Center) {
            if (app.isActive && app.isEnabled) {
                Box(modifier = Modifier.size(6.dp).background(Oled.okGreen, CircleShape))
            }
        }
    }
}

// ---------- Selected app pane ----------

@Composable
fun AppDetailPane(
    paneSections: SnapshotStateMap<String, Boolean>,
    app: AppRecord,
    context: Context,
    shizukuState: ShizukuManager.State,
    onHomeHolderChanged: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val shizukuReady = shizukuState == ShizukuManager.State.READY
    var screenOffMs by remember { mutableLongStateOf(0L) }
    val hasLaunch = remember(app.packageName) {
        context.packageManager.getLaunchIntentForPackage(app.packageName) != null
    }
    val dreamUiAvailable = remember { AppActionController.dreamSettingsAvailable(context) }
    val canWrite = shizukuReady ||
        context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED
    var primaryLoaded by remember { mutableStateOf(false) }
    var secureLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(app.packageName, shizukuReady) {
        val pm = context.packageManager
        val pkg = app.packageName
        coroutineScope {
            val perms = async(Dispatchers.IO) { AppInspector.loadPermissions(pm, pkg) }
            val ops = async(Dispatchers.IO) { if (shizukuReady) AppInspector.loadAppOps(pm, pkg) else emptyList() }
            val idle = async(Dispatchers.IO) { if (shizukuReady) AppActionController.idleWhitelistEntries() else null }
            val widget = async(Dispatchers.IO) { if (app.isLauncher) AppActionController.widgetBindGranted(context, pkg) else null }
            val loadedOps = ops.await()
            val loadedIdle = idle.await()
            if (shizukuReady) {
                if (loadedIdle != null) {
                    app.idleWhitelisted = pkg in loadedIdle
                    app.idleSystem = loadedIdle[pkg] == true
                }
                // ignore = exempt from auto-revoke
                app.autoRevokePerms = loadedOps.firstOrNull { it.op == AppInspector.AUTO_REVOKE_OP && !it.uidMode }?.mode != "ignore"
                val notificationMode = loadedOps.firstOrNull { it.op == AppInspector.NOTIFICATIONS_OP && !it.uidMode }?.mode
                app.notificationsAllowed = notificationMode != "ignore" && notificationMode != "deny"
            }
            widget.await()?.let { app.appBindWidgets = it }
            app.permissions.clear(); app.permissions.addAll(perms.await())
            app.appOps.clear(); app.appOps.addAll(loadedOps)
        }
        primaryLoaded = true
    }

    // re-read secure-setting state whenever an observed key changes
    val secureTick = rememberSecureSettingsTick()
    LaunchedEffect(app.packageName, secureTick, shizukuReady) {
        if (secureTick > 0) delay(200) // coalesce bursts of writes
        val pkg = app.packageName
        coroutineScope {
            val a11y = async(Dispatchers.IO) { ServiceSettings.accessibilityServices(context, pkg) }
            val listeners = async(Dispatchers.IO) { ServiceSettings.notificationListeners(context, pkg) }
            val imes = async(Dispatchers.IO) { ServiceSettings.inputMethods(context, pkg) }
            val dreams = async(Dispatchers.IO) { ServiceSettings.dreamServices(context, pkg) }
            val vpn = async(Dispatchers.IO) { ServiceSettings.declaresVpnService(context, pkg) }
            val alwaysOn = async(Dispatchers.IO) { ServiceSettings.alwaysOnVpnApp(context) }
            val lockdown = async(Dispatchers.IO) { ServiceSettings.alwaysOnVpnLockdown(context) }
            val timeout = async(Dispatchers.IO) { ServiceSettings.screenOffTimeoutMs(context) }
            app.accessibilityServices.clear(); app.accessibilityServices.addAll(a11y.await())
            app.notificationListeners.clear(); app.notificationListeners.addAll(listeners.await())
            app.inputMethods.clear(); app.inputMethods.addAll(imes.await())
            app.dreams.clear(); app.dreams.addAll(dreams.await())
            app.isVpn = vpn.await()
            app.alwaysOnVpn = alwaysOn.await() == pkg
            app.vpnLockdown = lockdown.await()
            screenOffMs = timeout.await()
        }
        secureLoaded = true
    }

    // skeleton until state is read, so toggles never visibly flip
    if (!(primaryLoaded && secureLoaded)) {
        DetailSkeleton(modifier)
        return
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Oled.card),
        shape = RoundedCornerShape(14.dp)
    ) {
        var showUnexported by remember { mutableStateOf(ActivityPrefs.showUnexported(context)) }
        val allActivities = remember(app.packageName) { ActivityList.load(context, app.packageName) }
        val activities = allActivities.filter { showUnexported || !it.starred }
        var openActivity by remember(app.packageName) { mutableStateOf<String?>(null) }
        var pendingIcon by remember(app.packageName) { mutableStateOf<String?>(null) }
        var pendingLabel by remember(app.packageName) { mutableStateOf<String?>(null) }
        LaunchedEffect(openActivity) {
            pendingIcon = null
            pendingLabel = null
        }
        var details by remember(app.packageName) { mutableStateOf<AppDetails?>(null) }
        var confirmPending by remember(app.packageName) { mutableStateOf<String?>(null) }
        var manifestText by remember(app.packageName) { mutableStateOf<String?>(null) }
        var backupPicking by remember(app.packageName) { mutableStateOf(false) }
        val secInfo = paneSections["Info"] ?: false
        val secActions = paneSections["Actions"] ?: true
        val secPerms = paneSections["Perms"] ?: true
        val secVpn = paneSections["Vpn"] ?: true
        val secA11y = paneSections["A11y"] ?: true
        val secNotif = paneSections["Notif"] ?: true
        val secKbd = paneSections["Kbd"] ?: true
        val secDreams = paneSections["Dreams"] ?: true
        val secOps = paneSections["Ops"] ?: true
        val secActs = paneSections["Acts"] ?: true
        var usageTick by remember { mutableIntStateOf(0) }
        LaunchedEffect(app.packageName, usageTick) { details = onIo { AppDetailsLoader.load(context, app.packageName) } }
        val live = UsageFeed.snapshot?.perPackage?.get(app.packageName)
        val liveText = when {
            !shizukuReady -> "Needs Shizuku"
            UsageFeed.snapshot == null -> "..."
            live == null -> "Not running"
            else -> "${formatCpu(live.cpuPercent)}  \u00B7  ${formatKb(live.rssKb)}"
        }
        if (backupPicking) {
            FileBrowser(
                title = "Backup APK to",
                start = "/sdcard/Download",
                dirMode = true,
                onPick = { dir ->
                    backupPicking = false
                    scope.launch {
                        val (ok, msg) = onIo { AppBackup.run(context, app.packageName, dir) }
                        context.toast(if (ok) "Saved $msg" else "Backup failed: $msg")
                    }
                },
                onDismiss = { backupPicking = false },
            )
        }
        manifestText?.let { text -> TextDialog("Manifest", text) { manifestText = null } }
        confirmPending?.let { what ->
            val pkg = app.packageName
            val (title, text, label, command) = when (what) {
                "data" -> listOf(
                    "Clear all data?", "$pkg loses its settings, logins and databases. This can't be undone.",
                    "Clear data", "pm clear $pkg",
                )
                "disable" -> listOf("Disable this app?", "Stops $pkg from running until it is enabled again.", "Disable", "")
                "enable" -> listOf("Enable this app?", "Turns $pkg back on.", "Enable", "")
                else -> listOf(
                    "Uninstall?", "Removes $pkg for this user. For a system app it is only hidden for this user.",
                    "Uninstall", "pm uninstall --user 0 $pkg",
                )
            }
            ConfirmDialog(
                title = title,
                text = text,
                confirmLabel = label,
                onConfirm = {
                    confirmPending = null
                    scope.launch {
                        if (what == "enable" || what == "disable") {
                            app.isEnabled = onIo { AppActionController.setAppEnabled(context, pkg, what == "enable") }
                            context.toast("Done")
                        } else {
                            val ok = Regex("[A-Za-z0-9_.]+").matches(pkg) && onIo { Shell.exec(command).ok }
                            context.toast(if (ok) "Done" else "Failed. Is Shizuku running?")
                        }
                    }
                },
                onDismiss = { confirmPending = null },
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {

            item { SectionHeader("Actions", secActions) { paneSections["Actions"] = !secActions } }
            if (secActions) {
            if (hasLaunch) {
                indentedItem { ActionRow("Launch", Icons.Default.PlayArrow) { AppActionController.launchApp(context, app.packageName) } }
            }
            indentedItem { ActionRow("App info", Icons.Default.Info) { AppActionController.openAppInfo(context, app.packageName) } }
            indentedItem {
                ActionRow("Force stop *", Icons.Default.Close, enabled = shizukuReady) {
                    scope.launch {
                        if (AppActionController.forceStopApp(context, app.packageName)) {
                            // re-check: the system may restart it
                            delay(300)
                            app.isActive = onIo {
                                app.packageName in AppActionController.runningPackages()
                            }
                        }
                    }
                }
            }

            indentedItem {
                ActionRow(
                    if (app.isEnabled) "Disable *" else "Enable *",
                    Icons.Default.Lock,
                    enabled = shizukuReady,
                    subtitle = if (app.isSystem && app.isEnabled) "Disabling a system app can break the device" else null,
                ) { confirmPending = if (app.isEnabled) "disable" else "enable" }
            }
            indentedItem {
                ActionRow("Clear data *", AppIcons.Trash, enabled = shizukuReady) { confirmPending = "data" }
            }
            indentedItem {
                ActionRow("Uninstall *", AppIcons.Exit, enabled = shizukuReady) { confirmPending = "uninstall" }
            }
            indentedItem {
                ActionRow("Backup APK *", AppIcons.Card, enabled = shizukuReady) { backupPicking = true }
            }
            }
            item { SectionHeader("Details", secInfo) { paneSections["Info"] = !secInfo } }
            if (secInfo) {
            indentedItem { StatRow("Version", details?.version ?: "...") }
            indentedItem { StatRow("Installed", formatDateTime(app.firstInstallTime)) }
            indentedItem { StatRow("Updated", formatDateTime(app.lastUpdateTime)) }
            indentedItem { StatRow("Installed by", details?.installer ?: "...") }
            details?.let { d ->
                val appBytes = d.appBytes
                val dataBytes = d.dataBytes
                val cacheBytes = d.cacheBytes
                if (appBytes != null && dataBytes != null && cacheBytes != null) {
                    indentedItem { StatRow("Total size", formatBytes(context, appBytes + dataBytes + cacheBytes)) }
                    indentedItem { StatRow("App and data", "${formatBytes(context, appBytes)} and ${formatBytes(context, dataBytes)}") }
                    indentedItem { StatRow("Cache size", formatBytes(context, cacheBytes)) }
                } else if (d.needsUsageAccess) {
                    indentedItem {
                        ActionRow("Allow usage access for sizes *", Icons.Default.Info, enabled = shizukuReady) {
                            scope.launch {
                                onIo { Shell.appOpsSet(context.packageName, "GET_USAGE_STATS", "allow") }
                                usageTick++
                            }
                        }
                    }
                } else {
                    indentedItem { StatRow("Size", "Not available") }
                }
            }
            indentedItem { StatRow("CPU and RAM", liveText) }
            indentedItem {
                ActionRow("View manifest", AppIcons.Layers) {
                    scope.launch { manifestText = onIo { AppManifest.describe(context, app.packageName) } }
                }
            }
            }
            if (app.isVpn) {
                item { SectionHeader("VPN", secVpn) { paneSections["Vpn"] = !secVpn } }
                if (secVpn) {
                indentedItem {
                    ToggleRow(
                        "Always-on VPN",
                        app.alwaysOnVpn,
                        subtitle = "Takes effect after a restart",
                        enabled = canWrite,
                    ) { want ->
                        scope.launch {
                            val ok = onIo { ServiceSettings.setAlwaysOnVpn(context, app.packageName, want) }
                            if (!ok) context.toast("Couldn't change always-on VPN")
                            app.alwaysOnVpn = onIo { ServiceSettings.alwaysOnVpnApp(context) } == app.packageName
                        }
                    }
                }
                indentedItem {
                    ToggleRow(
                        "Block without VPN",
                        app.vpnLockdown,
                        subtitle = "Blocks all traffic while the VPN is down; applies after a restart",
                        enabled = canWrite && app.alwaysOnVpn,
                    ) { want ->
                        scope.launch {
                            val ok = onIo { ServiceSettings.setAlwaysOnVpnLockdown(context, want) }
                            if (!ok) context.toast("Couldn't change lockdown")
                            app.vpnLockdown = onIo { ServiceSettings.alwaysOnVpnLockdown(context) }
                        }
                    }
                }
                }
            }

            if (app.accessibilityServices.isNotEmpty()) {
                item { SectionHeader("Accessibility services", secA11y) { paneSections["A11y"] = !secA11y } }
                if (secA11y) {
            indentedItems(app.accessibilityServices, key = { "a11y:" + it.component }) { svc ->
                ToggleRow(svc.label, svc.enabled, subtitle = svc.detail, enabled = canWrite) { want ->
                    scope.launch {
                        val ok = onIo { ServiceSettings.setAccessibilityEnabled(context, svc.component, want) }
                        if (!ok) context.toast("Couldn't change ${svc.label}")
                        val fresh = onIo { ServiceSettings.accessibilityServices(context, app.packageName) }
                        app.accessibilityServices.clear()
                        app.accessibilityServices.addAll(fresh)
                    }
                }
            }
                }
            }

            if (app.notificationListeners.isNotEmpty()) {
                item { SectionHeader("Notification listeners", secNotif) { paneSections["Notif"] = !secNotif } }
                if (secNotif) {
            indentedItems(app.notificationListeners, key = { "nl:" + it.component }) { svc ->
                ToggleRow(svc.label, svc.enabled, subtitle = svc.detail, enabled = canWrite) { want ->
                    scope.launch {
                        val ok = onIo { ServiceSettings.setNotificationListenerEnabled(context, svc.component, want) }
                        if (!ok) context.toast("Couldn't change ${svc.label}")
                        val fresh = onIo { ServiceSettings.notificationListeners(context, app.packageName) }
                        app.notificationListeners.clear()
                        app.notificationListeners.addAll(fresh)
                    }
                }
            }
                }
            }

            if (app.inputMethods.isNotEmpty()) {
                item { SectionHeader("Keyboard", secKbd) { paneSections["Kbd"] = !secKbd } }
                if (secKbd) {
            indentedItems(app.inputMethods, key = { "ime:" + it.component }) { ime ->
                ToggleRow(
                    ime.label,
                    ime.enabled,
                    subtitle = "Set as active keyboard · ${ime.detail}",
                    enabled = canWrite,
                ) { want ->
                    scope.launch {
                        val error = onIo { ServiceSettings.setInputMethodActive(context, ime.component, want) }
                        if (error != null) context.toast(error)
                        val fresh = onIo { ServiceSettings.inputMethods(context, app.packageName) }
                        app.inputMethods.clear()
                        app.inputMethods.addAll(fresh)
                    }
                }
            }
                }
            }

            if (app.dreams.isNotEmpty()) {
                item { SectionHeader("Screensaver", secDreams) { paneSections["Dreams"] = !secDreams } }
                if (secDreams) {
                indentedItems(app.dreams, key = { "dream:" + it.component }) { dream ->
                    ToggleRow(
                        dream.label,
                        dream.enabled,
                        subtitle = "Use as screensaver · ${dream.detail}",
                        enabled = canWrite,
                    ) { want ->
                        scope.launch {
                            val ok = onIo { ServiceSettings.setDreamActive(context, dream.component, want) }
                            if (!ok) context.toast("Couldn't change screensaver")
                            val fresh = onIo { ServiceSettings.dreamServices(context, app.packageName) }
                            app.dreams.clear()
                            app.dreams.addAll(fresh)
                        }
                    }
                }
                if (dreamUiAvailable) {
                    // prefer the system screen for timeouts
                    indentedItem { ActionRow("Screensaver settings", Icons.Default.Settings) { AppActionController.openDreamSettings(context) } }
                } else {
                    indentedItem {
                        val base = listOf(
                            60_000L to "1 minute", 300_000L to "5 minutes", 600_000L to "10 minutes",
                            900_000L to "15 minutes", 1_800_000L to "30 minutes", 3_600_000L to "1 hour",
                        )
                        val all = if (screenOffMs > 0 && base.none { it.first == screenOffMs }) {
                            (base + (screenOffMs to "${screenOffMs / 60_000} min")).sortedBy { it.first }
                        } else {
                            base
                        }
                        DropdownRow(
                            label = "Screensaver start time",
                            options = all.map { it.first.toString() to it.second },
                            selectedId = screenOffMs.toString(),
                            subtitle = "System-wide",
                            enabled = shizukuReady,
                        ) { id ->
                            scope.launch {
                                val ok = onIo { ServiceSettings.setScreenOffTimeout(id.toLong()) }
                                if (!ok) context.toast("Couldn't change the timeout")
                                screenOffMs = onIo { ServiceSettings.screenOffTimeoutMs(context) }
                            }
                        }
                    }
                }
                }
            }

            item { SectionHeader("Permissions *", secPerms) { paneSections["Perms"] = !secPerms } }
            if (secPerms) {
            indentedItem {
                ToggleRow(
                    "Idle Whitelisted",
                    app.idleWhitelisted,
                    subtitle = if (app.idleSystem) "On the system whitelist (can't be removed)" else "Exempt from Doze battery optimisation",
                    enabled = shizukuReady && !app.idleSystem,
                ) { want ->
                    scope.launch { app.idleWhitelisted = AppActionController.setIdleWhitelist(context, app.packageName, want) }
                }
            }
            indentedItem {
                ToggleRow(
                    "Notifications",
                    app.notificationsAllowed,
                    subtitle = "Allow or silence this app's notifications",
                    enabled = shizukuReady,
                ) { want ->
                    scope.launch {
                        val ok = onIo { AppInspector.setNotifications(app.packageName, want) }
                        if (ok) {
                            app.notificationsAllowed = want
                        } else {
                            context.toast("Couldn't change notifications")
                        }
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= 30) {
                indentedItem {
                    ToggleRow(
                        "Auto revoke permissions",
                        app.autoRevokePerms,
                        subtitle = "Remove permissions if the app goes unused",
                        enabled = shizukuReady,
                    ) { want ->
                        scope.launch {
                            val ok = onIo { AppInspector.setAutoRevoke(app.packageName, want) }
                            if (ok) {
                                app.autoRevokePerms = want
                            } else {
                                context.toast("Couldn't change auto revoke")
                            }
                        }
                    }
                }
            }
            if (app.isLauncher) {
                indentedItem {
                    ToggleRow(
                        label = "Default launcher",
                        checked = app.defaultLauncher,
                        enabled = shizukuReady,
                    ) { want ->
                        scope.launch {
                            val change = AppActionController.setDefaultLauncher(context, app.packageName, want)
                            if (change.success) {
                                onHomeHolderChanged(change.pinnedPackage)
                                app.defaultLauncher = change.pinnedPackage == app.packageName
                            }
                        }
                    }
                }
                indentedItem {
                    ToggleRow(
                        "AppBind widgets",
                        app.appBindWidgets,
                        subtitle = "Lets this launcher host widgets",
                        enabled = shizukuReady,
                    ) { want ->
                        scope.launch { app.appBindWidgets = AppActionController.setWidgetBind(context, app.packageName, want) }
                    }
                }
            }

            indentedItems(app.permissions, key = { "perm:" + it.name }) { perm ->
                ToggleRow(perm.label, perm.granted, subtitle = perm.description, enabled = shizukuReady) { want ->
                    scope.launch {
                        val ok = onIo { AppInspector.setPermission(app.packageName, perm.name, want) }
                        if (!ok) {
                            context.toast("Couldn't ${if (want) "grant" else "revoke"} ${perm.label}")
                        }
                        val fresh = onIo { AppInspector.loadPermissions(context.packageManager, app.packageName) }
                        app.permissions.clear()
                        app.permissions.addAll(fresh)
                    }
                }
            }

            }

            // auto revoke and notifications have their own toggles
            val shownOps = app.appOps.filter { it.op != AppInspector.AUTO_REVOKE_OP && it.op != AppInspector.NOTIFICATIONS_OP }
            if (shownOps.isNotEmpty()) {
                item { SectionHeader("AppOps *", secOps) { paneSections["Ops"] = !secOps } }
                if (secOps) {
            indentedItems(shownOps, key = { "op:" + it.uidMode + ":" + it.op }) { op ->
                DropdownRow(
                    label = op.label,
                    options = AppInspector.APP_OP_MODES,
                    selectedId = op.mode,
                    subtitle = if (op.uidMode) "Applies to the whole UID" else null,
                    enabled = shizukuReady,
                ) { mode ->
                    scope.launch {
                        val ok = onIo { AppInspector.setAppOp(app.packageName, op, mode) }
                        if (!ok) {
                            context.toast("Couldn't change ${op.label}")
                        } else {
                            val i = app.appOps.indexOfFirst { it.op == op.op && it.uidMode == op.uidMode }
                            if (i >= 0) app.appOps[i] = op.copy(mode = mode)
                        }
                    }
                }
            }
                }
            }
            if (allActivities.isNotEmpty()) {
                item { SectionHeader("Activities", secActs) { paneSections["Acts"] = !secActs } }
                if (secActs) {
            indentedItem {
                ToggleRow("Show unexported", showUnexported, subtitle = "Activities only Shizuku or root can start") { want ->
                    showUnexported = want
                    ActivityPrefs.setShowUnexported(context, want)
                }
            }
            indentedItems(activities, key = { "act:" + it.className }) { act ->
                ActivityRow(
                    pkg = app.packageName,
                    entry = act,
                    open = openActivity == act.className,
                    onToggle = { openActivity = if (openActivity == act.className) null else act.className },
                    pendingIcon = if (openActivity == act.className) pendingIcon else null,
                    onPendingIcon = { pendingIcon = it },
                    pendingLabel = if (openActivity == act.className) pendingLabel else null,
                    onPendingLabel = { pendingLabel = it },
                )
            }
                }
            }
        }
    }
}

// ---------- Shared rows and buttons ----------

@Composable
fun SectionLabel(title: String) {
    Text(
        title,
        color = Oled.textSecondary,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
fun EmptyNote(text: String) {
    Text(text, color = Oled.textSecondary, fontSize = 10.sp, modifier = Modifier.padding(8.dp))
}

@Composable
fun ActionRow(label: String, icon: ImageVector, enabled: Boolean = true, subtitle: String? = null, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (focused) Oled.rowFocused else Color.Transparent, shape)
            .then(if (focused) Modifier.border(2.dp, Oled.focusBorder, shape) else Modifier)
            .keyDownClick(enabled) { onClick() }
            .clickable(enabled = enabled, interactionSource = interaction, indication = null) { onClick() }
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
            modifier = Modifier.size(20.dp).background(Oled.row, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Oled.textPrimary, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    subtitle: String? = null,
    enabled: Boolean = true,
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

@Composable
fun SystemTag() {
    Box(
        modifier = Modifier
            .background(Oled.row, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text("System", color = Oled.textSecondary, fontSize = 8.sp)
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Oled.textPrimary) } },
        containerColor = Oled.card,
    )
}

@Composable
fun TextDialog(title: String, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Oled.textPrimary) },
        text = {
            Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(text, color = Oled.textSecondary, fontSize = 11.sp, lineHeight = 14.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Oled.textPrimary) } },
        containerColor = Oled.card,
    )
}
