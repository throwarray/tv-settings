package com.tvsettings.app

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** Bumps whenever an observed setting changes, by anyone. Key a LaunchedEffect on it. Only active while on screen. */
@Composable
fun rememberSecureSettingsTick(): Int {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                tick++
            }
        }
        ServiceSettings.OBSERVED_KEYS.forEach { key ->
            context.contentResolver.registerContentObserver(Settings.Secure.getUriFor(key), false, observer)
        }
        (listOf(ServiceSettings.SCREEN_OFF_TIMEOUT) + SystemSettings.SYSTEM_KEYS).forEach { key ->
            context.contentResolver.registerContentObserver(Settings.System.getUriFor(key), false, observer)
        }
        SystemSettings.GLOBAL_KEYS.forEach { key ->
            context.contentResolver.registerContentObserver(Settings.Global.getUriFor(key), false, observer)
        }
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return tick
}
