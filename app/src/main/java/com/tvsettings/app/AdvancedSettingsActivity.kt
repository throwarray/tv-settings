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
import android.net.Uri
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
    var secInstall by rememberSaveable { mutableStateOf(false) }
    var installUri by remember { mutableStateOf<Uri?>(null) }
    val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            installUri = uri
        }
    }
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
                    item { SectionHeader(context.getString(R.string.section_status), secStatus) { secStatus = !secStatus } }
                    if (secStatus) {
                        indentedItem {
                            ActionRow(context.getString(R.string.advanced_clear_caches), AppIcons.Layers, enabled = shizukuReady,
                                subtitle = context.getString(R.string.clear_caches_subtitle)) { confirmCaches = true }
                            if (confirmCaches) {
                                ConfirmDialog(
                                    title = context.getString(R.string.clear_caches_dialog_title),
                                    text = context.getString(R.string.clear_caches_dialog_text),
                                    confirmLabel = context.getString(R.string.clear_caches_confirm),
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
                    // This toggle represents whether the Shizuku app is authorized, not daemon availability.
                    indentedToggleItems(
                        listOf(
                            ToggleSpec(
                                "shizuku_authorized", R.string.advanced_shizuku, authorized,
                                subtitle = when (shizukuState) {
                                    ShizukuManager.State.READY -> context.getString(R.string.shizuku_connected)
                                    ShizukuManager.State.CONNECTING -> context.getString(R.string.shizuku_connecting)
                                    ShizukuManager.State.NEEDS_PERMISSION -> context.getString(R.string.shizuku_needs_permission)
                                    ShizukuManager.State.NOT_RUNNING -> context.getString(
                                        if (authorized) R.string.shizuku_authorized_not_running else R.string.shizuku_not_running
                                    )
                                },
                            ) { ShizukuManager.requestPermission(activity) },
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
                                "unknown_sources", R.string.advanced_apps_other_sources, unknownSources,
                                subtitleRes = R.string.unknown_sources_subtitle,
                                enabled = canChange,
                            ) { want -> change("apps from other sources") { SystemSettings.setUnknownSources(context, want) } },
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

                    }
                    item { SectionHeader(context.getString(R.string.section_network), secNetwork) { secNetwork = !secNetwork } }
                    if (secNetwork) {
                    indentedToggleItems(
                        listOfNotNull(
                            ToggleSpec("wifi", R.string.advanced_wifi, wifiOn, enabled = canChange) { want ->
                                change("Wi-Fi") { SystemSettings.setWifi(context, want) }
                            },
                            if (macSupported) ToggleSpec("mac_randomization", R.string.advanced_mac_randomization, macRandomization, enabled = canChange) { want ->
                                change("MAC randomization") { SystemSettings.setMacRandomization(context, want) }
                            } else null,
                            ToggleSpec("adb_debugging", R.string.advanced_adb_debugging, adbDebugging, enabled = canChange) { want ->
                                change("ADB debugging") { SystemSettings.setAdbDebugging(context, want) }
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
                        ActionRow(
                            context.getString(R.string.advanced_reset_advertising_id),
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
                            label = context.getString(R.string.advanced_private_dns),
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
                                    val shellResult = Shell.exec(command)
                                    val shellValue = shellResult.output.trim()
                                    if (shellResult.ok && shellValue.isNotEmpty() && shellValue != "null") {
                                        shellResult
                                    } else {
                                        readSettingsCommand(context, command) ?: shellResult
                                    }
                                }
                                output = "exit ${r.exitCode}\n${r.output.trim()}".take(4000)
                            }
                        }
                    }
                    if (output.isNotEmpty()) indentedItem { OutputBox(output) }

                    }
                    item { SectionHeader(context.getString(R.string.section_privacy), secPrivacy) { secPrivacy = !secPrivacy } }
                    if (secPrivacy) {
                        indentedToggleItems(KeySettings.privacy.map { flag ->
                            ToggleSpec(
                                flag.key, flag.titleRes, privacyOn[flag.key] == true,
                                subtitle = "${flag.key} = ${privacyRaw[flag.key] ?: "unset"}",
                                enabled = canChange,
                            ) { want ->
                                    privacyOn = privacyOn + (flag.key to want)
                                    privacyRaw = privacyRaw + (flag.key to flag.valueFor(want))
                                    change(context.getString(flag.titleRes)) { flag.allKeys.all { KeySettings.write(context, it, flag.valueFor(want)) } }
                            }
                        })
                    }
                    item { SectionHeader(context.getString(R.string.section_screensaver_appearance), secScreensaver) { secScreensaver = !secScreensaver } }
                    if (secScreensaver) {
                    indentedToggleItems(
                        listOfNotNull(
                            ToggleSpec(
                                "force_rotation", R.string.advanced_force_rotation, rotationForced,
                                subtitleRes = R.string.force_rotation_subtitle,
                                enabled = shizukuReady,
                            ) { want -> change("rotation") { SystemSettings.setRotation(context, want, userRotation) } },
                            ToggleSpec(
                                "ambient_experience", R.string.advanced_ambient_experience, ambientOn,
                                subtitleRes = R.string.ambient_experience_subtitle,
                                enabled = canChange,
                            ) { want ->
                                ambientOn = want
                                change("ambient experience") { KeySettings.write(context, "ambient_experience_enabled", if (want) "1" else "0") }
                            },
                            ToggleSpec(
                                "screen_dimming", R.string.advanced_screen_dimming, screenDim,
                                subtitleRes = R.string.screen_dimming_subtitle,
                                enabled = canChange,
                            ) { want ->
                                screenDim = want
                                change("screen dimming") { KeySettings.write(context, "screen_dimming", if (want) "1" else "0") }
                            },
                        ),
                    )
                    indentedItem {
                        DropdownRow(
                            label = context.getString(R.string.advanced_dark_mode),
                            options = listOf("system" to "System", "light" to "Light", "dark" to "Dark"),
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
                            options = listOf("system" to "System", "light" to "Light", "dark" to "Dark"),
                            selectedId = appTheme,
                            subtitle = context.getString(R.string.app_theme_subtitle),
                        ) { id ->
                            AppTheme.setMode(context, id)
                            appTheme = id
                            activity.recreate()
                        }
                    }
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
                            options = listOfNotNull(if (!mockOn || locationMode == 0) "0" to "Off" else null, "1" to "GPS only", "2" to "Wi-Fi and network", "3" to "High accuracy"),
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
                    item { SectionHeader(context.getString(R.string.section_mouse_toggle), secMouse) { secMouse = !secMouse } }
                    if (secMouse) {
                    indentedToggleItems(
                        listOf(
                            ToggleSpec(
                                "mouse", R.string.advanced_mouse, mouseOn,
                                subtitleRes = R.string.mouse_toggle_subtitle,
                                enabled = canChange,
                            ) { want -> change("mouse") { OverlayPrefs.setMouse(context, want) } },
                            ToggleSpec(
                                "show_touches", R.string.advanced_show_touches, showTouches,
                                subtitleRes = R.string.show_touches_subtitle,
                                enabled = canChange,
                            ) { want ->
                                showTouches = want
                                change("show touches") { TableSettings.put(context, "system", "show_touches", if (want) "1" else "0") }
                            },
                        )
                    )
                    indentedItem {
                        DropdownRow(
                            label = context.getString(R.string.advanced_mouse_speed),
                            options = listOf("slow" to "Slow", "medium" to "Medium", "fast" to "Fast", "faster" to "Faster"),
                            selectedId = mouseSpeed,
                        ) { id ->
                            OverlayPrefs.setSpeed(context, id)
                            mouseSpeed = id
                        }
                    }
                    indentedItem {
                        DropdownRow(
                            label = context.getString(R.string.advanced_cursor_colour),
                            options = listOf(
                                "white" to "White", "yellow" to "Yellow", "cyan" to "Cyan",
                                "green" to "Green", "red" to "Red", "magenta" to "Magenta",
                            ),
                            selectedId = cursorName,
                            subtitle = context.getString(R.string.cursor_colour_subtitle),
                        ) { id ->
                            cursorName = id
                            OverlayPrefs.setCursorColor(context, id)
                            runCatching { overlayServiceInstance?.refreshCursor() }
                        }
                    }
                    indentedItem {
                        DropdownRow(
                            label = context.getString(R.string.advanced_scroll_speed),
                            options = listOf("slow" to "Slow", "normal" to "Normal", "fast" to "Fast"),
                            selectedId = scrollName,
                            subtitle = context.getString(R.string.scroll_speed_subtitle),
                        ) { id ->
                            scrollName = id
                            OverlayPrefs.setScrollSpeed(context, id)
                        }
                    }
                    }
                    item { SectionHeader(context.getString(R.string.section_home_redirect), secHomeRedirect) { secHomeRedirect = !secHomeRedirect } }
                    if (secHomeRedirect) {
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
                            ToggleSpec(
                                "black_screen_activity", R.string.advanced_black_screen_as_activity, blackAct,
                                subtitleRes = R.string.black_screen_activity_subtitle,
                                enabled = canChange && homeOn,
                            ) { want ->
                                blackAct = want
                                OverlayPrefs.setBlackActivity(context, want)
                            },
                            ToggleSpec(
                                "read_log_access", R.string.advanced_log_access, logAccess,
                                subtitleRes = R.string.log_access_subtitle,
                                enabled = shizukuReady,
                            ) { want ->
                                logAccess = want
                                scope.launch {
                                    val ok = onIo { Shell.permission(context.packageName, "android.permission.READ_LOGS", want).ok }
                                    if (!ok) context.toast("Couldn't change log access")
                                }
                            },
                        )
                    )
                    indentedItem {
                        DropdownRow(
                            label = context.getString(R.string.advanced_intercept_app),
                            options = homeApps,
                            selectedId = homeIntercept.substringBefore('/'),
                            subtitle = context.getString(R.string.intercept_app_subtitle),
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
                            label = context.getString(R.string.advanced_intercept_activity),
                            options = interceptActs,
                            selectedId = homeIntercept,
                            subtitle = context.getString(R.string.intercept_activity_subtitle),
                            enabled = canChange && homeOn,
                        ) { comp ->
                            homeIntercept = comp
                            change("home redirect") { OverlayPrefs.setHomeRedirect(context, homeOn, comp, homeLaunch) }
                        }
                    }
                    indentedItem {
                        DropdownRow(
                            label = context.getString(R.string.advanced_launch_app),
                            options = launchApps,
                            selectedId = homeLaunch.substringBefore('/'),
                            subtitle = context.getString(R.string.launch_app_subtitle),
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
                            label = context.getString(R.string.advanced_launch_activity),
                            options = launchActs,
                            selectedId = homeLaunch,
                            subtitle = context.getString(R.string.launch_activity_subtitle),
                            enabled = canChange && homeOn,
                        ) { comp ->
                            homeLaunch = comp
                            change("home redirect") { OverlayPrefs.setHomeRedirect(context, homeOn, homeIntercept, comp) }
                        }
                    }

                    indentedItem {
                        DropdownRow(
                            label = context.getString(R.string.advanced_redirect_delay),
                            options = listOf(
                                "500" to "0.5 s", "750" to "0.75 s", "1000" to "1 s",
                                "1250" to "1.25 s", "1500" to "1.5 s", "2000" to "2 s",
                            ),
                            selectedId = homeDelay,
                            subtitle = context.getString(R.string.redirect_delay_subtitle),
                            enabled = canChange && homeOn,
                        ) { id ->
                            homeDelay = id
                            OverlayPrefs.setHomeDelayMs(context, id.toInt())
                        }
                    }
                    }
                    item { SectionHeader(context.getString(R.string.section_install_apk), secInstall) { secInstall = !secInstall } }
                    if (secInstall) {
                        indentedItem {
                            ActionRow(context.getString(R.string.advanced_browse_apk), AppIcons.Card) {
                                apkPicker.launch(arrayOf("application/vnd.android.package-archive"))
                            }
                        }
                    }
                    item {
                        installUri?.let { uri ->
                            ConfirmDialog(
                                title = context.getString(R.string.install_apk_title),
                                text = ApkInstaller.displayName(context, uri),
                                confirmLabel = context.getString(R.string.install_apk_confirm),
                                onConfirm = {
                                    scope.launch {
                                        val ok = onIo { ApkInstaller.installFromUri(context, uri) }
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

fun LazyListScope.indentedToggleItems(settings: List<ToggleSpec>) {
    indentedItems(settings, key = { it.id }) { ToggleRow(it) }
}

private fun readSettingsCommand(context: Context, command: String): ShellResult? {
    val match = Regex("\\s*settings\\s+get\\s+(secure|system|global)\\s+([A-Za-z0-9_.]+)\\s*").matchEntire(command)
        ?: return null
    return try {
        val value = TableSettings.read(context, match.groupValues[1], match.groupValues[2])
        ShellResult(0, value ?: "null")
    } catch (_: SecurityException) {
        null
    }
}

private val TIMEOUT_PRESETS = listOf(
    "30000" to "30 seconds", "60000" to "1 minute", "120000" to "2 minutes", "300000" to "5 minutes",
    "600000" to "10 minutes", "900000" to "15 minutes", "1800000" to "30 minutes", "3600000" to "1 hour",
)

