package com.tvsettings.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.material.icons.filled.LocationOn
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AdvancedSettingsActivity : ComponentActivity() {
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
    val authorized by ShizukuManager.authorized
    val shizukuReady = shizukuState == ShizukuManager.State.READY
    var refreshTick by remember { mutableIntStateOf(0) }
    var loaded by remember { mutableStateOf(false) }
    var writeSecure by remember { mutableStateOf(false) }
    var logAccess by remember { mutableStateOf(false) }
    var rows by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var vpnDot by remember { mutableStateOf(OverlayPrefs.dotEnabled(context)) }
    var mouseOn by remember { mutableStateOf(OverlayPrefs.mouseEnabled(context)) }
    var enforcerOn by remember { mutableStateOf(OverlayPrefs.enforcerEnabled(context)) }
    var mouseBackend by remember { mutableStateOf(OverlayPrefs.backend(context)) }
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
    var cursorName by remember { mutableStateOf("white") }
    var showTouches by remember { mutableStateOf(false) }
    var windowAnim by remember { mutableStateOf("1") }
    var transAnim by remember { mutableStateOf("1") }
    var fontScale by remember { mutableStateOf("1") }
    var sleepDream by remember { mutableStateOf(false) }
    var secInstall by rememberSaveable { mutableStateOf(false) }
    var browsing by remember { mutableStateOf(false) }
    var installPath by remember { mutableStateOf<String?>(null) }
    var scrollName by remember { mutableStateOf("normal") }
    var homeDelay by remember { mutableStateOf("1000") }
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
    var screenDim by remember { mutableStateOf(false) }
    var ambientTo by remember { mutableStateOf("") }
    var ambientOn by remember { mutableStateOf(false) }
    val timeoutOptions =
        if (screenOff.isEmpty() || TIMEOUT_PRESETS.any { it.first == screenOff }) TIMEOUT_PRESETS
        else TIMEOUT_PRESETS + (screenOff to "Custom ($screenOff ms)")
    var blackAct by remember { mutableStateOf(false) }
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
    var mouseSpeed by remember { mutableStateOf(OverlayPrefs.speed(context)) }
    var resolved by remember { mutableStateOf("") }
    var command by remember {
        mutableStateOf(context.getSharedPreferences(OverlayPrefs.PREFS, Context.MODE_PRIVATE).getString("last_command", "") ?: "")
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
        logAccess = context.checkSelfPermission("android.permission.READ_LOGS") == PackageManager.PERMISSION_GRANTED
        writeSecure = context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") ==
            PackageManager.PERMISSION_GRANTED
        rows = onIo { collectDiagnostics(context, shizukuState) }
        location = onIo { SystemSettings.locationEnabled(context) }
        adbDebugging = onIo { SystemSettings.adbDebugging(context) }
        wirelessDebugging = onIo { SystemSettings.wirelessDebugging(context) }
        unknownSources = onIo { SystemSettings.unknownSources(context) }
        macSupported = onIo { SystemSettings.macRandomizationSupported(context) }
        macRandomization = onIo { SystemSettings.macRandomization(context) }
        onIo { SystemSettings.privateDns(context) }.let { dnsMode = it.mode; dnsHost = it.host }
        advertisingId = onIo { SystemSettings.advertisingId(context) }
        vpnDot = OverlayPrefs.dotEnabled(context)
        mouseOn = OverlayPrefs.mouseEnabled(context)
        enforcerOn = OverlayPrefs.enforcerEnabled(context)
        mouseBackend = OverlayPrefs.backend(context)
        mockOn = MockLocation.isEnabled(context)
        wifiOn = onIo { SystemSettings.wifiEnabled(context) }
        currentFix = onIo { MockLocation.currentLocation(context) }
        locationGranted = context.checkSelfPermission("android.permission.ACCESS_FINE_LOCATION") == PackageManager.PERMISSION_GRANTED
        homeOn = OverlayPrefs.homeRedirectEnabled(context)
        homeIntercept = OverlayPrefs.homeRedirectTarget(context)
        homeLaunch = OverlayPrefs.homeLaunchTarget(context)
        homeBoot = OverlayPrefs.homeOnBoot(context)
        cursorName = OverlayPrefs.cursorColorName(context)
        showTouches = onIo { TableSettings.read(context, "system", "show_touches") == "1" }
        windowAnim = onIo { TableSettings.read(context, "global", "window_animation_scale") ?: "1" }
        transAnim = onIo { TableSettings.read(context, "global", "transition_animation_scale") ?: "1" }
        fontScale = onIo { TableSettings.read(context, "system", "font_scale") ?: "1" }
        sleepDream = onIo { TableSettings.read(context, "secure", "screensaver_activate_on_sleep") == "1" }
        privacyOn = onIo { KeySettings.privacy.associate { it.key to KeySettings.isOn(context, it) } }
        privacyRaw = onIo { KeySettings.privacy.associate { it.key to (KeySettings.read(context, it.key) ?: "unset") } }
        screenOff = onIo { KeySettings.read(context, "screen_off_timeout") } ?: ""
        screenDim = onIo { KeySettings.isOn(context, "screen_dimming") }
        ambientTo = onIo { KeySettings.read(context, "ambient_inactivity_to") } ?: ""
        ambientOn = onIo { KeySettings.isOn(context, "ambient_experience_enabled") }
        scrollName = OverlayPrefs.scrollSpeedName(context)
        homeDelay = OverlayPrefs.homeDelayMs(context).toString()
        blackAct = OverlayPrefs.blackActivity(context)
        nightMode = AppTheme.systemNight(context)
        appTheme = AppTheme.mode(context)
        locationMode = onIo { SystemSettings.locationMode(context) }
        mockLat = MockLocation.latitude(context)
        mockLon = MockLocation.longitude(context)
        rotationForced = onIo { SystemSettings.rotationForced(context) }
        userRotation = onIo { SystemSettings.userRotation(context) }
        mouseSpeed = OverlayPrefs.speed(context)
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
                    "Advanced", color = Oled.textPrimary, fontSize = 18.sp, lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Shizuku and secure-settings status", color = Oled.textSecondary,
                    fontSize = 10.sp, lineHeight = 12.sp
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CompactIconButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Back") { activity.finish() }
                CompactIconButton(Icons.Default.Refresh, "Refresh") { refreshTick++ }
                CompactTextButton("?", "Help") { context.startActivity(Intent(context, HelpActivity::class.java)) }
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
                    item { SectionHeader("Status", secStatus) { secStatus = !secStatus } }
                    if (secStatus) {
                        indentedItem {
                            ActionRow("Clear all app caches *", AppIcons.Layers, enabled = shizukuReady,
                                subtitle = "Caches only. App data and settings are kept") { confirmCaches = true }
                            if (confirmCaches) {
                                ConfirmDialog(
                                    title = "Clear all app caches?",
                                    text = "Frees cache for every app. Nothing else is deleted.",
                                    confirmLabel = "Clear caches",
                                    onConfirm = {
                                        confirmCaches = false
                                        scope.launch {
                                            val ok = onIo { Shell.exec("pm trim-caches 99999999999999").ok }
                                            context.toast(if (ok) "Caches cleared" else "Failed. Is Shizuku running?")
                                        }
                                    },
                                    onDismiss = { confirmCaches = false },
                                )
                            }
                        }
                    indentedItem {
                        // shows the permission, not the daemon; tap to authorise when it's running
                        ToggleRow(
                            "Shizuku",
                            authorized,
                            subtitle = when (shizukuState) {
                                ShizukuManager.State.READY -> "Connected"
                                ShizukuManager.State.CONNECTING -> "Connecting"
                                ShizukuManager.State.NEEDS_PERMISSION -> "Running, needs permission"
                                ShizukuManager.State.NOT_RUNNING ->
                                    if (authorized) "Authorized, service not running" else "Service not running"
                            },
                        ) { ShizukuManager.requestPermission(activity) }
                    }
                    indentedItem {
                        ToggleRow(
                            "WRITE_SECURE_SETTINGS",
                            writeSecure,
                            subtitle = "Optional. Lets some changes work without Shizuku",
                            enabled = shizukuReady,
                        ) { want ->
                            scope.launch {
                                val ok = onIo { ShizukuManager.setWriteSecureSettings(want) }
                                if (!ok) context.toast("Couldn't change WRITE_SECURE_SETTINGS")
                                refreshTick++
                            }
                        }
                    }



                    indentedItem {
                        ToggleRow(
                            "Apps from other sources",
                            unknownSources,
                            subtitle = "Legacy flag; Android 8+ uses a per-app setting",
                            enabled = canChange,
                        ) { want ->
                            change("apps from other sources") { SystemSettings.setUnknownSources(context, want) }
                        }
                    }
                    indentedItem {
                        ToggleRow(
                            "Keep accessibility on",
                            enforcerOn,
                            subtitle = "Watches the accessibility keys, rewrites them every minute, and idle-whitelists* this app",
                            enabled = canChange,
                        ) { want ->
                            change("keep accessibility on") {
                                OverlayPrefs.setEnforcer(context, want)
                                KeepAlive.sync(context)
                                KeepAlive.applyIdleWhitelist(context, want)
                                true
                            }
                        }
                    }



                    }
                    item { SectionHeader("Network", secNetwork) { secNetwork = !secNetwork } }
                    if (secNetwork) {
                    indentedItem {
                        ToggleRow("Wi-Fi", wifiOn, enabled = canChange) { want ->
                            change("Wi-Fi") { SystemSettings.setWifi(context, want) }
                        }
                    }
                    if (macSupported) {
                        indentedItem {
                            ToggleRow("MAC randomization", macRandomization, enabled = canChange) { want ->
                                change("MAC randomization") { SystemSettings.setMacRandomization(context, want) }
                            }
                        }
                    }
                    indentedItem {
                        ToggleRow("ADB debugging", adbDebugging, enabled = canChange) { want ->
                            change("ADB debugging") { SystemSettings.setAdbDebugging(context, want) }
                        }
                    }
                    indentedItem {
                        ToggleRow("Wireless debugging", wirelessDebugging, enabled = canChange) { want ->
                            change("wireless debugging") { SystemSettings.setWirelessDebugging(context, want) }
                        }
                    }
                    indentedItem {
                        ToggleRow(
                            "VPN status dot",
                            vpnDot,
                            subtitle = "Corner dot showing VPN status; uses the accessibility permission",
                            enabled = canChange,
                        ) { want -> change("VPN status dot") { OverlayPrefs.setDot(context, want) } }
                    }
                    indentedItem {
                        ActionRow(
                            "Reset advertising ID",
                            AppIcons.Card,
                            enabled = advertisingId != null && canChange,
                        ) {
                            change("advertising ID") { SystemSettings.resetAdvertisingId(context) }
                        }
                    }

                    indentedItem {
                        val selected = when (dnsMode) {
                            "off" -> "off"
                            "opportunistic" -> "auto"
                            else -> SystemSettings.PRIVATE_DNS_PRESETS.firstOrNull { it.third == dnsHost }?.first ?: "custom"
                        }
                        DropdownRow(
                            label = "Private DNS",
                            options = listOf("off" to "Off", "auto" to "Automatic") +
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
                            TextFieldRow("Custom hostname", dnsHost, enabled = canChange, subtitle = "DNS-over-TLS host name") { host ->
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
                                val r = onIo { Shell.exec(command) }
                                output = "exit ${r.exitCode}\n${r.output.trim()}".take(4000)
                            }
                        }
                    }
                    if (output.isNotEmpty()) indentedItem { OutputBox(output) }

                    }
                    item { SectionHeader("Privacy", secPrivacy) { secPrivacy = !secPrivacy } }
                    if (secPrivacy) {
                        KeySettings.privacy.forEach { flag ->
                            indentedItem {
                                ToggleRow(
                                    flag.title,
                                    privacyOn[flag.key] == true,
                                    subtitle = "${flag.key} = ${privacyRaw[flag.key] ?: "unset"}",
                                    enabled = canChange,
                                ) { want ->
                                    privacyOn = privacyOn + (flag.key to want)
                                    privacyRaw = privacyRaw + (flag.key to flag.valueFor(want))
                                    change(flag.title) { KeySettings.write(context, flag.key, flag.valueFor(want)) }
                                }
                            }
                        }
                    }
                    item { SectionHeader("Screensaver / Appearance", secScreensaver) { secScreensaver = !secScreensaver } }
                    if (secScreensaver) {
                        indentedItem {
                            DropdownRow("Window animations", listOf("0" to "Off", "0.5" to "0.5x", "1" to "Normal", "1.5" to "1.5x"), windowAnim, subtitle = "window_animation_scale",
                                enabled = canChange) { id ->
                                windowAnim = id
                                change("window animations") { TableSettings.put(context, "global", "window_animation_scale", id) }
                            }
                        }
                        indentedItem {
                            DropdownRow("Transition animations", listOf("0" to "Off", "0.5" to "0.5x", "1" to "Normal", "1.5" to "1.5x"), transAnim, subtitle = "transition_animation_scale",
                                enabled = canChange) { id ->
                                transAnim = id
                                change("transition animations") { TableSettings.put(context, "global", "transition_animation_scale", id) }
                            }
                        }
                        indentedItem {
                            DropdownRow("Font size", listOf("0.85" to "Small", "1" to "Normal", "1.15" to "Large", "1.3" to "Largest"),
                                fontScale, subtitle = "font_scale", enabled = canChange) { id ->
                                fontScale = id
                                change("font size") { TableSettings.put(context, "system", "font_scale", id) }
                            }
                        }
                        indentedItem {
                            ToggleRow("Screensaver on sleep", sleepDream, subtitle = "screensaver_activate_on_sleep (on = 1)",
                                enabled = canChange) { want ->
                                sleepDream = want
                                change("screensaver on sleep") { TableSettings.put(context, "secure", "screensaver_activate_on_sleep", if (want) "1" else "0") }
                            }
                        }
                    indentedItem {
                        DropdownRow(
                            label = "Dark mode",
                            options = listOf("system" to "System", "light" to "Light", "dark" to "Dark"),
                            selectedId = nightMode,
                            subtitle = "System-wide night mode",
                            enabled = canChange,
                        ) { id ->
                            nightMode = id
                            change("dark mode") { AppTheme.setSystemNight(context, id) }
                        }
                    }
                    indentedItem {
                        DropdownRow(
                            label = "App theme",
                            options = listOf("system" to "System", "light" to "Light", "dark" to "Dark"),
                            selectedId = appTheme,
                            subtitle = "This app only",
                        ) { id ->
                            AppTheme.setMode(context, id)
                            appTheme = id
                            activity.recreate()
                        }
                    }
                    indentedItem {
                        ToggleRow(
                            "Force rotation",
                            rotationForced,
                            subtitle = "Locks the screen to the chosen rotation (settings and window manager)",
                            enabled = shizukuReady,
                        ) { want -> change("rotation") { SystemSettings.setRotation(context, want, userRotation) } }
                    }
                    indentedItem {
                        DropdownRow(
                            label = "Rotation",
                            options = listOf("0" to "0°", "1" to "90°", "2" to "180°", "3" to "270°"),
                            selectedId = userRotation.toString(),
                            enabled = shizukuReady,
                        ) { id -> change("rotation") { SystemSettings.setRotation(context, rotationForced, id.toInt()) } }
                    }
                        indentedItem {
                            DropdownRow(
                                label = "Screen off timeout",
                                focusDown = customFocus,
                                options = timeoutOptions,
                                selectedId = screenOff,
                                subtitle = "Presets. The field below takes any value",
                                enabled = canChange,
                            ) { id ->
                                screenOff = id
                                if (ambientTo.isNotEmpty()) ambientTo = id
                                change("screen off timeout") { KeySettings.writeTimeout(context, id) }
                            }
                        }
                        indentedItem {
                            TextFieldRow(
                                "Custom timeout (ms)", screenOff, enabled = canChange, focusRequester = customFocus, focusDown = ambientFocus,
                                subtitle = "screen_off_timeout, in milliseconds",
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
                                "Ambient timeout (ms)", ambientTo, enabled = canChange, focusRequester = ambientFocus,
                                subtitle = "ambient_inactivity_to, usually the same as the screen off timeout",
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
                        indentedItem {
                            ToggleRow(
                                "Ambient experience",
                                ambientOn,
                                subtitle = "ambient_experience_enabled (on = 1)",
                                enabled = canChange,
                            ) { want ->
                                ambientOn = want
                                change("ambient experience") { KeySettings.write(context, "ambient_experience_enabled", if (want) "1" else "0") }
                            }
                        }
                        indentedItem {
                            ToggleRow(
                                "Screen dimming",
                                screenDim,
                                subtitle = "screen_dimming (on = 1)",
                                enabled = canChange,
                            ) { want ->
                                screenDim = want
                                change("screen dimming") { KeySettings.write(context, "screen_dimming", if (want) "1" else "0") }
                            }
                        }
                    }
                    item { SectionHeader("Location", secLocation) { secLocation = !secLocation } }
                    if (secLocation) {
                    indentedItem {
                        ToggleRow("Location", location, subtitle = "System-wide", enabled = canChange) { want ->
                            change("location") { SystemSettings.setLocationEnabled(context, want) }
                        }
                    }
                    indentedItem {
                        ToggleRow(
                            "Mock location",
                            mockOn,
                            subtitle = "Fixed position instead of GPS / Wi-Fi",
                            enabled = shizukuReady || mockOn,
                        ) { want ->
                            scope.launch {
                                val ok = onIo { MockLocation.setEnabled(context, want) }
                                if (!ok) context.toast("Couldn't enable mock location")
                                mockOn = MockLocation.isEnabled(context)
                            }
                        }
                    }
                    indentedItem {
                        DropdownRow(
                            label = "Location method",
                            focusDown = latFocus,
                            options = listOfNotNull(if (!mockOn || locationMode == 0) "0" to "Off" else null, "1" to "GPS only", "2" to "Wi-Fi and network", "3" to "High accuracy"),
                            selectedId = locationMode.toString(),
                            subtitle = "Used when mock location is off",
                            enabled = canChange,
                        ) { id ->
                            if (id != "0" || !mockOn) change("location method") { SystemSettings.setLocationMode(context, id.toInt()) }
                        }
                    }
                    indentedItem {
                        TextFieldRow("Latitude", mockLat, subtitle = "Decimal degrees, -90 to 90", focusRequester = latFocus, focusDown = lonFocus) { text ->
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
                        TextFieldRow("Longitude", mockLon, subtitle = "Decimal degrees, -180 to 180", focusRequester = lonFocus) { text ->
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
                        ActionRow("Pick on map", Icons.Default.LocationOn, subtitle = "OpenStreetMap; sets the coordinates above") {
                            pickMap.launch(Intent(context, MapPickerActivity::class.java))
                        }
                    }

                    if (!locationGranted) indentedItem {
                        ActionRow("Grant location permission", Icons.Default.LocationOn, subtitle = "Needs Shizuku; the current location needs it") {
                            change("location permission") { MockLocation.grantLocation(context) }
                        }
                    }
                    indentedItem { CopyRow("Current location", currentFix) }
                    }
                    item { SectionHeader("Mouse Toggle", secMouse) { secMouse = !secMouse } }
                    if (secMouse) {
                    indentedItem {
                        ToggleRow(
                            "Mouse",
                            mouseOn,
                            subtitle = "Double-press Play to toggle. OK clicks, hold OK to drag. Needs Shizuku running",
                            enabled = canChange,
                        ) { want -> change("mouse") { OverlayPrefs.setMouse(context, want) } }
                    }
                    indentedItem {
                        DropdownRow(
                            label = "Mouse speed",
                            options = listOf("slow" to "Slow", "medium" to "Medium", "fast" to "Fast", "faster" to "Faster"),
                            selectedId = mouseSpeed,
                        ) { id ->
                            OverlayPrefs.setSpeed(context, id)
                            mouseSpeed = id
                        }
                    }
                    indentedItem {
                        DropdownRow(
                            label = "Cursor colour",
                            options = listOf(
                                "white" to "White", "yellow" to "Yellow", "cyan" to "Cyan",
                                "green" to "Green", "red" to "Red", "magenta" to "Magenta",
                            ),
                            selectedId = cursorName,
                            subtitle = "Colour of the mouse cursor",
                        ) { id ->
                            cursorName = id
                            OverlayPrefs.setCursorColor(context, id)
                            runCatching { overlayServiceInstance?.refreshCursor() }
                        }
                    }
                    indentedItem {
                        DropdownRow(
                            label = "Scroll speed",
                            options = listOf("slow" to "Slow", "normal" to "Normal", "fast" to "Fast"),
                            selectedId = scrollName,
                            subtitle = "How far Menu and Media Next scroll while the mouse is on",
                        ) { id ->
                            scrollName = id
                            OverlayPrefs.setScrollSpeed(context, id)
                        }
                    }
                    indentedItem {
                        ToggleRow("Show touches", showTouches, subtitle = "show_touches (on = 1)", enabled = canChange) { want ->
                            showTouches = want
                            change("show touches") { TableSettings.put(context, "system", "show_touches", if (want) "1" else "0") }
                        }
                    }
                    }
                    item { SectionHeader("Home redirect", secHomeRedirect) { secHomeRedirect = !secHomeRedirect } }
                    if (secHomeRedirect) {
                    indentedItem {
                        ToggleRow(
                            "Home redirect",
                            homeOn,
                            subtitle = "Intercepts ${homeIntercept.ifEmpty { "nothing" }}, opens ${homeLaunch.ifEmpty { "nothing" }}",
                            enabled = canChange,
                        ) { want ->
                            change("home redirect") { OverlayPrefs.setHomeRedirect(context, want, homeIntercept, homeLaunch) }
                        }
                    }
                    indentedItem {
                        ToggleRow(
                            "Redirect Home on boot",
                            homeBoot,
                            subtitle = "Starts the launch target when the box boots",
                            enabled = canChange,
                        ) { want ->
                            homeBoot = want
                            change("boot redirect") { OverlayPrefs.setHomeOnBoot(context, want) }
                        }
                    }
                    indentedItem {
                        DropdownRow(
                            label = "Intercept app",
                            options = homeApps,
                            selectedId = homeIntercept.substringBefore('/'),
                            subtitle = "App whose home activity gets intercepted",
                            enabled = canChange && homeOn,
                        ) { pkg ->
                            val first = AppActionController.homeActivitiesOf(context.packageManager, pkg).firstOrNull()?.first
                            if (first != null) {
                                homeIntercept = first
                                change("home redirect") { OverlayPrefs.setHomeRedirect(context, homeOn, first, homeLaunch) }
                            }
                        }
                    }
                    indentedItem {
                        DropdownRow(
                            label = "Intercept activity",
                            options = interceptActs,
                            selectedId = homeIntercept,
                            subtitle = "Activity that gets intercepted",
                            enabled = canChange && homeOn,
                        ) { comp ->
                            homeIntercept = comp
                            change("home redirect") { OverlayPrefs.setHomeRedirect(context, homeOn, comp, homeLaunch) }
                        }
                    }
                    indentedItem {
                        DropdownRow(
                            label = "Launch app",
                            options = launchApps,
                            selectedId = homeLaunch.substringBefore('/'),
                            subtitle = "Any app to open when Home is redirected",
                            enabled = canChange && homeOn,
                        ) { pkg ->
                            val first = ActivityList.load(context, pkg).firstOrNull()
                                ?.let { android.content.ComponentName(pkg, it.className).flattenToString() }
                            if (first != null) {
                                homeLaunch = first
                                change("home redirect") { OverlayPrefs.setHomeRedirect(context, homeOn, homeIntercept, first) }
                            }
                        }
                    }
                    indentedItem {
                        DropdownRow(
                            label = "Launch activity",
                            options = launchActs,
                            selectedId = homeLaunch,
                            subtitle = "Activity to open on Home",
                            enabled = canChange && homeOn,
                        ) { comp ->
                            homeLaunch = comp
                            change("home redirect") { OverlayPrefs.setHomeRedirect(context, homeOn, homeIntercept, comp) }
                        }
                    }

                    indentedItem {
                        ToggleRow(
                            "Black screen as activity",
                            blackAct,
                            subtitle = "Covers Home with a black activity instead of an overlay",
                            enabled = canChange && homeOn,
                        ) { want ->
                            blackAct = want
                            OverlayPrefs.setBlackActivity(context, want)
                        }
                    }
                    indentedItem {
                        ToggleRow(
                            "Log access",
                            logAccess,
                            subtitle = "READ_LOGS. Home presses come from the system log. Grant once through Shizuku",
                            enabled = shizukuReady,
                        ) { want ->
                            logAccess = want
                            scope.launch {
                                val ok = onIo { Shell.permission(context.packageName, "android.permission.READ_LOGS", want).ok }
                                if (!ok) context.toast("Couldn't change log access")
                            }
                        }
                    }
                    indentedItem {
                        DropdownRow(
                            label = "Redirect delay",
                            options = listOf(
                                "500" to "0.5 s", "750" to "0.75 s", "1000" to "1 s",
                                "1250" to "1.25 s", "1500" to "1.5 s", "2000" to "2 s",
                            ),
                            selectedId = homeDelay,
                            subtitle = "Black screen before the redirect. Any key other than Home, or navigating, cancels it",
                            enabled = canChange && homeOn,
                        ) { id ->
                            homeDelay = id
                            OverlayPrefs.setHomeDelayMs(context, id.toInt())
                        }
                    }
                    }
                    item { SectionHeader("Install APK", secInstall) { secInstall = !secInstall } }
                    if (secInstall) {
                        indentedItem {
                            ActionRow("Browse for an APK", AppIcons.Exit, enabled = shizukuReady) { browsing = true }
                        }
                    }
                    item {
                        if (browsing) {
                            FileBrowser(
                                title = "Choose an APK",
                                start = "/sdcard",
                                dirMode = false,
                                onPick = { path -> browsing = false; installPath = path },
                                onDismiss = { browsing = false },
                            )
                        }
                        installPath?.let { path ->
                            ConfirmDialog(
                                title = "Install this APK?",
                                text = path,
                                confirmLabel = "Install",
                                onConfirm = {
                                    installPath = null
                                    scope.launch {
                                        val ok = onIo { ApkInstaller.prepareAndInstall(context, path) }
                                        if (!ok) context.toast("Install didn't start. Allow installs for this app, then retry")
                                    }
                                },
                                onDismiss = { installPath = null },
                            )
                        }
                    }
                    item { SectionHeader("Diagnostics", diagOpen) { diagOpen = !diagOpen } }
                    if (diagOpen) indentedItems(rows, key = { "diag:" + it.first }) { (label, value) ->
                        Box(modifier = Modifier.padding(start = 12.dp)) { InfoRow(label, value) }
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
        Shell.exec("id").output.trim().substringBefore(' ').ifEmpty { "unknown" }
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

private val TIMEOUT_PRESETS = listOf(
    "30000" to "30 seconds", "60000" to "1 minute", "120000" to "2 minutes", "300000" to "5 minutes",
    "600000" to "10 minutes", "900000" to "15 minutes", "1800000" to "30 minutes", "3600000" to "1 hour",
)

