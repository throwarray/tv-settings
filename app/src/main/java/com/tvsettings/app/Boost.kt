package com.tvsettings.app

import android.content.Context

// Apps to force close when Boost is pressed, kept on the device
object BoostList {
    private const val PREFS = "boost"
    private const val KEY = "kill"

    fun packages(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY, emptySet()) ?: emptySet()

    fun contains(context: Context, pkg: String): Boolean = pkg in packages(context)

    fun set(context: Context, pkg: String, on: Boolean) {
        val s = packages(context).toMutableSet()
        if (on) s.add(pkg) else s.remove(pkg)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY, s).apply()
    }

    // Force-stops the listed apps, and any running app whose background run is set to ignore
    fun killCommands(listed: Collection<String>, running: Collection<String>): List<String> =
        listed.map { Shell.Cmd.forceStop(it) } + running.map { Shell.Cmd.forceStopIfBackgroundIgnored(it) }
}
