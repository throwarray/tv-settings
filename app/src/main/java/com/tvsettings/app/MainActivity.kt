package com.tvsettings.app

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
    val boostScope = rememberCoroutineScope()
    var boosting by remember { mutableStateOf(false) }
    // Trims memory in the apps behind the screen: they release what they don't need, nothing is killed.
    // The launcher pair and this app are left alone.
    fun boostApps() {
        boosting = true
        boostScope.launch {
            val self = activity.packageName
            val keep = setOf(
                self,
                OverlayPrefs.homeLaunchTarget(activity).substringBefore('/'),
                OverlayPrefs.homeRedirectTarget(activity).substringBefore('/'),
            )
            val targets = UsageFeed.snapshot?.perPackage?.keys.orEmpty().filter { it !in keep }
            val listed = BoostList.packages(activity)
            // the baseline comes first: everything below is what it measures
            val before = onIo { memAvailableKb() }
            if (before == 0L) activity.toast(activity.getString(R.string.memory_unreadable))
            if (targets.isNotEmpty()) onIo { Shell.batch(targets.map { Shell.Cmd.trimMemory(it) }) }
            onIo { Shell.batch(BoostList.killCommands(listed - self, targets)) }
            val caches = if (Caches.clearOnBoost(activity)) onIo { Caches.clear(activity) } else null
            delay(3000)   // the freed memory shows a little after the trim
            val after = onIo { memAvailableKb() }
            val freedMb = ((after - before).coerceAtLeast(0L)) / 1024
            AppEvents.activeTick++
            boosting = false
            activity.toast(
                activity.getString(R.string.boost_done, targets.size, listed.size, freedMb.toInt()) +
                    " (${before / 1024} to ${after / 1024} MB available)" +
                    (caches?.let { " " + activity.getString(R.string.boost_cache, (it.beforeMb - it.afterMb).coerceAtLeast(0L).toInt()) } ?: "")
            )
            // this app, when it is on the list, goes last: stopping it ends this very process
            if (self in listed) {
                delay(1500)
                onIo { Shell.forceStop(self) }
            }
        }
    }
    val apps = remember { mutableStateListOf<AppRecord>() }
    var loading by remember { mutableStateOf(true) }
    var selectedPackage by remember { mutableStateOf<String?>(null) }
    // Once the user engages with the list, nothing moves their selection, focus or scroll on its own.
    var userPicked by remember { mutableStateOf(false) }
    var settled by remember { mutableStateOf(false) }
    // The ranking behind the CPU and RAM sorts is frozen, so rows never reorder under the cursor.
    var rankUsage by remember { mutableStateOf<UsageSnapshot?>(null) }
    var filter by remember { mutableStateOf(AppFilter.ALL) }
    var sort by remember { mutableStateOf(AppSort.RAM) }
    var sortAscending by remember { mutableStateOf(false) }
    // what the list is ranked by is a snapshot, so rows don't jump while it is being used; it is retaken when the list is still
    var rankActive by remember { mutableStateOf<Set<String>>(emptySet()) }
    var rankRefresh by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val shizukuState by ShizukuManager.state
    val backFocus = remember { FocusRequester() }
    val appListState = rememberLazyListState()
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
                    AppSort.CPU -> compareByDescending<AppRecord> { it.packageName in rankActive && it.isEnabled }.thenByDescending {
                        rankUsage?.perPackage?.get(it.packageName)?.cpuPercent?.takeIf(Float::isFinite) ?: 0f
                    }.thenByDescending {
                        rankUsage?.perPackage?.get(it.packageName)?.rssKb ?: 0L
                    }
                    AppSort.RAM -> compareByDescending<AppRecord> { it.packageName in rankActive && it.isEnabled }
                        .thenByDescending { rankUsage?.perPackage?.get(it.packageName)?.rssKb ?: 0L }
                    AppSort.ACTIVE -> compareByDescending<AppRecord> { it.packageName in rankActive && it.isEnabled }
                    else -> sort.comparator
                    }).thenBy { it.appName.lowercase() }.thenBy { it.packageName }
                    .let { c -> if (sortAscending) c.reversed() else c }
            )
        }
    }
    val firstVisiblePackage = visibleApps.firstOrNull()?.packageName

    // The user changed the sort, filter or search, or refreshed: take a fresh ranking, start at the top, select the first app.
    LaunchedEffect(loading, filter, sort, sortAscending, query, AppEvents.reloadTick) {
        if (loading) return@LaunchedEffect
        rankUsage = UsageFeed.snapshot
        rankActive = apps.filter { it.isActive }.mapTo(HashSet()) { it.packageName }
        userPicked = false
        appListState.scrollToItem(0)
        selectedPackage = firstVisiblePackage
    }

    // Usage arrives a few seconds after launch. Until the user engages with the list, rank by it once.
    LaunchedEffect(UsageFeed.snapshot) {
        val sample = UsageFeed.snapshot ?: return@LaunchedEffect
        if (!userPicked && sample.perPackage.isNotEmpty() && rankUsage?.perPackage.isNullOrEmpty()) {
            rankUsage = sample
            rankActive = apps.filter { it.isActive }.mapTo(HashSet()) { it.packageName }
        }
    }

    // After a re-rank the list keeps the old first row in view by key; go back to the top while it is still untouched.
    LaunchedEffect(rankUsage) {
        if (!userPicked && !loading && !rankRefresh) {
            appListState.scrollToItem(0)
            selectedPackage = firstVisiblePackage
        }
        rankRefresh = false
    }

    // Retakes the ranking while the list is still: never during a scroll, and the first visible row stays where it is.
    suspend fun refreshRanking() {
        do {
            snapshotFlow { appListState.isScrollInProgress }.first { !it }
            delay(400)
        } while (appListState.isScrollInProgress)
        val anchor = visibleApps.getOrNull(appListState.firstVisibleItemIndex)?.packageName
        val offset = appListState.firstVisibleItemScrollOffset
        rankRefresh = true
        rankUsage = UsageFeed.snapshot
        rankActive = apps.filter { it.isActive }.mapTo(HashSet()) { it.packageName }
        if (anchor != null) {
            delay(60)
            val index = visibleApps.indexOfFirst { it.packageName == anchor }
            if (index >= 0 && index != appListState.firstVisibleItemIndex) appListState.scrollToItem(index, offset)
        }
    }

    // Running apps and usage keep changing: for the sorts built on them, rank again now and then.
    val liveSort = sort == AppSort.CPU || sort == AppSort.RAM || sort == AppSort.ACTIVE
    LaunchedEffect(liveSort, loading) {
        if (!liveSort || loading) return@LaunchedEffect
        while (true) {
            delay(5_000)
            refreshRanking()
        }
    }

    // An app was ended, disabled or enabled: show it now, as soon as the list is still.
    LaunchedEffect(AppEvents.activeTick) {
        if (!loading && AppEvents.activeTick > 0) {
            delay(400)   // the running state is re-read first
            refreshRanking()
        }
    }

    // An app was uninstalled: its row goes, the list stays where it is, and the next row is selected if it was the one open.
    LaunchedEffect(AppEvents.removed) {
        val gone = AppEvents.removed ?: return@LaunchedEffect
        AppEvents.removed = null
        val index = visibleApps.indexOfFirst { it.packageName == gone }
        val next = (visibleApps.getOrNull(index + 1) ?: visibleApps.getOrNull(index - 1))?.packageName
        apps.removeAll { it.packageName == gone }
        if (selectedPackage == gone) selectedPackage = next
    }

    // Nothing selected (first load, or the selected app went away): show the first app.
    LaunchedEffect(loading, firstVisiblePackage, selectedPackage == null) {
        if (!loading && selectedPackage == null) selectedPackage = firstVisiblePackage
    }

    // The < button takes the initial focus once the list has settled, so no row gets it and scrolls into view.
    LaunchedEffect(loading) {
        if (loading) return@LaunchedEffect
        delay(500)
        runCatching { backFocus.requestFocus() }
        settled = true
    }

    LaunchedEffect(AppEvents.reloadTick) {
        val loaded = onIo { AppRegistry(activity).loadInstalledApps() }
        apps.clear()
        apps.addAll(loaded)
        if (loaded.none { it.packageName == selectedPackage }) {
            selectedPackage = null
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
    // Running = the usage sampler's process list (full package names from /proc), plus ps for anything it missed
    LaunchedEffect(AppEvents.activeTick, AppEvents.reloadTick, shizukuState, loading, UsageFeed.snapshot) {
        if (!loading && shizukuState == ShizukuManager.State.READY) {
            val running = onIo { AppActionController.runningPackages() } + (UsageFeed.snapshot?.perPackage?.keys ?: emptySet())
            apps.forEach { it.isActive = it.packageName in running }
        }
    }

    LaunchedEffect(AppEvents.reloadTick) { onIo { Debloat.load(activity) } }
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
                    stringResource(R.string.app_name), color = Oled.textPrimary, fontSize = 18.sp, lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(stringResource(R.string.app_controls), color = Oled.textSecondary, fontSize = 10.sp, lineHeight = 12.sp)
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
                    BoostButton(enabled = shizukuState == ShizukuManager.State.READY, busy = boosting) { boostApps() }
                    CompactIconButton(Icons.Default.Settings, stringResource(R.string.android_settings), Oled.row) {
                        AppActionController.openSystemSettings(activity)
                    }
                    CompactIconButton(Icons.Default.Build, stringResource(R.string.advanced_settings), Oled.row) {
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
                            CompactIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.back), modifier = Modifier.focusRequester(backFocus).onFocusChanged { backFocused = it.isFocused }) { activity.finish() }
                            CompactSearchField(query, { query = it }, Modifier.weight(1.3f))
                            CompactDropdown(
                                options = AppFilter.values().map { it.name to activity.getString(it.labelRes) },
                                selectedId = filter.name,
                                modifier = Modifier.weight(1f),
                            ) { filter = AppFilter.valueOf(it) }
                            CompactDropdown(
                                options = AppSort.values().map { it.name to activity.getString(it.labelRes) },
                                selectedId = sort.name,
                                modifier = Modifier.weight(1f),
                            ) { sort = AppSort.valueOf(it) }
                            CompactIconButton(
                                if (sortAscending) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                stringResource(if (sortAscending) R.string.ascending else R.string.descending)
                            ) { sortAscending = !sortAscending }
                            CompactIconButton(Icons.Default.Refresh, stringResource(R.string.refresh)) {
                                ShizukuManager.refresh()
                                AppEvents.reloadTick++
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(end = 30.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.apps_count, visibleApps.size),
                                color = Oled.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            UsageFeed.snapshot?.let { usage ->
                                Text(
                                    stringResource(
                                        R.string.cpu_ram_usage,
                                        formatCpu(usage.cpuPercent),
                                        "%.1f / %.1f GB".format(usage.memUsedKb / 1048576.0, usage.memTotalKb / 1048576.0),
                                    ),
                                    color = Oled.textSecondary, fontSize = 9.sp,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        if (visibleApps.isEmpty()) EmptyNote(stringResource(R.string.no_apps))
                        val thumbScope = rememberCoroutineScope()
                        LazyColumn(state = appListState, contentPadding = PaddingValues(end = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxSize().drawWithContent { drawContent(); drawListThumb(appListState) }.thumbDrag(appListState, thumbScope)) {
                            items(visibleApps, key = { it.packageName }) { app ->
                                AppListRow(
                                    app = app,
                                    selected = app.packageName == selectedPackage,
                                    onSelect = {
                                        selectedPackage = app.packageName
                                        userPicked = true
                                    },
                                    onFocus = { if (settled) userPicked = true },
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
                ) { EmptyNote(stringResource(R.string.no_apps_found)) }
            }
        }
    }
}
