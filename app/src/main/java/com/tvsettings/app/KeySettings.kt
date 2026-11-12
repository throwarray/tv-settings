package com.tvsettings.app

import android.content.Context
import android.provider.Settings

/**
 * A 0/1 setting shown as a toggle. On always means the private choice, so settings where 1 turns collection
 * on are [inverted]: on writes 0.
 */
data class FlagSetting(
    val key: String,
    val titleRes: Int,
    val inverted: Boolean = false,
    val also: List<String> = emptyList(),
) {
    val subtitle: String get() = "$key (on = ${if (inverted) 0 else 1})"
    val allKeys: List<String> get() = listOf(key) + also

    fun stateOf(raw: String?): Boolean = if (inverted) raw == "0" else raw == "1"

    fun valueFor(on: Boolean): String = if (on != inverted) "1" else "0"
}

/** Settings addressed by key alone. The table is the one the key already lives in, secure if it isn't set yet. */
object KeySettings {
    val privacy = listOf(
        FlagSetting("limit_ad_tracking", R.string.privacy_limit_ad_tracking,
            also = listOf("amz_limit_ad_tracking", "non_amz_limit_ad_tracking")),
        FlagSetting("alexa_wake_word_disabled", R.string.privacy_disable_alexa_wake_word),
        FlagSetting("usage_metrics_marketing_enabled", R.string.privacy_disable_usage_metrics, inverted = true,
            also = listOf("USAGE_METRICS_UPLOAD_ENABLED", "settingsApp:appUsageData:value", "settingsApp:deviceUsageData:value")),
        FlagSetting("ambient_motion_detection", R.string.privacy_disable_ambient_motion, inverted = true),
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
