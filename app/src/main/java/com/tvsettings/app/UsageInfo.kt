package com.tvsettings.app

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

/** Usage of one app, from the usage stats service. Needs usage access granted to this app. */
object UsageInfo {
    private const val DAY = 86_400_000L

    class Stats(val lastUsed: Long, val todayMs: Long, val weekMs: Long)

    fun allowed(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }

    fun load(context: Context, pkg: String): Stats? {
        if (!allowed(context)) return null
        val usage = context.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val today = usage.queryAndAggregateUsageStats(now - DAY, now)[pkg]
        val week = usage.queryAndAggregateUsageStats(now - 7 * DAY, now)[pkg]
        return Stats(week?.lastTimeUsed ?: 0L, today?.totalTimeInForeground ?: 0L, week?.totalTimeInForeground ?: 0L)
    }
}
