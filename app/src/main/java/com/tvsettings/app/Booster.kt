package com.tvsettings.app

import android.content.Context
import android.os.Handler
import android.os.Looper

/** Boost: trims memory in the apps behind, stops the listed apps, optionally clears caches. This app, if listed, goes last. */
object Booster {
    fun run(context: Context, onDone: (String) -> Unit) {
        Thread {
            val self = context.packageName
            val keep = setOf(
                self,
                OverlayPrefs.homeLaunchTarget(context).substringBefore('/'),
                OverlayPrefs.homeRedirectTarget(context).substringBefore('/'),
            )
            val targets = UsageFeed.snapshot?.perPackage?.keys.orEmpty().filter { it !in keep }
            val listed = BoostList.packages(context)
            val before = memAvailableKb()
            if (targets.isNotEmpty()) Shell.batch(targets.map { Shell.Cmd.trimMemory(it) })
            Shell.batch(BoostList.killCommands(listed - self, targets))
            val caches = if (Caches.clearOnBoost(context)) Caches.clear(context) else null
            Thread.sleep(3000)   // the freed memory shows a little after the trim
            val after = memAvailableKb()
            val freedMb = ((after - before).coerceAtLeast(0L)) / 1024
            val message = if (before == 0L) context.getString(R.string.memory_unreadable) else
                context.getString(R.string.boost_done, targets.size, listed.size, freedMb.toInt()) +
                    " (${before / 1024} to ${after / 1024} MB available)" +
                    (caches?.let { " " + context.getString(R.string.boost_cache, (it.beforeMb - it.afterMb).coerceAtLeast(0L).toInt()) } ?: "")
            Handler(Looper.getMainLooper()).post { onDone(message) }
            if (self in listed) {
                Thread.sleep(1500)
                Shell.forceStop(self)
            }
        }.start()
    }

    private fun memAvailableKb(): Long =
        Regex("MemAvailable:\\s+(\\d+)").find(Shell.meminfo().output)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
}
