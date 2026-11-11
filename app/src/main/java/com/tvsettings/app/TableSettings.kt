package com.tvsettings.app

import android.content.Context
import android.provider.Settings

// Reads and writes a setting in the table it belongs to: secure, global or system.
object TableSettings {
    fun read(context: Context, table: String, key: String): String? {
        val cr = context.contentResolver
        return when (table) {
            "global" -> Settings.Global.getString(cr, key)
            "system" -> Settings.System.getString(cr, key)
            else -> Settings.Secure.getString(cr, key)
        }
    }

    fun put(context: Context, table: String, key: String, value: String): Boolean =
        if (table == "secure") ServiceSettings.putSecure(context, key, value)
        else Shell.settingsPut(table, key, value).ok
}
