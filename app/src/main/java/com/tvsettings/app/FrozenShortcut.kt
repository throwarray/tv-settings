package com.tvsettings.app

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.os.Build

/**
 * A launcher shortcut that opens the app, whether or not it is frozen (disabled). It goes through LaunchTrampolineActivity,
 * which enables a disabled app first (needs Shizuku) and then launches it. Use it to hide the app's own icon.
 */
object FrozenShortcut {
    fun add(context: Context, pkg: String, label: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val sm = context.getSystemService(ShortcutManager::class.java) ?: return false
        if (!sm.isRequestPinShortcutSupported) return false
        ActivityPrefs.allowShortcut(context, "$pkg/*")
        val intent = Intent(context, LaunchTrampolineActivity::class.java)
            .setAction(LaunchTrampolineActivity.ACTION)
            .putExtra("pkg", pkg)
        val info = ShortcutInfo.Builder(context, "open:$pkg")
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(Icon.createWithBitmap(appIcon(context, pkg)))
            .setIntent(intent)
            .build()
        return sm.requestPinShortcut(info, null)
    }

    private fun appIcon(context: Context, pkg: String): Bitmap {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        runCatching { context.packageManager.getApplicationIcon(pkg) }.getOrNull()?.apply {
            setBounds(0, 0, size, size)
            draw(Canvas(bitmap))
        }
        return bitmap
    }
}
