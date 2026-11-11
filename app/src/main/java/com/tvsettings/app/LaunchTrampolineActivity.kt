package com.tvsettings.app

import android.app.Activity
import android.os.Bundle

// Target of shortcuts to starred (non-exported) activities. It isn't exported: pinned shortcuts are started by the
// system as this app, so only our own shortcuts reach it. The allowlist (activities the user made a shortcut for) is
// a second check, and the start goes through Shell.startActivity, which validates and quotes the names.
class LaunchTrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra("pkg")
        val cls = intent.getStringExtra("cls")
        if (pkg != null && cls != null && ActivityPrefs.shortcutAllowed(this, "$pkg/$cls")) {
            if (ShizukuManager.isReady) {
                Thread { Shell.startActivity(pkg, cls) }.start()
            } else {
                toast("Needs Shizuku running")
            }
        }
        finish()
    }

    companion object {
        const val ACTION = "com.tvsettings.app.LAUNCH_ACTIVITY"
    }
}
