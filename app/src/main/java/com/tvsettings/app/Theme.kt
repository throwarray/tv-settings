package com.tvsettings.app

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Palette for the current theme. [dark] is set by [TVSettingsTheme] on every composition. */
object Oled {
    var dark = true
    val background get() = if (dark) Color.Black else Color(0xFFF4F4F4)
    val card get() = if (dark) Color.Black else Color(0xFFFFFFFF)
    val row get() = if (dark) Color(0xFF151515) else Color(0xFFE8E8E8)
    val rowFocused get() = if (dark) Color(0xFF262626) else Color(0xFFD6D6D6)
    val selected get() = if (dark) Color(0xFF333333) else Color(0xFFCFCFCF)
    val skeleton get() = if (dark) Color(0xFF1C1C1C) else Color(0xFFE0E0E0)
    val focusBorder = Color(0xFF0A84FF)
    val okGreen get() = if (dark) Color(0xFF8CF3A5) else Color(0xFF1B6B36)
    val textPrimary get() = if (dark) Color.White else Color(0xFF111111)
    val textSecondary get() = if (dark) Color(0xFF9AA7B3) else Color(0xFF55606B)
    val textTertiary get() = if (dark) Color(0xFFB1BAC5) else Color(0xFF44505B)
    val thumb get() = if (dark) Color(0xFF8C8C8C) else Color(0xFF6E6E6E)
    val switchTrackOff get() = if (dark) Color(0xFF3B3B3B) else Color(0xFFBDBDBD)
}

object Dimens {
    val contentMaxWidth = 1920.dp
    val advancedMaxWidth = 960.dp
    val textMaxWidth = 480.dp
}

/** The app's own theme (system, light or dark) and the system-wide night mode it can switch. */
object AppTheme {
    private const val PREFS = "tvsettings"
    private const val MODE = "app_theme"
    private const val SYSTEM_DARK = "system_dark"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // system | light | dark
    fun mode(context: Context): String = prefs(context).getString(MODE, null) ?: "system"

    fun setMode(context: Context, mode: String) {
        prefs(context).edit().putString(MODE, mode).apply()
    }

    // ui_night_mode in secure settings: 0 system, 1 light, 2 dark
    fun systemNight(context: Context): String =
        when (Settings.Secure.getInt(context.contentResolver, "ui_night_mode", 0)) {
            1 -> "light"
            2 -> "dark"
            else -> "system"
        }

    fun setSystemNight(context: Context, mode: String): Boolean {
        val value = when (mode) {
            "light" -> 1
            "dark" -> 2
            else -> 0
        }
        return ServiceSettings.putSecure(context, "ui_night_mode", value.toString())
    }
}

@Composable
fun TVSettingsTheme(content: @Composable () -> Unit) {
    val dark = when (AppTheme.mode(LocalContext.current)) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    Oled.dark = dark
    MaterialTheme(
        colorScheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
            primary = Oled.textPrimary,
            secondary = Oled.row,
            background = Oled.background,
            surface = Oled.card,
            surfaceContainer = Oled.card,
            onPrimary = Oled.background,
            onBackground = Oled.textPrimary,
            onSurface = Oled.textPrimary,
        ),
        content = content
    )
}
