package com.tvsettings.app

import android.content.pm.ApplicationInfo
import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import android.text.format.Formatter
import java.text.DateFormat
import java.util.Date

data class AppDetails(
    val version: String,
    val installer: String,
    val appBytes: Long?,
    val dataBytes: Long?,
    val cacheBytes: Long?,
    val needsUsageAccess: Boolean,
)

fun formatDateTime(time: Long): String =
    if (time <= 0) "-" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))

fun formatBytes(context: Context, bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

object AppDetailsLoader {
    // Sizes come from StorageStatsManager, which needs usage access; Shizuku can grant it.
    private fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    @Suppress("DEPRECATION")
    fun load(context: Context, pkg: String): AppDetails {
        val pm = context.packageManager
        val version = try {
            val info = pm.getPackageInfo(pkg, 0)
            val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
            "${info.versionName ?: "0"} ($code)"
        } catch (_: Exception) {
            "0"
        }
        val installerPkg = try {
            if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(pkg).installingPackageName
            else pm.getInstallerPackageName(pkg)
        } catch (_: Exception) {
            null
        }
        val system = try {
            (pm.getApplicationInfo(pkg, 0).flags and ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (_: Exception) {
            false
        }
        val installer = if (installerPkg == null) {
            if (system) "System image" else "Unknown (adb or sideload)"
        } else {
            try {
                "${pm.getApplicationLabel(pm.getApplicationInfo(installerPkg, 0))} ($installerPkg)"
            } catch (_: Exception) {
                installerPkg
            }
        }
        var app: Long? = null
        var data: Long? = null
        var cache: Long? = null
        var needsAccess = false
        if (Build.VERSION.SDK_INT >= 26) {
            if (hasUsageAccess(context)) {
                try {
                    val stats = context.getSystemService(StorageStatsManager::class.java)
                        .queryStatsForPackage(StorageManager.UUID_DEFAULT, pkg, Process.myUserHandle())
                    app = stats.appBytes
                    cache = stats.cacheBytes
                    data = (stats.dataBytes - stats.cacheBytes).coerceAtLeast(0)
                } catch (_: Exception) {
                }
            } else {
                needsAccess = true
            }
        }
        return AppDetails(version, installer, app, data, cache, needsAccess)
    }
}
