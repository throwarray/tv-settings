package com.tvsettings.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
            // readable without Shizuku: PowerManager says whether the app is exempt from Doze
            val exempt = context.getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(pkg)
            val widget = async(Dispatchers.IO) { if (app.isLauncher) AppActionController.widgetBindGranted(context, pkg) else null }
            val loadedOps = ops.await()
            val loadedIdle = idle.await()
            app.idleWhitelisted = exempt
            if (loadedIdle != null) {
                app.idleSystem = loadedIdle[pkg] == true
            }
            if (shizukuReady) {
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
            val vpnPerm = async(Dispatchers.IO) { ServiceSettings.vpnAllowed(pkg) == true }
            val timeout = async(Dispatchers.IO) { ServiceSettings.screenOffTimeoutMs(context) }
            app.accessibilityServices.clear(); app.accessibilityServices.addAll(a11y.await())
            app.notificationListeners.clear(); app.notificationListeners.addAll(listeners.await())
            app.inputMethods.clear(); app.inputMethods.addAll(imes.await())
            app.dreams.clear(); app.dreams.addAll(dreams.await())
            app.isVpn = vpn.await()
            app.alwaysOnVpn = alwaysOn.await() == pkg
            app.vpnLockdown = lockdown.await()
            app.vpnPermission = vpnPerm.await()
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
        var netBlocked by remember(app.packageName) { mutableStateOf(NetBlock.isBlocked(context, app.packageName)) }
        var usageAllowed by remember { mutableStateOf(false) }
        var usageStats by remember(app.packageName) { mutableStateOf<UsageInfo.Stats?>(null) }
        LaunchedEffect(app.packageName) {
            usageAllowed = onIo { UsageInfo.allowed(context) }
            usageStats = if (usageAllowed) onIo { UsageInfo.load(context, app.packageName) } else null
        }
        var freezeShortcut by remember { mutableStateOf(true) }
        val freezeExtra: @Composable () -> Unit = {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { freezeShortcut = !freezeShortcut }.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = freezeShortcut, onCheckedChange = { freezeShortcut = it })
                Text(context.getString(R.string.freeze_option_label), color = Oled.textPrimary, fontSize = 12.sp)
            }
        }
        var killOnBoost by remember(app.packageName) { mutableStateOf(BoostList.contains(context, app.packageName)) }
        var dozeMode by remember(app.packageName) { mutableStateOf(Doze.mode(context, app.packageName)) }
        var backupPicking by remember(app.packageName) { mutableStateOf(false) }
        val secInfo = paneSections["Info"] ?: false
        val secActions = paneSections["Actions"] ?: true
        val secPerms = paneSections["Perms"] ?: true
        val secSignaturePerms = paneSections["SignaturePerms"] ?: false
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
            !shizukuReady -> context.getString(R.string.needs_shizuku)
            UsageFeed.snapshot == null -> context.getString(R.string.value_loading)
            live == null -> context.getString(R.string.not_running)
            else -> "${formatCpu(live.cpuPercent)}  \u00B7  ${formatKb(live.rssKb)}"
        }
        if (backupPicking) {
            FileBrowser(
                title = context.getString(R.string.backup_apk_to),
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
        confirmPending?.let { what ->
            val pkg = app.packageName
            val (title, text, label) = when (what) {
                "data" -> listOf(
                    context.getString(R.string.clear_all_data_title),
                    context.getString(R.string.clear_data_confirmation, pkg),
                    context.getString(R.string.clear_data_action),
                )
                "disable" -> listOf(
                    context.getString(R.string.disable_app_title),
                    context.getString(R.string.disable_app_confirmation, pkg),
                    context.getString(R.string.disable_app),
                )
                "enable" -> listOf(
                    context.getString(R.string.enable_app_title),
                    context.getString(R.string.enable_app_confirmation, pkg),
                    context.getString(R.string.enable_app),
                )
                else -> listOf(
                    context.getString(R.string.uninstall_app_title),
                    context.getString(R.string.uninstall_app_confirmation, pkg),
                    context.getString(R.string.uninstall_app),
                )
            }
            ConfirmDialog(
                title = title,
                text = text,
                confirmLabel = label,
                extra = if (what == "disable") freezeExtra else null,
                onConfirm = {
                    confirmPending = null
                    scope.launch {
                        if (what == "enable" || what == "disable") {
                            app.isEnabled = onIo { AppActionController.setAppEnabled(context, pkg, what == "enable") }
                            if (what == "disable" && freezeShortcut && !app.isEnabled) FrozenShortcut.add(context, pkg, app.appName)
                            AppEvents.activeTick++
                            usageTick++
                            usageStats = if (usageAllowed) onIo { UsageInfo.load(context, app.packageName) } else null
                            context.toast("Done")
                        } else {
                            val ok = onIo { (if (what == "data") Shell.clearData(pkg) else Shell.uninstallForUser(pkg)).ok }
                            if (ok && what == "uninstall") AppEvents.removed = pkg
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

            item { SectionHeader(context.getString(R.string.section_actions), secActions, dot = if (app.isActive) Oled.okGreen else null, trailing = liveText) { paneSections["Actions"] = !secActions } }
            if (secActions) {
            indentedItem {
                QuickActionRow(buildList {
                    add(QuickAction(context.getString(R.string.launch), Icons.Default.PlayArrow, enabled = hasLaunch && app.isEnabled) { AppActionController.launchApp(context, app.packageName) })
                    add(QuickAction(context.getString(R.string.force_stop), AppIcons.Stop, shizukuReady) {
                        scope.launch {
                            if (AppActionController.forceStopApp(context, app.packageName)) {
                                AppEvents.activeTick++
                                // re-check: the system may restart it
                                delay(300)
                                app.isActive = onIo { app.packageName in AppActionController.runningPackages() }
                            }
                        }
                    })
                    add(QuickAction(context.getString(R.string.app_info), Icons.Default.Info) { AppActionController.openAppInfo(context, app.packageName) })
                    add(QuickAction(context.getString(R.string.open_in_store), Icons.Default.ShoppingCart) {
                        if (!Stores.open(context, app.packageName)) context.toast(context.getString(R.string.store_none))
                    })
                })
            }
            }
            item { SectionHeader(context.getString(R.string.section_details), secInfo) { paneSections["Info"] = !secInfo } }
            if (secInfo) {
            Debloat.infoFor(app.packageName)?.let { info -> indentedItem { DebloatNote(info) } }
            val loadingText = context.getString(R.string.value_loading)
            val infoRows = buildList<Pair<String, String>> {
                add(context.getString(R.string.version) to (details?.version ?: loadingText))
                add(context.getString(R.string.installed_by) to (details?.installer ?: loadingText))
                add(context.getString(R.string.installed) to formatDateTime(app.firstInstallTime))
                add(context.getString(R.string.updated) to formatDateTime(app.lastUpdateTime))
                if (usageAllowed) {
                    add(context.getString(R.string.usage_last_used) to
                        (usageStats?.let { if (it.lastUsed > 0) formatDateTime(it.lastUsed) else context.getString(R.string.usage_never) } ?: ""))
                    add(context.getString(R.string.usage_stats) to
                        (usageStats?.let { context.getString(R.string.usage_today_week, formatUsage(context, it.todayMs), formatUsage(context, it.weekMs)) } ?: ""))
                } else {
                    add(context.getString(R.string.usage_stats) to context.getString(R.string.usage_needs_access))
                }
                details?.let { d ->
                    val appBytes = d.appBytes
                    val dataBytes = d.dataBytes
                    val cacheBytes = d.cacheBytes
                    if (appBytes != null && dataBytes != null && cacheBytes != null) {
                        add(context.getString(R.string.total_size) to formatBytes(context, appBytes + dataBytes + cacheBytes))
                        add(context.getString(R.string.app_and_data) to
                            context.getString(R.string.app_and_data_sizes, formatBytes(context, appBytes), formatBytes(context, dataBytes)))
                        add(context.getString(R.string.cache_size) to formatBytes(context, cacheBytes))
                    }
                }
            }
            // the More item is the fifth row of the last group; clicking any group expands or folds the manifest
            val toggleManifest: () -> Unit = {
                if (manifestText != null) manifestText = null
                else scope.launch { manifestText = onIo { AppManifest.describe(context, app.packageName) } }
            }
            val sizesShown = details?.let { it.appBytes != null && it.dataBytes != null && it.cacheBytes != null } == true
            val moreRow: Pair<String, String> = Pair(context.getString(R.string.more_details), "")
            val infoGroups: List<List<Pair<String, String>>> = (infoRows + moreRow).chunked(5)
            infoGroups.forEach { chunk ->
                indentedItem { StatChunk(chunk, onClick = toggleManifest) }
            }
            if (!sizesShown) details?.let { d ->
                if (d.needsUsageAccess) {
                    indentedItem {
                        ActionRow(context.getString(R.string.allow_usage_access_for_sizes), Icons.Default.Info, enabled = shizukuReady) {
                            // this app's own app-op: set on the system screen (writing it here restarts the app)
                            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                } else {
                    indentedItem { StatRow(context.getString(R.string.size), context.getString(R.string.not_available)) }
                }
            }
            if (app.isSystem && app.isEnabled) indentedItem {
                Text(context.getString(R.string.disable_system_app_warning), color = Oled.textSecondary, fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
            }
            // always shown, not inside a group
            indentedItem {
                QuickActionRow(buildList {
                    add(QuickAction(
                        context.getString(if (app.isEnabled) R.string.disable_app_action else R.string.enable_app_action),
                        if (app.isEnabled) Icons.Default.Lock else Icons.Default.CheckCircle,
                        shizukuReady,
                    ) { confirmPending = if (app.isEnabled) "disable" else "enable" })
                    add(QuickAction(context.getString(R.string.clear_data_privileged_action), AppIcons.Trash, shizukuReady) { confirmPending = "data" })
                    add(QuickAction(context.getString(R.string.uninstall_app_action), AppIcons.Exit, shizukuReady) { confirmPending = "uninstall" })
                    add(QuickAction(context.getString(R.string.backup_apk), AppIcons.Backup, shizukuReady) { backupPicking = true })
                })
            }
            // the expanded manifest, five lines a group so it scrolls inside the viewport
            manifestText?.lines()?.chunked(5)?.forEach { chunk ->
                indentedItem { TextChunk(chunk, onClick = toggleManifest) }
            }
            }
            if (app.isVpn) {
                item { SectionHeader(context.getString(R.string.section_vpn), secVpn) { paneSections["Vpn"] = !secVpn } }
                if (secVpn) {
                indentedToggleItems(
                    listOf(
                        ToggleSpec(
                            "vpn_permission", R.string.vpn_permission, app.vpnPermission,
                            subtitleRes = R.string.vpn_permission_subtitle,
                            enabled = canWrite,
                        ) { want ->
                            scope.launch {
                                val ok = onIo { ServiceSettings.setVpnPermission(app.packageName, want) }
                                if (!ok) context.toast("Couldn't change the VPN permission")
                                app.vpnPermission = onIo { ServiceSettings.vpnAllowed(app.packageName) } == true
                            }
                        },
                        ToggleSpec(
                            "always_on_vpn", R.string.always_on_vpn, app.alwaysOnVpn,
                            subtitleRes = R.string.always_on_vpn_subtitle,
                            enabled = canWrite,
                        ) { want ->
                            scope.launch {
                                val ok = onIo { ServiceSettings.setAlwaysOnVpn(context, app.packageName, want) }
                                if (!ok) context.toast("Couldn't change always-on VPN")
                                app.alwaysOnVpn = onIo { ServiceSettings.alwaysOnVpnApp(context) } == app.packageName
                            }
                        },
                        ToggleSpec(
                            "vpn_lockdown", R.string.block_without_vpn, app.vpnLockdown,
                            subtitleRes = R.string.block_without_vpn_subtitle,
                            enabled = canWrite && app.alwaysOnVpn,
                        ) { want ->
                            scope.launch {
                                val ok = onIo { ServiceSettings.setAlwaysOnVpnLockdown(context, want) }
                                if (!ok) context.toast("Couldn't change lockdown")
                                app.vpnLockdown = onIo { ServiceSettings.alwaysOnVpnLockdown(context) }
                            }
                        },
                    )
                )
                }
            }

            if (app.accessibilityServices.isNotEmpty()) {
                item { SectionHeader(context.getString(R.string.section_accessibility_services), secA11y) { paneSections["A11y"] = !secA11y } }
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
                item { SectionHeader(context.getString(R.string.section_notification_listeners), secNotif) { paneSections["Notif"] = !secNotif } }
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
                item { SectionHeader(context.getString(R.string.section_keyboard), secKbd) { paneSections["Kbd"] = !secKbd } }
                if (secKbd) {
            indentedItems(app.inputMethods, key = { "ime:" + it.component }) { ime ->
                ToggleRow(
                    ime.label,
                    ime.enabled,
                    subtitle = context.getString(R.string.active_keyboard_subtitle, ime.detail),
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
                item { SectionHeader(context.getString(R.string.section_screensaver), secDreams) { paneSections["Dreams"] = !secDreams } }
                if (secDreams) {
                indentedItems(app.dreams, key = { "dream:" + it.component }) { dream ->
                    ToggleRow(
                        dream.label,
                        dream.enabled,
                        subtitle = context.getString(R.string.active_screensaver_subtitle, dream.detail),
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
                    indentedItem { ActionRow(context.getString(R.string.screensaver_settings), Icons.Default.Settings) { AppActionController.openDreamSettings(context) } }
                } else {
                    indentedItem {
                        val base = listOf(
                            60_000L to context.getString(R.string.duration_one_minute),
                            300_000L to context.getString(R.string.duration_five_minutes),
                            600_000L to context.getString(R.string.duration_ten_minutes),
                            900_000L to context.getString(R.string.duration_fifteen_minutes),
                            1_800_000L to context.getString(R.string.duration_thirty_minutes),
                            3_600_000L to context.getString(R.string.duration_one_hour),
                        )
                        val all = if (screenOffMs > 0 && base.none { it.first == screenOffMs }) {
                            (base + (screenOffMs to context.getString(R.string.duration_minutes, screenOffMs / 60_000))).sortedBy { it.first }
                        } else {
                            base
                        }
                        DropdownRow(
                            label = context.getString(R.string.screensaver_start_time),
                            options = all.map { it.first.toString() to it.second },
                            selectedId = screenOffMs.toString(),
                            subtitle = context.getString(R.string.system_wide_subtitle),
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

            item { SectionHeader(context.getString(R.string.section_permissions), secPerms) { paneSections["Perms"] = !secPerms } }
            if (secPerms) {
                indentedItem {
                    ToggleRow(
                        context.getString(R.string.label_block_network),
                        netBlocked,
                        subtitle = context.getString(R.string.subtitle_block_network),
                        enabled = shizukuReady,
                    ) { want ->
                        netBlocked = want
                        scope.launch { if (!onIo { NetBlock.set(context, app.packageName, want) }) netBlocked = !want }
                    }
                }
                indentedItem {
                    ToggleRow(
                        context.getString(R.string.label_boost_kill),
                        killOnBoost,
                        subtitle = context.getString(R.string.subtitle_boost_kill),
                        enabled = true,
                    ) { want ->
                        killOnBoost = want
                        BoostList.set(context, app.packageName, want)
                    }
                }
                indentedItem {
                    DropdownRow(
                        label = context.getString(R.string.label_doze),
                        options = listOf(
                            Doze.NORMAL to context.getString(R.string.doze_normal),
                            Doze.EXIT to context.getString(R.string.doze_exit),
                    Doze.RESTRICT to context.getString(R.string.doze_restrict),
                            Doze.IDLE to context.getString(R.string.doze_idle),
                        ),
                        // what the system says wins over what was saved
                        selectedId = if (app.idleWhitelisted) Doze.IDLE else if (dozeMode == Doze.IDLE) Doze.NORMAL else dozeMode,
                        enabled = shizukuReady && !app.idleSystem,
                    ) { id ->
                        dozeMode = id
                        scope.launch {
                            onIo { Doze.apply(context, app.packageName, id) }
                            app.idleWhitelisted = context.getSystemService(android.os.PowerManager::class.java).isIgnoringBatteryOptimizations(app.packageName)
                        }
                    }
                }
                indentedToggleItems(buildList {
                    add(ToggleSpec(
                        "notifications",
                        R.string.app_notifications,
                        app.notificationsAllowed,
                        subtitleRes = R.string.app_notifications_subtitle,
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
                    })
                    if (Build.VERSION.SDK_INT >= 30) add(ToggleSpec(
                        "auto_revoke_permissions",
                        R.string.auto_revoke_permissions,
                        app.autoRevokePerms,
                        subtitleRes = R.string.auto_revoke_permissions_subtitle,
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
                    })
                    if (app.isLauncher) {
                        add(ToggleSpec("default_launcher", R.string.default_launcher, app.defaultLauncher, enabled = shizukuReady) { want ->
                            scope.launch {
                                val change = AppActionController.setDefaultLauncher(context, app.packageName, want)
                                if (change.success) {
                                    onHomeHolderChanged(change.pinnedPackage)
                                    app.defaultLauncher = change.pinnedPackage == app.packageName
                                }
                            }
                        })
                        add(ToggleSpec(
                            "app_bind_widgets",
                            R.string.app_bind_widgets,
                            app.appBindWidgets,
                            subtitleRes = R.string.app_bind_widgets_subtitle,
                            enabled = shizukuReady,
                        ) { want ->
                            scope.launch { app.appBindWidgets = AppActionController.setWidgetBind(context, app.packageName, want) }
                        })
                    }
                })

            indentedItems(app.permissions.filter { it.kind == "runtime" || it.kind == "appop" || it.kind == "development" }, key = { "perm:" + it.name }) { perm ->
                if (perm.kind == "appwidget") {
                    ToggleRow(perm.label, perm.granted, subtitle = perm.description, enabled = shizukuReady) { want ->
                        scope.launch { onIo { Shell.widgetBind(app.packageName, want) } }
                    }
                } else if (perm.kind == "appop") {
                    AppOpPermRow(app, perm, scope, shizukuReady)
                } else ToggleRow(
                    perm.label,
                    perm.granted,
                    subtitle = PermissionNotes.subtitle(context, perm.name, perm.description),
                    enabled = shizukuReady && (perm.kind == "runtime" || perm.kind == "development"),
                    focusableWhenDisabled = true,
                ) { want ->
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
                val secNormalPerms = paneSections["NormalPerms"] ?: false
                val normalPermissions = app.permissions.filter { it.kind == "fixed" }
                if (normalPermissions.isNotEmpty()) {
                    indentedItem {
                        SectionHeader(context.getString(R.string.section_install_permissions), secNormalPerms) {
                            paneSections["NormalPerms"] = !secNormalPerms
                        }
                    }
                    if (secNormalPerms) indentedItems(normalPermissions, key = { "normal:" + it.name }) { perm ->
                        Box(modifier = Modifier.padding(start = 12.dp)) { FixedPermRow(perm) }
                    }
                }
            val signaturePermissions = app.permissions.filter { it.kind == "signature" || it.kind == "appwidget" }
            if (signaturePermissions.isNotEmpty()) {
                indentedItem {
                    SectionHeader(context.getString(R.string.section_signature_permissions), secSignaturePerms) {
                        paneSections["SignaturePerms"] = !secSignaturePerms
                    }
                }
                if (secSignaturePerms) indentedItems(signaturePermissions, key = { "signature:" + it.name }) { perm ->
                    Box(modifier = Modifier.padding(start = 12.dp)) {
                        if (perm.kind == "appwidget") {
                            // not grantable like the others: the launcher widget helper sets it
                            var bound by remember(app.packageName, perm.name) { mutableStateOf(perm.granted) }
                            ToggleRow(perm.label, bound, subtitle = perm.description, enabled = shizukuReady) { want ->
                                bound = want
                                scope.launch { if (!onIo { Shell.widgetBind(app.packageName, want).ok }) bound = !want }
                            }
                        } else {
                            // signature permissions are kept here, but still settable: which ones take effect isn't known up front
                            var granted by remember(app.packageName, perm.name) { mutableStateOf(perm.granted) }
                            ToggleRow(
                                perm.label, granted,
                                subtitle = PermissionNotes.subtitle(context, perm.name, perm.description),
                                enabled = shizukuReady && !PermissionNotes.fixed(perm.name),
                            ) { want ->
                                granted = want
                                scope.launch { if (!onIo { AppInspector.setPermission(app.packageName, perm.name, want) }) granted = !want }
                            }
                        }
                    }
                }
            }

            }

            // auto revoke and notifications have their own toggles
            val shownOps = app.appOps.filter { it.op != AppInspector.AUTO_REVOKE_OP && it.op != AppInspector.NOTIFICATIONS_OP }
            if (shownOps.isNotEmpty()) {
                item { SectionHeader(context.getString(R.string.section_appops), secOps) { paneSections["Ops"] = !secOps } }
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
                item { SectionHeader(context.getString(R.string.section_activities), secActs) { paneSections["Acts"] = !secActs } }
                if (secActs) {
            indentedToggleItems(listOf(ToggleSpec(
                "show_unexported_activities",
                R.string.show_unexported_activities,
                showUnexported,
                subtitleRes = R.string.show_unexported_activities_subtitle,
            ) { want ->
                showUnexported = want
                ActivityPrefs.setShowUnexported(context, want)
            }))
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

// "2h 05m" for a duration in milliseconds
private fun formatUsage(context: Context, ms: Long): String =
    context.getString(R.string.usage_duration, ms / 3_600_000L, (ms / 60_000L) % 60)
