package com.tvsettings.app

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AdvancedSettingsActivity : ComponentActivity() {
    override fun onStop() {
        super.onStop()
        Debloat.clearIndex(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TVSettingsTheme {
                AdvancedSettingsScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ShizukuManager.refresh()
    }
}

@Composable
fun AdvancedSettingsScreen() {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    val shizukuState by ShizukuManager.state
    val shizukuReady = shizukuState == ShizukuManager.State.READY
    var refreshTick by remember { mutableIntStateOf(0) }
    var loaded by remember { mutableStateOf(false) }
    var writeSecure by remember { mutableStateOf(false) }
    var rows by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var vpnDot by remember { mutableStateOf(OverlayPrefs.dotEnabled(context)) }
    var enforcerOn by remember { mutableStateOf(OverlayPrefs.enforcerEnabled(context)) }
    var location by remember { mutableStateOf(false) }
    var adbDebugging by remember { mutableStateOf(false) }
    var wirelessDebugging by remember { mutableStateOf(false) }
    var unknownSources by remember { mutableStateOf(false) }
    var macSupported by remember { mutableStateOf(false) }
    var macRandomization by remember { mutableStateOf(false) }
    var dnsMode by remember { mutableStateOf("opportunistic") }
    var dnsHost by remember { mutableStateOf("") }
    var customDns by remember { mutableStateOf(false) }
    var mockOn by remember { mutableStateOf(MockLocation.isEnabled(context)) }
    var mockLat by remember { mutableStateOf(MockLocation.latitude(context)) }
    var mockLon by remember { mutableStateOf(MockLocation.longitude(context)) }
    var advertisingId by remember { mutableStateOf<String?>(null) }
    var wifiOn by remember { mutableStateOf(false) }
    var currentFix by remember { mutableStateOf("") }
    var locationGranted by remember { mutableStateOf(false) }
    var homeOn by remember { mutableStateOf(false) }
    var homeIntercept by remember { mutableStateOf("") }
    var homeLaunch by remember { mutableStateOf("") }
    var homeBoot by remember { mutableStateOf(false) }
    var showTouches by remember { mutableStateOf(false) }
    var windowAnim by remember { mutableStateOf("1") }
    var transAnim by remember { mutableStateOf("1") }
    var fontScale by remember { mutableStateOf("1") }
    var secInstall by rememberSaveable { mutableStateOf(false) }
    var secDebloat by rememberSaveable { mutableStateOf(false) }
    var debloatUrl by remember { mutableStateOf(Debloat.customUrl(context)) }
    var debloatStatus by remember { mutableStateOf(Debloat.loadedName(context)?.let { "Applied: $it" } ?: "") }
    var debloatDevices by remember { mutableStateOf<List<Debloat.Device>>(emptyList()) }
    var debloatChoice by remember { mutableStateOf("") }
    var quietOn by remember { mutableStateOf(Debloat.quietOn(context)) }
    var npOn by remember { mutableStateOf(false) }
    var cacheMb by remember { mutableStateOf<Long?>(null) }
    var otaOn by remember { mutableStateOf(Debloat.otaOn(context)) }
    var apkUrl by remember { mutableStateOf("https://f-droid.org/F-Droid.apk") }
    var lastDownload by remember { mutableStateOf(ApkDownloads.last(context)) }
    var clearing by remember { mutableStateOf(false) }
    var cacheOnBoost by remember { mutableStateOf(Caches.clearOnBoost(context)) }
    var browsingApk by remember { mutableStateOf(false) }
    var indexBusy by remember { mutableStateOf(false) }
    var listBusy by remember { mutableStateOf(false) }
    var installBusy by remember { mutableStateOf(false) }
    var dlName by remember { mutableStateOf("") }
    var dlFolder by remember { mutableStateOf<String?>(null) }
    var pickingFolder by remember { mutableStateOf(false) }
    var dlProgress by remember { mutableStateOf<ApkDownloads.Progress?>(null) }
    val dlRunning = dlProgress?.running == true
    val dlText = dlProgress?.let { p ->
        when {
            p.total > 0 -> context.getString(R.string.download_progress, (p.done * 100 / p.total).toInt())
            p.running -> context.getString(R.string.download_progress_bytes, (p.done / 1048576).toInt())
            else -> ""
        }
    } ?: ""
    // Polls while a download runs; stops once it has finished
    LaunchedEffect(lastDownload) {
        while (lastDownload >= 0) {
            dlProgress = onIo { ApkDownloads.progress(context) }
            if (dlProgress?.running != true) break
            kotlinx.coroutines.delay(1000)
        }
    }
    // Matches the cached index against this TV; no network unless the automatic update is on
    fun applyIndex(list: List<Debloat.Device>) {
        debloatDevices = list
        debloatStatus = if (list.isEmpty()) "No index yet: press Update indexes" else "Pick your TV from the list"
    }

    fun updateIndexes() {
        indexBusy = true
        scope.launch {
            val result = onIo { runCatching { Debloat.updateIndex(context) } }
            indexBusy = false
            result.fold({ applyIndex(it) }, { debloatStatus = "Index update failed: ${it.message}" })
        }
    }

    // Reads the live CEC setting and the app cache size from the shell, once Shizuku is ready
    LaunchedEffect(shizukuReady) {
        if (shizukuReady) {
            cacheMb = onIo { Caches.totalMb(context) }
        }
    }

    LaunchedEffect(secDebloat) {
        if (secDebloat && debloatDevices.isEmpty()) applyIndex(onIo { Debloat.cachedIndex(context) })
    }

    fun runDevice(d: Debloat.Device) {
        listBusy = true
        scope.launch {
            debloatStatus = onIo {
                runCatching { Debloat.downloadDevice(context, d) }
                    .fold({ "Loaded $it packages for ${d.name}" }, { "Download failed: ${it.message}" })
            }
            listBusy = false
        }
    }
    fun runDebloat(url: String, source: String) {
        listBusy = true
        scope.launch {
            debloatStatus = onIo {
                runCatching { Debloat.download(context, url, source) }
                    .fold({ "Loaded $it packages" }, { "Download failed: ${it.message}" })
            }
            listBusy = false
        }
    }
    var secShizuku by rememberSaveable { mutableStateOf(true) }
    var installUri by remember { mutableStateOf<Uri?>(null) }
    val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            installUri = uri
        }
    }
    var homeDelay by remember { mutableStateOf("1000") }
    var homeRemapOn by remember { mutableStateOf(true) }
    var recentsOn by remember { mutableStateOf(true) }
    var doubleMsSel by remember { mutableStateOf(RemapEngine.doubleMs(context)) }
    var holdMsSel by remember { mutableStateOf(RemapEngine.holdMs(context)) }
    var remapperOnSel by remember { mutableStateOf(RemapEngine.remapperOn(context)) }
    var remapUp by remember { mutableStateOf(false) }
    var remoteMouse by remember { mutableStateOf(false) }
    var secRemoteMouse by rememberSaveable { mutableStateOf(false) }
    var diagOpen by rememberSaveable { mutableStateOf(false) }
    var confirmCaches by remember { mutableStateOf(false) }
    var secStatus by rememberSaveable { mutableStateOf(true) }
    var secNetwork by rememberSaveable { mutableStateOf(false) }
    var secLocation by rememberSaveable { mutableStateOf(false) }
    var secMouse by rememberSaveable { mutableStateOf(false) }
    var secHomeRedirect by rememberSaveable { mutableStateOf(false) }
    var secPrivacy by rememberSaveable { mutableStateOf(false) }
    var secScreensaver by rememberSaveable { mutableStateOf(false) }
    var privacyRaw by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var privacyOn by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var screenOff by remember { mutableStateOf("") }
    var sleepTimeout by remember { mutableStateOf("") }
    var screensaverLabel by remember { mutableStateOf("") }
    var secSleep by rememberSaveable { mutableStateOf(false) }
    var screenDim by remember { mutableStateOf(false) }
    var ambientTo by remember { mutableStateOf("") }
    var ambientOn by remember { mutableStateOf(false) }
    val timeoutOptions =
        if (screenOff.isEmpty() || TIMEOUT_PRESETS.any { it.first == screenOff }) TIMEOUT_PRESETS
        else TIMEOUT_PRESETS + (screenOff to "Custom ($screenOff ms)")
    val latFocus = remember { FocusRequester() }
    val customFocus = remember { FocusRequester() }
    val ambientFocus = remember { FocusRequester() }
    val lonFocus = remember { FocusRequester() }
    val homeApps = remember { AppActionController.homeApps(context.packageManager) }
    val interceptActs = remember(homeIntercept) {
        AppActionController.homeActivitiesOf(context.packageManager, homeIntercept.substringBefore('/'))
    }
    val launchActs = remember(homeLaunch) {
        val pkg = homeLaunch.substringBefore('/')
        ActivityList.load(context, pkg).map { android.content.ComponentName(pkg, it.className).flattenToString() to it.label }
    }
    val launchApps = remember { ActivityList.appChoices(context) }
    var nightMode by remember { mutableStateOf("system") }
    var appTheme by remember { mutableStateOf(AppTheme.mode(context)) }
    var locationMode by remember { mutableIntStateOf(0) }
    var rotationForced by remember { mutableStateOf(false) }
    var userRotation by remember { mutableIntStateOf(0) }
    var command by remember {
        mutableStateOf(
            context.getSharedPreferences(OverlayPrefs.PREFS, Context.MODE_PRIVATE)
                .getString("last_command", "dumpsys deviceidle whitelist") ?: "dumpsys deviceidle whitelist"
        )
    }
    var output by remember { mutableStateOf("") }

    val canChange = shizukuReady || writeSecure
    val pickMap = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshTick++ }

    fun change(label: String, apply: () -> Boolean) {
        scope.launch {
            if (!onIo { apply() }) {
                context.toast("Couldn't change $label")
            }
            refreshTick++
        }
    }

    val secureTick = rememberSecureSettingsTick()
    LaunchedEffect(shizukuState, refreshTick, secureTick) {
        writeSecure = context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") ==
            PackageManager.PERMISSION_GRANTED
        rows = onIo { collectDiagnostics(context, shizukuState) }
        npOn = onIo { ServiceSettings.notificationListeners(context, context.packageName).any { it.enabled } }
        location = onIo { SystemSettings.locationEnabled(context) }
        adbDebugging = onIo { SystemSettings.adbDebugging(context) }
        wirelessDebugging = onIo { SystemSettings.wirelessDebugging(context) }
        unknownSources = onIo { SystemSettings.unknownSources(context) }
        macSupported = onIo { SystemSettings.macRandomizationSupported(context) }
        macRandomization = onIo { SystemSettings.macRandomization(context) }
        onIo { SystemSettings.privateDns(context) }.let { dnsMode = it.mode; dnsHost = it.host }
        advertisingId = onIo { SystemSettings.advertisingId(context) }
        vpnDot = OverlayPrefs.dotEnabled(context)
        enforcerOn = OverlayPrefs.enforcerEnabled(context)
        mockOn = MockLocation.isEnabled(context)
        wifiOn = onIo { SystemSettings.wifiEnabled(context) }
        currentFix = onIo { MockLocation.currentLocation(context) }
        locationGranted = context.checkSelfPermission("android.permission.ACCESS_FINE_LOCATION") == PackageManager.PERMISSION_GRANTED
        homeOn = OverlayPrefs.homeRedirectEnabled(context)
        homeIntercept = OverlayPrefs.homeRedirectTarget(context)
        homeLaunch = OverlayPrefs.homeLaunchTarget(context)
        homeBoot = OverlayPrefs.homeOnBoot(context)
        showTouches = onIo { TableSettings.read(context, "system", "show_touches") == "1" }
        windowAnim = onIo { TableSettings.read(context, "global", "window_animation_scale") ?: "1" }
        transAnim = onIo { TableSettings.read(context, "global", "transition_animation_scale") ?: "1" }
        fontScale = onIo { TableSettings.read(context, "system", "font_scale") ?: "1" }
        privacyOn = onIo { KeySettings.privacy.associate { it.key to KeySettings.isOn(context, it) } }
        privacyRaw = onIo {
            KeySettings.privacy.flatMap { flag ->
                flag.allKeys.map { it to (KeySettings.read(context, it) ?: "unset") }
            }.toMap()
        }
        screenOff = onIo { KeySettings.read(context, "screen_off_timeout") } ?: ""
        sleepTimeout = onIo { KeySettings.read(context, "sleep_timeout") } ?: ""
        screensaverLabel = onIo { currentScreensaver(context) }
        screenDim = onIo { KeySettings.isOn(context, "screen_dimming") }
        ambientTo = onIo { KeySettings.read(context, "ambient_inactivity_to") } ?: ""
        ambientOn = onIo { KeySettings.isOn(context, "ambient_experience_enabled") }
        homeDelay = OverlayPrefs.homeDelayMs(context).toString()
        homeRemapOn = RemapEngine.homeRemap(context)
        recentsOn = RemapEngine.recents(context)
        if (shizukuReady) onIo { RemapEngine.ensure(context, OverlayPrefs.homeLaunchTarget(context)) }
        remapUp = RemapEngine.active(context)
        remoteMouse = RemapEngine.mouseOn(context)
        nightMode = AppTheme.systemNight(context)
        appTheme = AppTheme.mode(context)
        locationMode = onIo { SystemSettings.locationMode(context) }
        mockLat = MockLocation.latitude(context)
        mockLon = MockLocation.longitude(context)
        rotationForced = onIo { SystemSettings.rotationForced(context) }
        userRotation = onIo { SystemSettings.userRotation(context) }
        loaded = true
    }

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
                    context.getString(R.string.advanced_title), color = Oled.textPrimary, fontSize = 18.sp, lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    context.getString(R.string.advanced_status_subtitle), color = Oled.textSecondary,
                    fontSize = 10.sp, lineHeight = 12.sp
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CompactIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Back") { activity.finish() }
                CompactIconButton(Icons.Default.Refresh, "Refresh") { refreshTick++ }
                CompactTextButton("?", context.getString(R.string.about_button)) {
                    context.startActivity(Intent(context, HelpActivity::class.java))
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        // skeleton until everything is read, so a control can't be changed from stale state
        if (!loaded) {
            DetailSkeleton(Modifier.widthIn(max = Dimens.advancedMaxWidth).fillMaxSize())
        } else {
            Card(
                modifier = Modifier.widthIn(max = Dimens.advancedMaxWidth).fillMaxSize(),
                colors = CardDefaults.cardColors(containerColor = Oled.card),
                shape = RoundedCornerShape(14.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    item { SectionHeader(context.getString(R.string.section_status), secStatus) { secStatus = !secStatus } }
                    if (secStatus) {
                        indentedItem {
                            ToggleRow(
                                context.getString(R.string.label_np_access),
                                npOn,
                                subtitle = context.getString(R.string.subtitle_np_access),
                                enabled = true,
                            ) { want ->
                                npOn = want
                                scope.launch {
                                    val component = android.content.ComponentName(context, NowPlayingListener::class.java).flattenToString()
                                    val ok = onIo { ServiceSettings.setNotificationListenerEnabled(context, component, want) }
                                    if (!ok) {
                                        // no way to write the setting from here: the system screen can
                                        npOn = !want
                                        runCatching {
                                            context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                        }.onFailure { context.toast(context.getString(R.string.np_access_unavailable)) }
                                    }
                                }
                            }
                        }
                        indentedItem {
                            ToggleRow(
                                context.getString(R.string.shizuku_permission),
                                shizukuReady,
                                subtitle = context.getString(when (shizukuState) {
                                    ShizukuManager.State.READY -> R.string.shizuku_connected
                                    ShizukuManager.State.CONNECTING -> R.string.shizuku_connecting
                                    ShizukuManager.State.NEEDS_PERMISSION -> R.string.shizuku_needs_permission
                                    ShizukuManager.State.NOT_RUNNING -> R.string.shizuku_startup_note
                                }),
                                enabled = true,
                            ) { want ->
                                if (want) ShizukuManager.requestPermission(activity)
                                else context.toast(context.getString(R.string.shizuku_revoke_hint))
                            }
                        }
                        indentedToggleItems(
                            listOf(
                                ToggleSpec(
                                    "write_secure_settings", R.string.advanced_write_secure_settings, writeSecure,
                                    subtitleRes = R.string.write_secure_settings_subtitle,
                                    enabled = shizukuReady,
                                ) { want ->
                                    scope.launch {
                                        val ok = onIo { ShizukuManager.setWriteSecureSettings(want) }
                                        if (!ok) context.toast("Couldn't change WRITE_SECURE_SETTINGS")
                                        refreshTick++
                                    }
                                },
                                ToggleSpec(
                                    "accessibility_keep_alive", R.string.advanced_keep_accessibility_on, enforcerOn,
                                    subtitleRes = R.string.accessibility_keep_alive_subtitle,
                                    enabled = canChange,
                                ) { want ->
                                    change("keep accessibility on") {
                                        OverlayPrefs.setEnforcer(context, want)
                                        KeepAlive.sync(context)
                                        KeepAlive.applyIdleWhitelist(context, want)
                                        true
                                    }
                                },
                            )
                        )
                        indentedItem {
                            ActionRow(context.getString(R.string.advanced_clear_caches), AppIcons.Layers, enabled = shizukuReady, busy = clearing,
                                subtitle = cacheMb?.let { context.getString(R.string.clear_caches_size, it.toInt()) } ?: context.getString(R.string.clear_caches_subtitle)) { confirmCaches = true }
                            if (confirmCaches) {
                                ConfirmDialog(
                                    title = context.getString(R.string.clear_caches_dialog_title),
                                    text = context.getString(R.string.clear_caches_dialog_text),
                                    confirmLabel = context.getString(R.string.clear_caches_confirm),
                                    onConfirm = {
                                        confirmCaches = false
                                        scope.launch {
                                            clearing = true
                                            val result = onIo { Caches.clear(context) }
                                            clearing = false
                                            cacheMb = onIo { Caches.totalMb(context) }
                                            val freed = (result.beforeMb - result.afterMb).coerceAtLeast(0L)
                                            context.toast(
                                                if (result.ok) context.getString(R.string.clear_caches_done, freed.toInt()) + " (${result.beforeMb} to ${result.afterMb} MB)"
                                                else context.getString(R.string.clear_caches_failed)
                                            )
                                        }
                                    },
                                    onDismiss = { confirmCaches = false },
                                )
                            }
                        }
                        indentedItem {
                            ToggleRow(
                                context.getString(R.string.label_cache_on_boost),
                                cacheOnBoost,
                                subtitle = context.getString(R.string.subtitle_cache_on_boost),
                                enabled = shizukuReady,
                            ) { want ->
                                cacheOnBoost = want
                                Caches.setClearOnBoost(context, want)
                            }
                        }
                    }
                    item { SectionHeader(context.getString(R.string.section_network), secNetwork) { secNetwork = !secNetwork } }
                    if (secNetwork) {
                        indentedItem {
                            ToggleRow(
                                context.getString(R.string.label_ota_freeze),
                                otaOn,
                                subtitle = context.getString(R.string.subtitle_ota_freeze),
                                enabled = shizukuReady,
                            ) { want ->
                                otaOn = want
                                Debloat.setOta(context, want)
                                scope.launch { onIo { Shell.batch(Debloat.otaCommands(want)) } }
                            }
                        }
                    indentedToggleItems(
                        listOfNotNull(
                            ToggleSpec("wifi", R.string.advanced_wifi, wifiOn, enabled = canChange) { want ->
                                change(context.getString(R.string.label_wifi)) { SystemSettings.setWifi(context, want) }
                            },
                            if (macSupported) ToggleSpec("mac_randomization", R.string.advanced_mac_randomization, macRandomization, enabled = canChange) { want ->
                                change(context.getString(R.string.label_mac_randomization)) { SystemSettings.setMacRandomization(context, want) }
                            } else null,
                            ToggleSpec("adb_debugging", R.string.advanced_adb_debugging, adbDebugging, enabled = canChange) { want ->
                                change(context.getString(R.string.label_adb_debugging)) { SystemSettings.setAdbDebugging(context, want) }
                            },
                            ToggleSpec("wireless_debugging", R.string.advanced_wireless_debugging, wirelessDebugging, enabled = canChange) { want ->
                                change("wireless debugging") { SystemSettings.setWirelessDebugging(context, want) }
                            },
                            ToggleSpec(
                                "vpn_status_dot", R.string.advanced_vpn_status_dot, vpnDot,
                                subtitleRes = R.string.vpn_status_dot_subtitle,
                                enabled = canChange,
                            ) { want -> change("VPN status dot") { OverlayPrefs.setDot(context, want) } },
                        )
                    )
                        indentedItem {
                            ActionRow(context.getString(R.string.wireless_debugging_settings), AppIcons.ExternalLink) {
                                runCatching {
                                    activity.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                                }.onFailure {
                                    runCatching { activity.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                                        .onFailure { context.toast(context.getString(R.string.wireless_settings_unavailable)) }
                                }
                            }
                        }
                    indentedItem {
                        val selected = when (dnsMode) {
                            "off" -> "off"
                            "opportunistic" -> "auto"
                            else -> SystemSettings.PRIVATE_DNS_PRESETS.firstOrNull { it.third == dnsHost }?.first ?: "custom"
                        }
                        DropdownRow(
                            label = context.getString(R.string.advanced_private_dns),
                            options = listOf("off" to context.getString(R.string.option_off), "auto" to "Automatic") +
                                SystemSettings.PRIVATE_DNS_PRESETS.map { it.first to it.second } + ("custom" to "Custom"),
                            selectedId = selected,
                            enabled = canChange,
                        ) { id ->
                            customDns = id == "custom"
                            val preset = SystemSettings.PRIVATE_DNS_PRESETS.firstOrNull { it.first == id }
                            when {
                                id == "off" -> change("private DNS") { SystemSettings.setPrivateDns(context, "off") }
                                id == "auto" -> change("private DNS") { SystemSettings.setPrivateDns(context, "opportunistic") }
                                preset != null -> change("private DNS") { SystemSettings.setPrivateDns(context, "hostname", preset.third) }
                            }
                        }
                    }
                    val dnsIsCustom = dnsMode == "hostname" && SystemSettings.PRIVATE_DNS_PRESETS.none { it.third == dnsHost }
                    if (customDns || dnsIsCustom) {
                        indentedItem {
                            TextFieldRow(context.getString(R.string.advanced_custom_hostname), dnsHost, enabled = canChange, subtitle = context.getString(R.string.custom_hostname_subtitle)) { host ->
                                change("private DNS") { SystemSettings.setPrivateDns(context, "hostname", host) }
                            }
                        }
                    }

                    indentedItem {
                        CommandRow(
                            command = command,
                            enabled = shizukuReady,
                            onChange = {
                                command = it
                                context.getSharedPreferences(OverlayPrefs.PREFS, Context.MODE_PRIVATE)
                                    .edit().putString("last_command", it).apply()
                            },
                        ) {
                            scope.launch {
                                val r = onIo {
                                    Shell.runUserCommand(command)
                                }
                                output = "exit ${r.exitCode}\n${r.output.trim()}".take(4000)
                            }
                        }
                    }
                    if (output.isNotEmpty()) indentedItem { OutputBox(output) }

                    }
                    item { SectionHeader(context.getString(R.string.section_privacy), secPrivacy) { secPrivacy = !secPrivacy } }
                    if (secPrivacy) {
                        indentedItem {
                            ToggleRow(
                                context.getString(R.string.label_quiet_telemetry),
                                quietOn,
                                subtitle = context.getString(R.string.subtitle_quiet_telemetry),
                                enabled = shizukuReady,
                            ) { want ->
                                quietOn = want
                                Debloat.setQuiet(context, want)
                                scope.launch { onIo { Shell.batch(Debloat.quietCommands(want)) } }
                            }
                        }
                        indentedToggleItems(KeySettings.privacy.map { flag ->
                            ToggleSpec(
                                flag.key, flag.titleRes, privacyOn[flag.key] == true,
                                subtitle = flag.allKeys.joinToString("\n") { "$it = ${privacyRaw[it] ?: "unset"}" },
                                enabled = canChange,
                            ) { want ->
                                    privacyOn = privacyOn + (flag.key to want)
                                    privacyRaw = privacyRaw + flag.allKeys.associateWith { flag.valueFor(want) }
                                    change(context.getString(flag.titleRes)) { flag.allKeys.all { KeySettings.write(context, it, flag.valueFor(want)) } }
                            }
                        })
                        indentedItem {
                            ActionRow(
                                context.getString(R.string.advanced_reset_advertising_id),
                                AppIcons.Card,
                                enabled = advertisingId != null && canChange,
                            ) {
                                change("advertising ID") { SystemSettings.resetAdvertisingId(context) }
                            }
                        }
                    }
                    item { SectionHeader(context.getString(R.string.section_screensaver_appearance), secScreensaver) { secScreensaver = !secScreensaver } }
                    if (secScreensaver) {
                    indentedItem {
                        DropdownRow(
                            label = context.getString(R.string.advanced_window_animations),
                            options = listOf("0" to context.getString(R.string.option_off), "0.5" to "0.5x", "1" to "1x", "1.5" to "1.5x", "2" to "2x"),
                            selectedId = windowAnim,
                            enabled = canChange,
                        ) { id ->
                            windowAnim = id
                            change("window animations") { Shell.settingsPut("global", "window_animation_scale", id).ok }
                        }
                    }
                    indentedItem {
                        DropdownRow(
                            label = context.getString(R.string.advanced_transition_animations),
                            options = listOf("0" to context.getString(R.string.option_off), "0.5" to "0.5x", "1" to "1x", "1.5" to "1.5x", "2" to "2x"),
                            selectedId = transAnim,
                            enabled = canChange,
                        ) { id ->
                            transAnim = id
                            change("transition animations") { Shell.settingsPut("global", "transition_animation_scale", id).ok }
                        }
                    }
                    indentedItem {
                        DropdownRow(
                            label = context.getString(R.string.advanced_font_size),
                            options = listOf("0.85" to context.getString(R.string.option_small), "1" to context.getString(R.string.option_default), "1.15" to context.getString(R.string.option_large), "1.3" to context.getString(R.string.option_largest)),
                            selectedId = fontScale,
                            enabled = canChange,
                        ) { id ->
                            fontScale = id
                            change("font size") { Shell.settingsPut("system", "font_scale", id).ok }
                        }
                    }
                    indentedToggleItems(listOf(
                        ToggleSpec(
                                "show_touches", R.string.advanced_show_touches, showTouches,
                                subtitleRes = R.string.show_touches_subtitle,
                                enabled = canChange,
                            ) { want ->
                                showTouches = want
                                change("show touches") { TableSettings.put(context, "system", "show_touches", if (want) "1" else "0") }
                            },
                    ))
                    indentedToggleItems(
                        listOfNotNull(
                            ToggleSpec(
                                "force_rotation", R.string.advanced_force_rotation, rotationForced,
                                subtitleRes = R.string.force_rotation_subtitle,
                                enabled = shizukuReady,
                            ) { want -> change("rotation") { SystemSettings.setRotation(context, want, userRotation) } },
                        ),
                    )
                    indentedItem {
                        DropdownRow(
                            label = context.getString(R.string.advanced_rotation),
                            options = listOf("0" to "0°", "1" to "90°", "2" to "180°", "3" to "270°"),
                            selectedId = userRotation.toString(),
                            enabled = shizukuReady,
                        ) { id -> change("rotation") { SystemSettings.setRotation(context, rotationForced, id.toInt()) } }
                    }
                    indentedItem {
                        DropdownRow(
                            label = context.getString(R.string.advanced_dark_mode),
                            options = listOf("system" to context.getString(R.string.option_system), "light" to context.getString(R.string.option_light), "dark" to context.getString(R.string.option_dark)),
                            selectedId = nightMode,
                            subtitle = context.getString(R.string.dark_mode_subtitle),
                            enabled = canChange,
                        ) { id ->
                            nightMode = id
                            change("dark mode") { AppTheme.setSystemNight(context, id) }
                        }
                    }
                    indentedItem {
                        DropdownRow(
                            label = context.getString(R.string.advanced_app_theme),
                            options = listOf("system" to context.getString(R.string.option_system), "light" to context.getString(R.string.option_light), "dark" to context.getString(R.string.option_dark)),
                            selectedId = appTheme,
                            subtitle = context.getString(R.string.app_theme_subtitle),
                        ) { id ->
                            AppTheme.setMode(context, id)
                            appTheme = id
                            activity.recreate()
                        }
                    }
                    }
                    item { SectionHeader(context.getString(R.string.section_screensaver_sleep), secSleep) { secSleep = !secSleep } }
                    if (secSleep) {
                    indentedItem {
                        InfoRow(context.getString(R.string.label_screensaver_current), screensaverLabel.ifEmpty { context.getString(R.string.screensaver_none) })
                    }
                    indentedItem {
                        DropdownRow(
                            label = context.getString(R.string.advanced_screen_off_timeout),
                            focusDown = customFocus,
                            options = timeoutOptions,
                            selectedId = screenOff,
                            subtitle = context.getString(R.string.timeout_presets_subtitle),
                            enabled = canChange,
                        ) { id ->
                            screenOff = id
                            if (ambientTo.isNotEmpty()) ambientTo = id
                            change("screen off timeout") { KeySettings.writeTimeout(context, id) }
                        }
                    }
                    indentedItem {
                        DropdownRow(
                            label = context.getString(R.string.advanced_sleep_timeout),
                            options = if (sleepTimeout.isEmpty() || TIMEOUT_PRESETS.any { it.first == sleepTimeout }) TIMEOUT_PRESETS
                            else TIMEOUT_PRESETS + (sleepTimeout to "Custom ($sleepTimeout ms)"),
                            selectedId = sleepTimeout,
                            subtitle = context.getString(R.string.sleep_timeout_subtitle),
                            enabled = canChange,
                        ) { id ->
                            sleepTimeout = id
                            change("sleep timeout") { KeySettings.write(context, "sleep_timeout", id) }
                        }
                    }
                    indentedToggleItems(listOf(ToggleSpec(
                        "screen_dimming", R.string.advanced_screen_dimming, screenDim,
                        subtitleRes = R.string.screen_dimming_subtitle,
                        enabled = canChange,
                    ) { want ->
                        screenDim = want
                        change("screen dimming") { KeySettings.write(context, "screen_dimming", if (want) "1" else "0") }
                    }))
                    indentedItem {
                        TextFieldRow(
                            context.getString(R.string.advanced_custom_timeout), screenOff, enabled = canChange, focusRequester = customFocus, focusDown = ambientFocus,
                            subtitle = context.getString(R.string.screen_timeout_subtitle),
                        ) { text ->
                            val ms = text.trim().toLongOrNull()
                            if (ms == null || ms < 1_000) {
                                context.toast("Enter milliseconds, at least 1000")
                            } else {
                                screenOff = ms.toString()
                                if (ambientTo.isNotEmpty()) ambientTo = ms.toString()
                                change("screen off timeout") { KeySettings.writeTimeout(context, ms.toString()) }
                            }
                        }
                    }
                    indentedItem {
                        TextFieldRow(
                            context.getString(R.string.advanced_ambient_timeout), ambientTo, enabled = canChange, focusRequester = ambientFocus,
                            subtitle = context.getString(R.string.ambient_timeout_subtitle),
                        ) { text ->
                            val ms = text.trim().toLongOrNull()
                            if (ms == null || ms < 1_000) {
                                context.toast("Enter milliseconds, at least 1000")
                            } else {
                                ambientTo = ms.toString()
                                change("ambient timeout") { KeySettings.write(context, "ambient_inactivity_to", ms.toString()) }
                            }
                        }
                    }
                    indentedToggleItems(listOf(ToggleSpec(
                        "ambient_experience", R.string.advanced_ambient_experience, ambientOn,
                        subtitleRes = R.string.ambient_experience_subtitle,
                        enabled = canChange,
                    ) { want ->
                        ambientOn = want
                        change("ambient experience") { KeySettings.write(context, "ambient_experience_enabled", if (want) "1" else "0") }
                    }))
                    }
                    item { SectionHeader(context.getString(R.string.section_location), secLocation) { secLocation = !secLocation } }
                    if (secLocation) {
                    indentedToggleItems(
                        listOf(
                            ToggleSpec(
                                "location", R.string.section_location, location,
                                subtitleRes = R.string.system_wide_subtitle,
                                enabled = canChange,
                            ) { want -> change("location") { SystemSettings.setLocationEnabled(context, want) } },
                            ToggleSpec(
                                "mock_location", R.string.advanced_mock_location, mockOn,
                                subtitleRes = R.string.mock_location_subtitle,
                                enabled = shizukuReady || mockOn,
                            ) { want ->
                                scope.launch {
                                    val ok = onIo { MockLocation.setEnabled(context, want) }
                                    if (!ok) context.toast("Couldn't enable mock location")
                                    mockOn = MockLocation.isEnabled(context)
                                }
                            },
                        )
                    )
                    indentedItem {
                        DropdownRow(
                            label = context.getString(R.string.advanced_location_method),
                            focusDown = latFocus,
                            options = listOfNotNull(if (!mockOn || locationMode == 0) "0" to context.getString(R.string.option_off) else null, "1" to "GPS only", "2" to "Wi-Fi and network", "3" to "High accuracy"),
                            selectedId = locationMode.toString(),
                            subtitle = context.getString(R.string.location_method_subtitle),
                            enabled = canChange,
                        ) { id ->
                            if (id != "0" || !mockOn) change("location method") { SystemSettings.setLocationMode(context, id.toInt()) }
                        }
                    }
                    indentedItem {
                        TextFieldRow(context.getString(R.string.advanced_latitude), mockLat, subtitle = context.getString(R.string.latitude_subtitle), focusRequester = latFocus, focusDown = lonFocus) { text ->
                            val lat = text.toDoubleOrNull()
                            if (lat == null || lat < -90 || lat > 90) {
                                context.toast("Latitude must be -90 to 90")
                            } else {
                                MockLocation.setCoordinates(context, lat, MockLocation.coordinates(context).second)
                                mockLat = MockLocation.latitude(context)
                            }
                        }
                    }
                    indentedItem {
                        TextFieldRow(context.getString(R.string.advanced_longitude), mockLon, subtitle = context.getString(R.string.longitude_subtitle), focusRequester = lonFocus) { text ->
                            val lon = text.toDoubleOrNull()
                            if (lon == null || lon < -180 || lon > 180) {
                                context.toast("Longitude must be -180 to 180")
                            } else {
                                MockLocation.setCoordinates(context, MockLocation.coordinates(context).first, lon)
                                mockLon = MockLocation.longitude(context)
                            }
                        }
                    }

                    indentedItem {
                        ActionRow(context.getString(R.string.advanced_pick_on_map), Icons.Default.LocationOn, subtitle = context.getString(R.string.pick_on_map_subtitle)) {
                            pickMap.launch(Intent(context, MapPickerActivity::class.java))
                        }
                    }

                    if (!locationGranted) indentedItem {
                        ActionRow(context.getString(R.string.advanced_grant_location_permission), Icons.Default.LocationOn, subtitle = context.getString(R.string.grant_location_permission_subtitle)) {
                            change("location permission") { MockLocation.grantLocation(context) }
                        }
                    }
                    indentedItem { CopyRow(context.getString(R.string.advanced_current_location), currentFix) }
                    }
                    item { SectionHeader(context.getString(R.string.section_home_redirect), secHomeRedirect) { secHomeRedirect = !secHomeRedirect } }
                    if (secHomeRedirect) {
                    indentedItem {
                        ToggleRow(
                            context.getString(R.string.label_builtin_remapper),
                            remapperOnSel,
                            subtitle = context.getString(R.string.subtitle_builtin_remapper),
                            enabled = shizukuReady,
                        ) { want ->
                            remapperOnSel = want
                            scope.launch { onIo { RemapEngine.setRemapperOn(context, want) } }
                        }
                    }
                    indentedItem {
                        DropdownRow(
                            label = context.getString(R.string.label_double_delay),
                            options = listOf("150", "250", "400", "600").map { it to context.getString(R.string.delay_ms, it.toInt()) },
                            selectedId = doubleMsSel.toString(),
                            enabled = shizukuReady && remapperOnSel,
                        ) { id ->
                            doubleMsSel = id.toInt()
                            scope.launch { onIo { RemapEngine.setTiming(context, doubleMsSel, RemapEngine.holdMs(context)) } }
                        }
                    }
                    indentedItem {
                        DropdownRow(
                            label = context.getString(R.string.label_hold_delay),
                            options = listOf("300", "600", "900", "1200").map { it to context.getString(R.string.delay_ms, it.toInt()) },
                            selectedId = holdMsSel.toString(),
                            enabled = shizukuReady && remapperOnSel,
                        ) { id ->
                            holdMsSel = id.toInt()
                            scope.launch { onIo { RemapEngine.setTiming(context, RemapEngine.doubleMs(context), holdMsSel) } }
                        }
                    }
indentedToggleItems(
                        listOf(
                            ToggleSpec(
                                "remote_mouse", R.string.remote_mouse, remoteMouse,
                                subtitleRes = R.string.remote_mouse_subtitle,
                                enabled = shizukuReady && remapperOnSel,
                            ) { want ->
                                remoteMouse = want
                                scope.launch { onIo { RemapEngine.setMouse(context, want) } }
                            },
                        )
                    )
                    indentedItem {
                        ToggleRow(
                            context.getString(R.string.label_home_remap),
                            homeRemapOn,
                            subtitle = context.getString(R.string.subtitle_home_remap),
                            enabled = shizukuReady && remapperOnSel,
                        ) { want ->
                            homeRemapOn = want
                            RemapEngine.setHomeRemap(context, want)
                            scope.launch {
                                val ok = onIo { RemapEngine.sync(context) }
                                if (!ok) context.toast("Remap engine didn't start: ${RemapEngine.lastError}")
                                remapUp = RemapEngine.active(context)
                            }
                        }
                    }
                    indentedToggleItems(
                        listOf(
                            ToggleSpec(
                                "home_redirect", R.string.advanced_home_redirect, homeOn,
                                subtitle = context.getString(R.string.home_redirect_subtitle, homeIntercept.ifEmpty { "nothing" }, homeLaunch.ifEmpty { "nothing" }),
                                enabled = canChange,
                            ) { want -> change("home redirect") { OverlayPrefs.setHomeRedirect(context, want, homeIntercept, homeLaunch) } },
                            ToggleSpec(
                                "redirect_home_on_boot", R.string.advanced_redirect_home_on_boot, homeBoot,
                                subtitleRes = R.string.redirect_home_on_boot_subtitle,
                                enabled = canChange,
                            ) { want ->
                                homeBoot = want
                                change("boot redirect") { OverlayPrefs.setHomeOnBoot(context, want) }
                            },
                        )
                    )
                    indentedItem {
                        DropdownRow(
                            label = context.getString(R.string.advanced_intercept_app),
                            options = homeApps,
                            selectedId = homeIntercept.substringBefore('/'),
                            subtitle = context.getString(R.string.intercept_app_subtitle),
                            enabled = canChange && (homeOn || homeRemapOn),
                        ) { pkg ->
                            val first = AppActionController.homeActivitiesOf(context.packageManager, pkg).firstOrNull()?.first
                            if (first != null) {
                                homeIntercept = first
                                scope.launch { onIo { RemapEngine.updateRules(context, homeLaunch) } }
                                change("home redirect") { OverlayPrefs.setHomeRedirect(context, homeOn, first, homeLaunch) }
                            }
                        }
                    }
                    indentedItem {
                        DropdownRow(
                            label = context.getString(R.string.advanced_intercept_activity),
                            options = interceptActs,
                            selectedId = homeIntercept,
                            subtitle = context.getString(R.string.intercept_activity_subtitle),
                            enabled = canChange && (homeOn || homeRemapOn),
                        ) { comp ->
                            homeIntercept = comp
                            scope.launch { onIo { RemapEngine.updateRules(context, homeLaunch) } }
                            change("home redirect") { OverlayPrefs.setHomeRedirect(context, homeOn, comp, homeLaunch) }
                        }
                    }
                    indentedItem {
                        DropdownRow(
                            label = context.getString(R.string.advanced_launch_app),
                            options = launchApps,
                            selectedId = homeLaunch.substringBefore('/'),
                            subtitle = context.getString(R.string.launch_app_subtitle),
                            enabled = canChange && (homeOn || homeRemapOn),
                        ) { pkg ->
                            val first = ActivityList.load(context, pkg).firstOrNull()
                                ?.let { android.content.ComponentName(pkg, it.className).flattenToString() }
                            if (first != null) {
                                homeLaunch = first
                                scope.launch { onIo { RemapEngine.updateRules(context, first) } }
                                change("home redirect") { OverlayPrefs.setHomeRedirect(context, homeOn, homeIntercept, first) }
                            }
                        }
                    }
                    indentedItem {
                        DropdownRow(
                            label = context.getString(R.string.advanced_launch_activity),
                            options = launchActs,
                            selectedId = homeLaunch,
                            subtitle = context.getString(R.string.launch_activity_subtitle),
                            enabled = canChange && (homeOn || homeRemapOn),
                        ) { comp ->
                            homeLaunch = comp
                            scope.launch { onIo { RemapEngine.updateRules(context, comp) } }
                            change("home redirect") { OverlayPrefs.setHomeRedirect(context, homeOn, homeIntercept, comp) }
                        }
                    }

                    indentedItem {
                        ToggleRow(
                            context.getString(R.string.label_recents_button),
                            recentsOn,
                            subtitle = context.getString(R.string.subtitle_recents_button),
                            enabled = true,
                        ) { want ->
                            recentsOn = want
                            RemapEngine.setRecents(context, want)
                            scope.launch { onIo { RemapEngine.updateRules(context, OverlayPrefs.homeLaunchTarget(context)) } }
                        }
                    }
                    }
                    item { SectionHeader(context.getString(R.string.section_debloat), secDebloat) { secDebloat = !secDebloat } }
                    if (secDebloat) {
                        indentedItem { Text(context.getString(R.string.debloat_disclaimer), color = Oled.textSecondary, fontSize = 10.sp, lineHeight = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)) }
                        indentedItem {
                            ActionRow(context.getString(R.string.label_update_indexes), AppIcons.Card, busy = indexBusy, subtitle = context.getString(R.string.subtitle_update_indexes)) {
                                updateIndexes()
                            }
                        }
                        indentedItem {
                            ActionRow(context.getString(R.string.debloat_clear), AppIcons.Layers) {
                                Debloat.clear(context)
                                debloatStatus = context.getString(R.string.debloat_cleared)
                            }
                        }
                        indentedItem {
                            ActionRow(
                                context.getString(R.string.credit_cleanyourtv), AppIcons.ExternalLink,
                                subtitle = context.getString(R.string.credit_cleanyourtv_subtitle),
                            ) {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Gathin23/cleanyourtv")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }.onFailure { context.toast(context.getString(R.string.no_browser)) }
                            }
                        }
                        indentedItem {
                            val applied = Debloat.appliedDeviceId(context)
                            val options = debloatDevices.map { it.id to Debloat.label(it) }.let { opts ->
                                if (applied != null && opts.none { it.first == applied }) opts + (applied to (Debloat.loadedName(context) ?: applied)) else opts
                            }
                            PickRow(
                                label = context.getString(R.string.label_your_tv),
                                options = options,
                                selectedId = debloatChoice.ifEmpty { applied ?: "" },
                                enabled = debloatDevices.isNotEmpty(),
                                actionDescription = context.getString(R.string.label_download_this_list),
                                actionEnabled = debloatChoice.isNotEmpty(),
                                actionBusy = listBusy,
                                onSelect = { debloatChoice = it },
                                onAction = { debloatDevices.firstOrNull { d -> d.id == debloatChoice }?.let { runDevice(it) } },
                            )
                        }
                        indentedItem {
                            ActionRow(context.getString(R.string.debloat_download_uad), AppIcons.Card,
                                subtitle = context.getString(R.string.debloat_uad_subtitle)) {
                                runDebloat(Debloat.UAD_NG, "uad-ng")
                            }
                        }
                        indentedItem {
                            FieldActionRow(
                                label = context.getString(R.string.debloat_custom_url),
                                value = debloatUrl,
                                placeholder = context.getString(R.string.debloat_custom_subtitle),
                                icon = AppIcons.Card,
                                actionDescription = context.getString(R.string.debloat_download_custom),
                                enabled = debloatUrl.isNotBlank(),
                                busy = listBusy,
                                onCommit = { debloatUrl = it; Debloat.setCustomUrl(context, it) },
                                onAction = {
                                    Debloat.resolve(debloatUrl, debloatChoice)?.let { runDebloat(it, "custom") }
                                },
                            )
                        }
                        if (debloatStatus.isNotEmpty()) indentedItem {
                            Text(debloatStatus, color = Oled.textSecondary, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
                        }
                    }
                    item { SectionHeader(context.getString(R.string.section_install_apk), secInstall) { secInstall = !secInstall } }
                    if (secInstall) {
                        indentedToggleItems(listOf(ToggleSpec(
                            "unknown_sources", R.string.advanced_apps_other_sources, unknownSources,
                            subtitleRes = R.string.unknown_sources_subtitle,
                            enabled = canChange,
                        ) { want -> change("apps from other sources") { SystemSettings.setUnknownSources(context, want) } }))
                        indentedItem {
                            DownloadRow(
                                url = apkUrl,
                                name = dlName.ifEmpty { ApkDownloads.defaultName(apkUrl) },
                                folder = dlFolder ?: "/sdcard/Download",
                                progress = dlText,
                                running = dlRunning,
                                onUrl = { apkUrl = it },
                                onName = { dlName = it },
                                onPickFolder = { pickingFolder = true },
                                onToggle = {
                                    if (dlRunning) {
                                        ApkDownloads.cancel(context)
                                        lastDownload = -1L
                                        dlProgress = null
                                    } else {
                                        ApkDownloads.resolve(apkUrl)?.let { u ->
                                            lastDownload = ApkDownloads.start(context, u, dlFolder, dlName.ifEmpty { ApkDownloads.defaultName(u) })
                                            context.toast(context.getString(R.string.download_started))
                                        }
                                    }
                                },
                            )
                        }
                        if (pickingFolder) {
                            indentedItem {
                                FileBrowser(
                                    title = context.getString(R.string.choose_folder_title),
                                    start = "/sdcard",
                                    dirMode = true,
                                    onPick = { dlFolder = it; pickingFolder = false },
                                    onDismiss = { pickingFolder = false },
                                )
                            }
                        }
                        indentedItem {
                            ActionRow(context.getString(R.string.label_open_folder), AppIcons.ExternalLink) { ApkDownloads.openFolder(context, dlFolder) }
                        }
                        indentedItem {
                            ActionRow(context.getString(R.string.advanced_browse_apk), AppIcons.Folder, busy = installBusy) {
                                // our browser lists folders through Shizuku; without it the system picker is the way
                                if (shizukuReady) browsingApk = true else apkPicker.launch(arrayOf("application/vnd.android.package-archive"))
                            }
                        }
                        if (browsingApk) {
                            indentedItem {
                                FileBrowser(
                                    title = context.getString(R.string.choose_apk_title),
                                    start = "/sdcard/Download",
                                    dirMode = false,
                                    onPick = { path ->
                                        browsingApk = false
                                        installUri = Uri.fromFile(java.io.File(path))
                                    },
                                    onDismiss = { browsingApk = false },
                                )
                            }
                        }
                    }
                    indentedItem {
                        installUri?.let { uri ->
                            ConfirmDialog(
                                title = context.getString(R.string.install_apk_title),
                                text = ApkInstaller.displayName(context, uri),
                                confirmLabel = context.getString(R.string.install_apk_confirm),
                                onConfirm = {
                                    scope.launch {
                                        installBusy = true
                                        val ok = onIo { ApkInstaller.installFromUri(context, uri) }
                                        installBusy = false
                                        if (ok) installUri = null
                                        else context.toast("Install didn't start. Check install permission")
                                    }
                                },
                                onDismiss = { installUri = null },
                            )
                        }
                    }
                    item { SectionHeader(context.getString(R.string.section_diagnostics), diagOpen) { diagOpen = !diagOpen } }
                    if (diagOpen) indentedItems(rows, key = { "diag:" + it.first }) { (label, value) ->
                        InfoRow(label, value)
                    }
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusable() // no focus visuals on purpose: it's not an action, but D-pad scrolling needs focus
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, color = Oled.textSecondary, fontSize = 10.sp, lineHeight = 12.sp)
        Text(value, color = Oled.textPrimary, fontSize = 12.sp, lineHeight = 14.sp)
    }
}

// raw values on purpose: shows exactly what is stored
private fun collectDiagnostics(context: Context, state: ShizukuManager.State): List<Pair<String, String>> {
    val resolver = context.contentResolver
    fun secure(key: String, splitOnColon: Boolean = false): String {
        val raw = Settings.Secure.getString(resolver, key)?.takeIf { it.isNotBlank() } ?: return "(none)"
        return if (splitOnColon) raw.replace(":", "\n") else raw
    }

    val shellIdentity = if (state == ShizukuManager.State.READY) {
        Shell.id().output.trim().substringBefore(' ').ifEmpty { "unknown" }
    } else {
        "shell n/a (Shizuku not connected)"
    }
    // user-added entries only
    val idleWhitelisted = if (state == ShizukuManager.State.READY) {
        val packages = AppActionController.idleWhitelistEntries()?.filterValues { systemOnly -> !systemOnly }?.keys
        when {
            packages == null -> "(couldn't read)"
            packages.isEmpty() -> "(none)"
            else -> packages.sorted().joinToString("\n")
        }
    } else {
        "(Shizuku not connected)"
    }
    val device = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    val settingsTarget = context.packageManager.resolveActivity(Intent(Settings.ACTION_SETTINGS), 0)
        ?.activityInfo?.let { "${it.packageName}/${it.name}" } ?: "none"

    return listOf(
        "Device · shell identity" to "$device · $shellIdentity",
        "Settings opens" to settingsTarget,
        "HOME resolves to" to (AppActionController.currentHomeHolder(context.packageManager) ?: "none pinned"),
        "Active keyboard" to secure("default_input_method"),
        "Enabled keyboards" to secure("enabled_input_methods", splitOnColon = true),
        "Accessibility services" to secure("enabled_accessibility_services", splitOnColon = true),
        "Notification listeners" to secure("enabled_notification_listeners", splitOnColon = true),
        "Screensaver" to secure("screensaver_components"),
        "Always-on VPN" to secure("always_on_vpn_app"),
        "Advertising ID" to (SystemSettings.advertisingId(context) ?: "(none)"),
        "Ad ID app copy" to SystemSettings.advertisingDetails(context),
        "Idle whitelisted" to idleWhitelisted,
    )
}

// Collapsible section title with a chevron; OK or a click toggles it
@Composable
fun SectionHeader(title: String, open: Boolean, onToggle: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (focused) Oled.rowFocused else Color.Transparent, RoundedCornerShape(8.dp))
            .focusable(interactionSource = interaction)
            .keyDownClick { onToggle() }
            .clickable(interactionSource = interaction, indication = null) { onToggle() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title, color = Oled.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (open) "Collapse" else "Expand",
            tint = Oled.textSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

// A section item, indented under its header
fun LazyListScope.indentedItem(content: @Composable () -> Unit) {
    item { Box(modifier = Modifier.padding(start = 12.dp)) { content() } }
}

// The same indent for each element of a list
fun <T> LazyListScope.indentedItems(list: List<T>, key: ((T) -> Any)? = null, content: @Composable (T) -> Unit) {
    items(list, key = key) { element -> Box(modifier = Modifier.padding(start = 12.dp)) { content(element) } }
}

fun LazyListScope.indentedToggleItems(settings: List<ToggleSpec>) {
    indentedItems(settings, key = { it.id }) { ToggleRow(it) }
}

private val TIMEOUT_PRESETS = listOf(
    "30000" to "30 seconds", "60000" to "1 minute", "120000" to "2 minutes", "300000" to "5 minutes",
    "600000" to "10 minutes", "900000" to "15 minutes", "1800000" to "30 minutes", "3600000" to "1 hour",
)

//    Regex("""Cache Size: (\d+)""").findAll(Shell.dumpsys("diskstats").output)
//        .sumOf { it.groupValues[1].toLong() } / (1024 * 1024)

// The label of the screensaver that is selected: its service's label, which is the app's name when it has none of its own
fun currentScreensaver(context: Context): String {
    val raw = Settings.Secure.getString(context.contentResolver, "screensaver_components")?.split(',')?.firstOrNull()?.trim().orEmpty()
    if (raw.isEmpty()) return ""
    val component = ComponentName.unflattenFromString(raw) ?: return raw
    return runCatching { context.packageManager.getServiceInfo(component, 0).loadLabel(context.packageManager).toString() }
        .getOrDefault(component.packageName)
}
