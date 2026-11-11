package com.tvsettings.app

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class PermissionEntry(
    val name: String,
    val label: String,
    val granted: Boolean,
    val description: String? = null,
)

data class AppOpEntry(
    val op: String,
    val label: String,
    val mode: String,
    val uidMode: Boolean = false,
)

enum class AppFilter(val label: String) {
    ALL("All"),
    USER("Installed"),
    SYSTEM("System"),
}

/** Each key sorts ascending; the UI reverses it. */
enum class AppSort(val label: String, val comparator: Comparator<AppRecord>) {
    NAME("Name", compareBy<AppRecord> { it.appName.lowercase() }),
    PACKAGE("Package", compareBy<AppRecord> { it.packageName }),
    INSTALLED("Install date", compareBy<AppRecord> { it.firstInstallTime }),
    UPDATED("Updated", compareBy<AppRecord> { it.lastUpdateTime }),
    // ascending = running first
    ACTIVE("Active *", compareByDescending<AppRecord> { it.isActive && it.isEnabled }.thenBy { it.appName.lowercase() }),
    CPU("CPU", compareBy<AppRecord> { 0f }),
    RAM("RAM", compareBy<AppRecord> { 0L }),
    // ascending = frozen (disabled) first
    FROZEN("Frozen", compareByDescending<AppRecord> { !it.isEnabled }.thenBy { it.appName.lowercase() }),
}

class AppRecord(
    val packageName: String,
    val appName: String,
    val isLauncher: Boolean = false,
    val isSystem: Boolean = false,
    val firstInstallTime: Long = 0L,
    val lastUpdateTime: Long = 0L,
    enabled: Boolean = true,
    isDefaultLauncher: Boolean = false,
) {
    var isEnabled by mutableStateOf(enabled)
    var isActive by mutableStateOf(false) // only known with Shizuku
    var idleWhitelisted by mutableStateOf(false)
    var idleSystem by mutableStateOf(false)
    var autoRevokePerms by mutableStateOf(true)
    var notificationsAllowed by mutableStateOf(true)
    var isVpn by mutableStateOf(false)
    var alwaysOnVpn by mutableStateOf(false)
    var vpnLockdown by mutableStateOf(false)
    var defaultLauncher by mutableStateOf(isDefaultLauncher)
    var appBindWidgets by mutableStateOf(false)
    val accessibilityServices = mutableStateListOf<ServiceEntry>()
    val inputMethods = mutableStateListOf<ServiceEntry>()
    val dreams = mutableStateListOf<ServiceEntry>()
    val notificationListeners = mutableStateListOf<ServiceEntry>()
    val permissions = mutableStateListOf<PermissionEntry>()
    val appOps = mutableStateListOf<AppOpEntry>()
}

class AppRegistry(private val context: Context) {
    private val packageManager = context.packageManager

    fun loadInstalledApps(): List<AppRecord> {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launcherPackages = packageManager
            .queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }
            .toSet()
        // a launcher pinned by this app wins over what HOME resolves to
        val pinned = context.getSharedPreferences(AppActionController.PREFS, Context.MODE_PRIVATE)
            .getString(AppActionController.KEY_HOME_PIN, null)
        val defaultHome = pinned ?: AppActionController.currentHomePackage(packageManager)

        // MATCH_DISABLED_*: list disabled apps too
        val flags = PackageManager.MATCH_DISABLED_COMPONENTS or
            PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS

        return packageManager.getInstalledPackages(flags)
            .mapNotNull { pkgInfo ->
                val pkg = pkgInfo.packageName
                val appInfo = pkgInfo.applicationInfo ?: return@mapNotNull null
                AppRecord(
                    packageName = pkg,
                    appName = appInfo.loadLabel(packageManager).toString(),
                    isLauncher = pkg in launcherPackages,
                    isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    firstInstallTime = pkgInfo.firstInstallTime,
                    lastUpdateTime = pkgInfo.lastUpdateTime,
                    enabled = appInfo.enabled,
                    isDefaultLauncher = pkg == defaultHome,
                )
            }
            .sortedBy { it.appName.lowercase() }
    }
}
