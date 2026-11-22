package com.tvsettings.app

import android.content.Context

/**
 * Blocks all of one app's network through Shizuku (FirewallBlock). Saved so it can be re-applied after boot or a Shizuku restart.
 */
object NetBlock {
    private const val PREFS = "netblock"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isBlocked(context: Context, pkg: String): Boolean = prefs(context).getBoolean(pkg, false)

    fun set(context: Context, pkg: String, on: Boolean): Boolean {
        val uid = runCatching { context.packageManager.getApplicationInfo(pkg, 0).uid }.getOrNull() ?: return false
        if (!FirewallBlock.apply(pkg, uid, on)) return false
        prefs(context).edit().putBoolean(pkg, on).apply()
        return true
    }

    /** After boot or Shizuku coming back: the policy isn't kept across a reboot */
    fun reapply(context: Context) {
        for ((pkg, value) in prefs(context).all) {
            if (value == true) set(context, pkg, true)
        }
    }
}
