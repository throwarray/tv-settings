package com.tvsettings.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppActionController {
    const val PREFS = "tvsettings"
    const val KEY_HOME_PIN = "home_pin"


    private fun homeIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    fun launchApp(context: Context, packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            try {
                context.startActivity(intent)
            } catch (_: Throwable) {
                context.toast("Couldn't launch $packageName")
            }
        } else {
            context.toast("No launch intent for $packageName")
        }
    }

    fun openAppInfo(context: Context, packageName: String) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        } catch (_: Throwable) {
            context.toast("Couldn't open app info")
        }
    }

    fun dreamSettingsAvailable(context: Context): Boolean =
        Intent(Settings.ACTION_DREAM_SETTINGS).resolveActivity(context.packageManager) != null

    fun openDreamSettings(context: Context) {
        try {
            context.startActivity(Intent(Settings.ACTION_DREAM_SETTINGS))
        } catch (_: Throwable) {
            context.toast("Couldn't open screensaver settings")
        }
    }

    // The Fire TV launcher's settings first, then the system screen.
    fun openSystemSettings(context: Context) {
        try {
            context.startActivity(
                Intent().setComponent(ComponentName("com.amazon.tv.launcher", "com.amazon.tv.launcher.ui.MainSettingsActivity"))
            )
            return
        } catch (_: Throwable) {
        }
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (_: Throwable) {
            context.toast("Couldn't open system settings")
        }
    }

    suspend fun forceStopApp(context: Context, packageName: String): Boolean {
        val r = onIo { Shell.forceStop(packageName) }
        context.toast(if (r.ok) "Force stopped $packageName" else "Force stop failed (is Shizuku connected?)")
        return r.ok
    }

    suspend fun setAppEnabled(context: Context, packageName: String, enabled: Boolean): Boolean {
        val r = onIo { Shell.setEnabled(packageName, enabled) }
        if (!r.ok) {
            context.toast("Couldn't ${if (enabled) "enable" else "disable"} $packageName")
            return !enabled
        }
        return enabled
    }

    // ---- Default launcher (pm set-home-activity) ----

    fun currentHomePackage(pm: PackageManager): String? =
        pm.resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
            ?.takeIf { it != "android" }

    /** Via the shell when Shizuku is up (no stale client caches). */
    fun currentHomeHolder(pm: PackageManager): String? {
        if (ShizukuManager.isReady) {
            // 65536 = MATCH_DEFAULT_ONLY
            val r = Shell.resolveHome()
            val component = if (r.ok) r.lines.lastOrNull { it.contains('/') } else null
            if (component != null) return component.substringBefore('/').takeIf { it != "android" }
        }
        return currentHomePackage(pm)
    }

    private fun homeComponentOf(pm: PackageManager, packageName: String): ComponentName? =
        pm.queryIntentActivities(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo }
            .firstOrNull { it.packageName == packageName }
            ?.let { ComponentName(it.packageName, it.name) }

    // (package, label) for every app that handles HOME
    fun homeApps(pm: PackageManager): List<Pair<String, String>> =
        pm.queryIntentActivities(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }
            .distinct()
            .map { pkg ->
                pkg to try {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (_: Exception) {
                    pkg
                }
            }
            .sortedBy { it.second.lowercase() }

    // (flattened component, short class name) for each HOME activity of a package
    fun homeActivitiesOf(pm: PackageManager, pkg: String): List<Pair<String, String>> =
        pm.queryIntentActivities(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo }
            .filter { it.packageName == pkg }
            .map { ComponentName(it.packageName, it.name).flattenToString() to it.name.substringAfterLast('.') }

    fun systemHomeComponent(pm: PackageManager, exclude: String): ComponentName? =
        pm.queryIntentActivities(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo }
            .firstOrNull {
                it.packageName != exclude &&
                    (it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            }
            ?.let { ComponentName(it.packageName, it.name) }

    /** pinnedPackage: this app, or the stock launcher after a restore. */
    data class HomeChange(val success: Boolean, val pinnedPackage: String?)

    /** Trusts pm set-home-activity's exit status; doesn't re-check what HOME resolves to. */
    suspend fun setDefaultLauncher(context: Context, packageName: String, enabled: Boolean): HomeChange {
        val pm = context.packageManager
        val target = onIo {
            if (enabled) homeComponentOf(pm, packageName) else systemHomeComponent(pm, packageName)
        }
        if (target == null) {
            context.toast(if (enabled) "$packageName has no launcher activity" else "Couldn't find the stock launcher")
            return HomeChange(false, null)
        }
        val r = onIo { Shell.setHomeActivity(target) }
        if (!r.ok || r.output.contains("Error:")) {
            context.toast("pm set-home-activity failed: ${r.output.trim().take(80)}")
            return HomeChange(false, null)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_HOME_PIN, target.packageName).apply()
        context.toast(
            if (enabled) "$packageName set as default launcher" else "Default launcher restored: ${target.packageName}"
        )
        return HomeChange(true, target.packageName)
    }

    /** Packages with a running process. Needs Shizuku. */
    fun runningPackages(): Set<String> {
        val r = Shell.processNames()
        if (!r.ok) return emptySet()
        // first line is the NAME header; names are "pkg" or "pkg:service"
        return r.lines.drop(1).map { it.substringBefore(':') }.toSet()
    }

    // ---- Doze (battery optimisation) whitelist ----

    /** package -> true if only on the (unremovable) system whitelist; null if unreadable. */
    fun idleWhitelistEntries(): Map<String, Boolean>? {
        val r = Shell.dumpsys("deviceidle", "whitelist")
        if (!r.ok) return null
        val entries = HashMap<String, Boolean>()
        r.lines.forEach { line ->
            // "user,com.pkg,10123" / "system,com.pkg,1000" / "system-excidle,com.pkg,1000"
            val parts = line.split(',')
            if (parts.size < 3) return@forEach
            when (parts[0]) {
                "user" -> entries[parts[1]] = false
                "system", "system-excidle" -> entries.putIfAbsent(parts[1], true)
            }
        }
        return entries
    }

    suspend fun setIdleWhitelist(context: Context, packageName: String, enabled: Boolean): Boolean {
        val now = onIo {
            Shell.idleWhitelist(packageName, enabled)
            idleWhitelistEntries()?.containsKey(packageName)
        }
        if (now == null || now != enabled) {
            context.toast("Couldn't ${if (enabled) "add to" else "remove from"} the idle whitelist")
        }
        return now ?: !enabled
    }

    // ---- Launcher widget binding (appwidget grantbind) ----

    private const val WIDGET_PREFS = "widget_bind"

    /** Parses "Grants:" in dumpsys appwidget; falls back to what this app last set. */
    fun widgetBindGranted(context: Context, packageName: String): Boolean {
        if (ShizukuManager.isReady) {
            val r = Shell.dumpsys("appwidget")
            if (r.ok) {
                val grant = Regex("""user=(\d+)\s+package=([A-Za-z0-9_.]+)""")
                var inGrants = false
                var sawGrants = false
                for (line in r.output.lineSequence()) {
                    if (line.trim() == "Grants:") {
                        inGrants = true
                        sawGrants = true
                        continue
                    }
                    if (!inGrants) continue
                    val m = grant.find(line)
                    if (m == null) {
                        if (line.isBlank()) inGrants = false
                    } else if (m.groupValues[1] == "0" && m.groupValues[2] == packageName) {
                        return true
                    }
                }
                if (sawGrants) return false
            }
        }
        return context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE).getBoolean(packageName, false)
    }

    suspend fun setWidgetBind(context: Context, packageName: String, enabled: Boolean): Boolean {
        val r = onIo { Shell.widgetBind(packageName, enabled) }
        if (!r.ok) {
            context.toast("Couldn't change widget binding (is Shizuku connected?)")
            return !enabled
        }
        context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE).edit().putBoolean(packageName, enabled).apply()
        return enabled
    }
}
