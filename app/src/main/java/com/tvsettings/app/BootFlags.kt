package com.tvsettings.app

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/** Settings that are applied at boot: ADB, wireless debugging, and the app launched at boot. */
object BootFlags {
    private const val PREFS = "boot_flags"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(context: Context, key: String): Boolean = prefs(context).getBoolean(key, false)
    fun set(context: Context, key: String, on: Boolean) { prefs(context).edit().putBoolean(key, on).apply() }

    fun launchTarget(context: Context): String = prefs(context).getString("launch_target", "") ?: ""
    fun setLaunchTarget(context: Context, component: String) { prefs(context).edit().putString("launch_target", component).apply() }

    /** Runs at boot. Off never writes anything: the system's own setting stays as it is. */
    fun applyAtBoot(context: Context) {
        if (get(context, "adb_on_boot")) writeGlobal(context, "adb_enabled", "1")
        if (get(context, "wireless_on_boot")) writeGlobal(context, "adb_wifi_enabled", "1")
    }

    // Direct when this app holds WRITE_SECURE_SETTINGS (the one-time grant from Advanced), else through Shizuku if it is up
    private fun writeGlobal(context: Context, key: String, value: String) {
        val granted = context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") ==
            PackageManager.PERMISSION_GRANTED
        if (granted && runCatching { Settings.Global.putString(context.contentResolver, key, value) }.getOrDefault(false)) return
        if (ShizukuManager.isReady) Shell.settingsPut("global", key, value)
    }
}
