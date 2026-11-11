package com.tvsettings.app

import android.content.Context
import android.provider.Settings

/**
 * A 0/1 setting shown as a toggle. On always means the private choice, so settings where 1 turns collection
 * on are [inverted]: on writes 0.
 */
data class FlagSetting(val key: String, val title: String, val inverted: Boolean = false) {
    val subtitle: String get() = "$key (on = ${if (inverted) 0 else 1})"

    fun stateOf(raw: String?): Boolean = if (inverted) raw == "0" else raw == "1"

    fun valueFor(on: Boolean): String = if (on != inverted) "1" else "0"
}

/** Settings addressed by key alone. The table is the one the key already lives in, secure if it isn't set yet. */
object KeySettings {
    val privacy = listOf(
        FlagSetting("limit_ad_tracking", "Limit ad tracking"),
        FlagSetting("amz_limit_ad_tracking", "Limit Amazon tailored ads"),
        FlagSetting("non_amz_limit_ad_tracking", "Limit non-Amazon tailored ads"),
        FlagSetting("alexa_wake_word_disabled", "Disable Alexa wake word"),
        FlagSetting("usage_metrics_marketing_enabled", "Disable marketing usage metrics", inverted = true),
        FlagSetting("USAGE_METRICS_UPLOAD_ENABLED", "Disable usage metrics upload", inverted = true),
        FlagSetting("ambient_motion_detection", "Disable ambient motion detection", inverted = true),
        FlagSetting("settingsApp:appUsageData:value", "Disable app usage data", inverted = true),
        FlagSetting("settingsApp:deviceUsageData:value", "Disable device usage data", inverted = true),
    )

    private fun table(context: Context, key: String): String {
        val cr = context.contentResolver
        return when {
            Settings.Secure.getString(cr, key) != null -> "secure"
            Settings.Global.getString(cr, key) != null -> "global"
            Settings.System.getString(cr, key) != null -> "system"
            else -> "secure"
        }
    }

    fun read(context: Context, key: String): String? {
        val cr = context.contentResolver
        return Settings.Secure.getString(cr, key) ?: Settings.Global.getString(cr, key) ?: Settings.System.getString(cr, key)
    }

    fun isOn(context: Context, key: String): Boolean = read(context, key) == "1"

    fun isOn(context: Context, flag: FlagSetting): Boolean = flag.stateOf(read(context, flag.key))

    fun write(context: Context, key: String, value: String): Boolean {
        val table = table(context, key)
        return if (table == "secure") ServiceSettings.putSecure(context, key, value) else Shell.settingsPut(table, key, value).ok
    }

    // The ambient timeout should generally match the screen off timeout, so it follows when the key exists.
    fun writeTimeout(context: Context, ms: String): Boolean {
        val ok = write(context, "screen_off_timeout", ms)
        if (ok && read(context, "ambient_inactivity_to") != null) write(context, "ambient_inactivity_to", ms)
        return ok
    }
}
