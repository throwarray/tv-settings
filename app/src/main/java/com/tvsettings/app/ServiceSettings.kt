package com.tvsettings.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings

data class ServiceEntry(
    val component: String,
    val label: String,
    val detail: String,
    val enabled: Boolean,
)

/** Per-component toggles. The secure lists are merged, never replaced. */
object ServiceSettings {
    private const val ACCESSIBILITY = "enabled_accessibility_services"
    private const val LISTENERS = "enabled_notification_listeners"
    private const val ALWAYS_ON_VPN = "always_on_vpn_app"
    private const val ALWAYS_ON_VPN_LOCKDOWN = "always_on_vpn_lockdown"

    val OBSERVED_KEYS = listOf(
        ACCESSIBILITY, LISTENERS, ALWAYS_ON_VPN, ALWAYS_ON_VPN_LOCKDOWN, "default_input_method", "enabled_input_methods", "screensaver_components", "location_mode", "advertising_id",
        "advertisingIdApp:advertisingId:value", "advertisingIdApp:advertisingId:syncPending",
    )

    private fun parse(raw: String?): List<String> =
        raw.orEmpty().split(':').map { it.trim() }.filter { it.isNotEmpty() }

    private fun String.toComponent(): ComponentName? = ComponentName.unflattenFromString(this)

    private fun canWriteSecure(context: Context): Boolean =
        context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED

    // Direct write when this app holds WRITE_SECURE_SETTINGS, otherwise through Shizuku. null deletes.
    fun putSecure(context: Context, key: String, value: String?): Boolean {
        if (canWriteSecure(context)) {
            try {
                if (Settings.Secure.putString(context.contentResolver, key, value)) return true
            } catch (_: SecurityException) {
            }
        }
        return (if (value == null) Shell.settingsDelete("secure", key) else Shell.settingsPut("secure", key, value)).ok
    }

    fun accessibilityServices(context: Context, pkg: String): List<ServiceEntry> = discover(
        context, pkg,
        action = "android.accessibilityservice.AccessibilityService",
        requiredPermission = "android.permission.BIND_ACCESSIBILITY_SERVICE",
        settingKey = ACCESSIBILITY,
    )

    fun notificationListeners(context: Context, pkg: String): List<ServiceEntry> = discover(
        context, pkg,
        action = "android.service.notification.NotificationListenerService",
        requiredPermission = "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
        settingKey = LISTENERS,
    )

    private fun discover(
        context: Context,
        pkg: String,
        action: String,
        requiredPermission: String,
        settingKey: String,
    ): List<ServiceEntry> {
        val pm = context.packageManager
        val enabled = parse(Settings.Secure.getString(context.contentResolver, settingKey)).mapNotNull { it.toComponent() }
        return pm.queryIntentServices(Intent(action).setPackage(pkg), PackageManager.MATCH_DISABLED_COMPONENTS)
            .map { it.serviceInfo }
            .filter { it.permission == requiredPermission }
            .map { si ->
                val cn = ComponentName(si.packageName, si.name)
                ServiceEntry(
                    component = cn.flattenToString(),
                    label = si.loadLabel(pm).toString().ifBlank { si.name.substringAfterLast('.') },
                    detail = cn.shortClassName,
                    enabled = cn in enabled,
                )
            }
            .distinctBy { it.component }
            .sortedBy { it.label.lowercase() }
    }

    /** Matches by component (short and long forms are equal); writes the short form, keeps other entries verbatim. */
    private fun editList(context: Context, key: String, target: ComponentName, enabled: Boolean): Boolean {
        val current = parse(Settings.Secure.getString(context.contentResolver, key))
        val present = current.any { it.toComponent() == target }
        if (enabled == present) return true

        val others = current.filter { it.toComponent() != target }
        val updated = if (enabled) others + target.flattenToShortString() else others
        return putSecure(context, key, if (updated.isEmpty()) null else updated.joinToString(":"))
    }

    // Drops entries whose service no longer exists (renamed or uninstalled apps).
    private fun pruneAccessibility(context: Context) {
        val pm = context.packageManager
        val current = parse(Settings.Secure.getString(context.contentResolver, ACCESSIBILITY))
        val live = current.filter { entry ->
            val cn = entry.toComponent() ?: return@filter false
            try {
                pm.getServiceInfo(cn, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
        if (live.size != current.size) {
            putSecure(context, ACCESSIBILITY, if (live.isEmpty()) null else live.joinToString(":"))
        }
    }

    fun setAccessibilityEnabled(context: Context, component: String, enabled: Boolean): Boolean {
        val target = component.toComponent() ?: return false
        if (enabled) pruneAccessibility(context)
        if (!editList(context, ACCESSIBILITY, target, enabled)) return false
        // any change turns the master flag on; turning it off is its own toggle in Advanced
        return putSecure(context, "accessibility_enabled", "1")
    }

    // Reads both the list and the master flag and writes only what's out of step. Blocking.
    fun ensureAccessibility(context: Context, component: String): Boolean {
        val target = component.toComponent() ?: return false
        val listed = parse(Settings.Secure.getString(context.contentResolver, ACCESSIBILITY)).any { it.toComponent() == target }
        val flagOn = Settings.Secure.getInt(context.contentResolver, "accessibility_enabled", 0) == 1
        if (listed && flagOn) return true
        return setAccessibilityEnabled(context, component, true)
    }

    // Always writes the list (with our entry) and the master flag, whether or not they already look right.
    fun writeAccessibility(context: Context, component: String): Boolean {
        val target = component.toComponent() ?: return false
        val current = parse(Settings.Secure.getString(context.contentResolver, ACCESSIBILITY))
        val updated = current.filter { it.toComponent() != target } + target.flattenToShortString()
        return putSecure(context, ACCESSIBILITY, updated.joinToString(":")) &&
            putSecure(context, "accessibility_enabled", "1")
    }

    /** Rewrites [target]'s entry to the short form and drops duplicates. */
    private fun normalizeToShortForm(context: Context, key: String, target: ComponentName) {
        val current = parse(Settings.Secure.getString(context.contentResolver, key))
        val updated = ArrayList<String>()
        var seen = false
        for (entry in current) {
            if (entry.toComponent() == target) {
                if (!seen) updated.add(target.flattenToShortString())
                seen = true
            } else {
                updated.add(entry)
            }
        }
        if (updated != current) {
            putSecure(context, key, updated.joinToString(":"))
        }
    }

    /** Both paths: cmd notification (updates the manager's own list, binds/unbinds) and the secure list. */
    fun setNotificationListenerEnabled(context: Context, component: String, enabled: Boolean): Boolean {
        val target = component.toComponent() ?: return false
        val viaCmd = Shell.notificationListener(target, enabled).ok
        val viaSetting = editList(context, LISTENERS, target, enabled)
        if (viaSetting && enabled) normalizeToShortForm(context, LISTENERS, target)
        return viaCmd || viaSetting
    }

    // ---- Keyboards ----

    private const val DEFAULT_IME = "default_input_method"
    private const val ENABLED_IMES = "enabled_input_methods"
    private const val PREFS = "tvsettings"
    private const val PREV_IME = "prev_ime"

    fun inputMethods(context: Context, pkg: String): List<ServiceEntry> {
        val pm = context.packageManager
        val active = Settings.Secure.getString(context.contentResolver, DEFAULT_IME)?.toComponent()
        return pm.queryIntentServices(Intent("android.view.InputMethod").setPackage(pkg), PackageManager.MATCH_DISABLED_COMPONENTS)
            .map { it.serviceInfo }
            .filter { it.permission == "android.permission.BIND_INPUT_METHOD" }
            .map { si ->
                val cn = ComponentName(si.packageName, si.name)
                ServiceEntry(
                    component = cn.flattenToString(),
                    label = si.loadLabel(pm).toString().ifBlank { si.name.substringAfterLast('.') },
                    detail = cn.shortClassName,
                    enabled = cn == active,
                )
            }
            .distinctBy { it.component }
            .sortedBy { it.label.lowercase() }
    }

    private fun enableIme(context: Context, target: ComponentName): Boolean {
        val id = target.flattenToShortString()
        if (ShizukuManager.isReady) return Shell.ime("enable", id).ok
        val entries = parse(Settings.Secure.getString(context.contentResolver, ENABLED_IMES))
        if (entries.any { it.substringBefore(';').toComponent() == target }) return true
        return putSecure(context, ENABLED_IMES, (entries + id).joinToString(":"))
    }

    private fun setDefaultIme(context: Context, target: ComponentName): Boolean {
        val id = target.flattenToShortString()
        if (ShizukuManager.isReady) return Shell.ime("set", id).ok
        return putSecure(context, DEFAULT_IME, id)
    }

    /** Activating remembers the previous keyboard; deactivating restores it, else another enabled one. Returns an error message or null. */
    fun setInputMethodActive(context: Context, component: String, active: Boolean): String? {
        val target = component.toComponent() ?: return "Invalid keyboard"
        val resolver = context.contentResolver
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        if (active) {
            val currentRaw = Settings.Secure.getString(resolver, DEFAULT_IME)
            if (currentRaw != null && currentRaw.toComponent() != target) {
                prefs.edit().putString(PREV_IME, currentRaw).apply()
            }
            if (!enableIme(context, target)) return "Couldn't enable that keyboard"
            if (!setDefaultIme(context, target)) return "Couldn't switch to that keyboard"
        } else {
            // enabled_input_methods looks like "ime1;subtypeHash;...:ime2;subtypeHash".
            val others = parse(Settings.Secure.getString(resolver, ENABLED_IMES))
                .mapNotNull { it.substringBefore(';').toComponent() }
                .filter { it != target }
            val previous = prefs.getString(PREV_IME, null)?.toComponent()
            val fallback = others.firstOrNull { it == previous } ?: others.firstOrNull()
                ?: return "No other enabled keyboard to switch back to"
            if (!setDefaultIme(context, fallback)) return "Couldn't switch keyboard"
            prefs.edit().remove(PREV_IME).apply()
        }

        for (attempt in 0 until 5) {
            val now = Settings.Secure.getString(resolver, DEFAULT_IME)?.toComponent()
            if ((now == target) == active) return null
            Thread.sleep(200)
        }
        return "Keyboard change didn't take effect"
    }

    // ---- Always-on VPN ----

    fun declaresVpnService(context: Context, pkg: String): Boolean =
        context.packageManager
            .queryIntentServices(Intent("android.net.VpnService").setPackage(pkg), PackageManager.MATCH_DISABLED_COMPONENTS)
            .any { it.serviceInfo.permission == "android.permission.BIND_VPN_SERVICE" }

    fun alwaysOnVpnApp(context: Context): String? {
        Settings.Secure.getString(context.contentResolver, ALWAYS_ON_VPN)?.takeIf { it.isNotBlank() }?.let { return it }
        // some keys aren't readable by apps on newer Android
        return Shell.settingsGet("secure", ALWAYS_ON_VPN)
    }

    /** One app holds always-on, so enabling replaces any other. Applied after a restart. */
    fun setAlwaysOnVpn(context: Context, pkg: String, enabled: Boolean): Boolean {
        if (enabled) {
            // best effort: pre-authorise so there's no consent prompt
            Shell.appOpsSet(pkg, "ACTIVATE_VPN", "allow")
            return putSecure(context, ALWAYS_ON_VPN, pkg)
        }
        if (alwaysOnVpnApp(context) != pkg) return true
        return putSecure(context, ALWAYS_ON_VPN, null)
    }

    fun alwaysOnVpnLockdown(context: Context): Boolean {
        Settings.Secure.getString(context.contentResolver, ALWAYS_ON_VPN_LOCKDOWN)?.let { return it == "1" }
        return Shell.settingsGet("secure", ALWAYS_ON_VPN_LOCKDOWN) == "1"
    }

    fun setAlwaysOnVpnLockdown(context: Context, enabled: Boolean): Boolean =
        putSecure(context, ALWAYS_ON_VPN_LOCKDOWN, if (enabled) "1" else "0")

    // ---- Screensavers (dreams) ----

    private const val SCREENSAVERS = "screensaver_components"
    private const val PREV_SCREENSAVERS = "prev_screensavers"
    const val SCREEN_OFF_TIMEOUT = "screen_off_timeout"

    private fun activeDreams(context: Context): List<ComponentName> =
        Settings.Secure.getString(context.contentResolver, SCREENSAVERS).orEmpty()
            .split(',').mapNotNull { it.trim().toComponent() }

    fun dreamServices(context: Context, pkg: String): List<ServiceEntry> {
        val pm = context.packageManager
        val active = activeDreams(context)
        return pm.queryIntentServices(Intent("android.service.dreams.DreamService").setPackage(pkg), PackageManager.MATCH_DISABLED_COMPONENTS)
            .map { it.serviceInfo }
            .filter { it.permission == "android.permission.BIND_DREAM_SERVICE" }
            .map { si ->
                val cn = ComponentName(si.packageName, si.name)
                ServiceEntry(
                    component = cn.flattenToString(),
                    label = si.loadLabel(pm).toString().ifBlank { si.name.substringAfterLast('.') },
                    detail = cn.shortClassName,
                    enabled = cn in active,
                )
            }
            .distinctBy { it.component }
            .sortedBy { it.label.lowercase() }
    }

    /** Remembers the previous list; deactivating restores it, or clears it for the system default. */
    fun setDreamActive(context: Context, component: String, active: Boolean): Boolean {
        val target = component.toComponent() ?: return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (active) {
            val currentRaw = Settings.Secure.getString(context.contentResolver, SCREENSAVERS)
            if (!currentRaw.isNullOrBlank() && activeDreams(context).none { it == target }) {
                prefs.edit().putString(PREV_SCREENSAVERS, currentRaw).apply()
            }
            val ok = putSecure(context, SCREENSAVERS, target.flattenToShortString())
            if (ok) putSecure(context, "screensaver_enabled", "1")
            return ok
        }
        val previous = prefs.getString(PREV_SCREENSAVERS, null)
        val ok = if (previous != null) {
            putSecure(context, SCREENSAVERS, previous)
        } else {
            putSecure(context, SCREENSAVERS, null)
        }
        if (ok) prefs.edit().remove(PREV_SCREENSAVERS).apply()
        return ok
    }

    fun screenOffTimeoutMs(context: Context): Long =
        Settings.System.getLong(context.contentResolver, SCREEN_OFF_TIMEOUT, 0L)

    fun setScreenOffTimeout(ms: Long): Boolean =
        ms > 0 && Shell.settingsPut("system", SCREEN_OFF_TIMEOUT, ms.toString()).ok
}
