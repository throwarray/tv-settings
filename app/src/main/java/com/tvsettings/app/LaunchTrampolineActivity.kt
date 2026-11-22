package com.tvsettings.app

import android.app.Activity
import android.os.Bundle

// Target of shortcuts that start through Shizuku's shell. Three kinds, all checked against the allowlist:
// an activity (pkg + cls: starred or a normal activity the user chose this for), or an app (pkg only: the app is
// enabled first if it is disabled, then launched). It isn't exported: pinned shortcuts are started by the system as
// this app, so only our own shortcuts reach it.
// The window is black (see the manifest) and stays until the target is up, so our own screen never shows behind it.
class LaunchTrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra("pkg")
        val cls = intent.getStringExtra("cls")
        if (pkg == null || !ActivityPrefs.shortcutAllowed(this, "$pkg/${cls ?: "*"}")) {
            finish()
            return
        }
        Thread { open(pkg, cls) }.start()
    }

    private fun open(pkg: String, cls: String?) {
        if (cls != null) {
            if (!ShizukuManager.isReady) return done("Needs Shizuku running")
            Shell.startActivity(pkg, cls)
            return done(null)
        }
        val enabled = runCatching { packageManager.getApplicationInfo(pkg, 0).enabled }.getOrDefault(false)
        if (!enabled) {
            if (!ShizukuManager.isReady) return done("Needs Shizuku running to enable $pkg")
            if (!Shell.setEnabled(pkg, true).ok) return done("Couldn't enable $pkg")
            // the package manager takes a moment to report the new state: wait for it, up to three seconds
            repeat(30) {
                if (runCatching { packageManager.getApplicationInfo(pkg, 0).enabled }.getOrDefault(false)) return@repeat
                Thread.sleep(100)
            }
        }
        runOnUiThread {
            AppActionController.launchApp(this, pkg)
            finish()
        }
    }

    // Shows a message if there is one, then leaves the black window
    private fun done(message: String?) = runOnUiThread {
        if (message != null) toast(message)
        finish()
    }

    companion object {
        const val ACTION = "com.tvsettings.app.LAUNCH_ACTIVITY"
    }
}
