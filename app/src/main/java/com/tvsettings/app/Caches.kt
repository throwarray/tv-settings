package com.tvsettings.app

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager

/**
 * App cache sizes and clearing them. `dumpsys diskstats` only prints a snapshot the system takes about once a day,
 * so its numbers never change after a clear: the live figures come from the storage stats service instead.
 */
object Caches {
    private const val PREFS = "caches"
    private const val KEEP_OVER = 512 * 1024L   // a package left with more than this after the trim gets its own clear
    private const val MAX_PACKAGES = 40

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun clearOnBoost(context: Context): Boolean = prefs(context).getBoolean("on_boost", false)

    fun setClearOnBoost(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("on_boost", on).apply()
    }

    class Sizes(val total: Long, val byPackage: Map<String, Long>)

    class Result(val beforeMb: Long, val afterMb: Long, val ok: Boolean)

    // The storage stats service needs usage access; Shizuku can give it to this app.
    private fun usageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        fun allowed() = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
        if (allowed()) return true
        if (!ShizukuManager.isReady) return false
        Shell.appOpsSet(context.packageName, "GET_USAGE_STATS", "allow")
        return allowed()
    }

    /** Live cache bytes per package, or null when the service can't be asked (no usage access, or Android before 8). */
    fun measure(context: Context): Sizes? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !usageAccess(context)) return null
        val stats = context.getSystemService(StorageStatsManager::class.java)
        val user = Process.myUserHandle()
        val sizes = HashMap<String, Long>()
        for (info in context.packageManager.getInstalledApplications(0)) {
            val bytes = runCatching {
                stats.queryStatsForPackage(StorageManager.UUID_DEFAULT, info.packageName, user).cacheBytes
            }.getOrNull() ?: continue
            if (bytes > 0) sizes[info.packageName] = bytes
        }
        return Sizes(sizes.values.sum(), sizes)
    }

    /** Total app cache in MB: live when it can be, otherwise the system's daily snapshot. */
    fun totalMb(context: Context): Long = measure(context)?.let { it.total / (1024 * 1024) } ?: snapshotMb()

    // dumpsys diskstats: a stale snapshot, used only when the live figure isn't available
    private fun snapshotMb(): Long =
        Regex("""Cache Size: (\d+)""").findAll(Shell.dumpsys("diskstats").output).sumOf { it.groupValues[1].toLong() } / (1024 * 1024)

    /**
     * pm trim-caches frees what the system will give up. Whatever is still big afterwards gets its own cache-only clear and
     * its external cache emptied (a package's cache folder on shared storage is not always covered by the trim).
     */
    fun clear(context: Context): Result {
        val before = measure(context)
        val trimmed = Shell.trimCaches().ok
        var after = measure(context)
        if (after != null) {
            val left = after.byPackage.filter { it.value > KEEP_OVER }.entries.sortedByDescending { it.value }.take(MAX_PACKAGES).map { it.key }
            if (left.isNotEmpty()) {
                Shell.batch(left.flatMap { listOf(Shell.Cmd.clearCache(it), Shell.Cmd.clearExternalCache(it)) })
                after = measure(context)
            }
        }
        val mb = 1024 * 1024L
        return if (before != null && after != null) Result(before.total / mb, after.total / mb, trimmed)
        else Result(snapshotMb(), snapshotMb(), trimmed)
    }
}
