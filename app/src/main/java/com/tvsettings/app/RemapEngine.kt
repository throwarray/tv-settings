package com.tvsettings.app

import android.content.Context

// Button remapping through remapd, Stickman's daemon (MIT, Mohamed Nofal; see THIRD_PARTY.md).
// It runs under Shizuku's shell. Without Shizuku the accessibility Home redirect stays in charge.
object RemapEngine {
    private const val DIR = "/data/local/tmp/stickman"
    private const val REMOTE = "Amazon Fire TV Remote"
    private const val PREFS = "remap_engine"
    // Linux input codes for Home: KEY_HOME, KEY_HOMEPAGE
    private val HOME_CODES = listOf(102, 172)
    // Play/Pause: a double press toggles the remote mouse (remapd draws it as a real HID pointer when it can)
    private const val MOUSE_TOGGLE_CODE = 164
    // True while remapd is up. The accessibility path stays off while it is, and comes back if it goes down.
    @Volatile private var up = false

    // The engine owns Home and the mouse toggle: TVSettings's accessibility versions stand down.
    fun active(context: Context): Boolean = up && enabled(context) && ShizukuManager.isReady

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun homeRemap(context: Context): Boolean = prefs(context).getBoolean("home_remap", true)

    fun setHomeRemap(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("home_remap", on).apply()
    }

    // The engine runs while Home remap or the remote mouse is on
    fun enabled(context: Context): Boolean = remapperOn(context) && (homeRemap(context) || mouseOn(context))

    // Off: Stickman or another remapper owns the remote. Home then falls back to accessibility and the mouse is off.
    fun remapperOn(context: Context): Boolean = prefs(context).getBoolean("remapper", true)

    fun setRemapperOn(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("remapper", on).apply()
        if (on && enabled(context)) restart(context) else stop()
    }

    // Stop, wait until the old remapd is really gone, then start a fresh one with the current rules
    private fun restart(context: Context): Boolean {
        stop()
        for (i in 0 until 15) {
            if (!running()) break
            Thread.sleep(200)
        }
        return start(context, OverlayPrefs.homeLaunchTarget(context))
    }

    // Brings the engine in line with the settings: started when wanted, stopped when nothing needs it
    fun sync(context: Context): Boolean {
        val launch = OverlayPrefs.homeLaunchTarget(context)
        if (!enabled(context)) return if (up) stop() else true
        return (up && ShizukuManager.isReady) || start(context, launch)
    }

    fun running(): Boolean = Shell.pidAlive("$DIR/remapd.pid", "remapd")

    // remap.conf v2 lines: "<code> <layer> <action>"
    // Linux code for the remote's Recents key, from getevent -l
    const val RECENT_CODE = 748   // 0x02ec from getevent -l
    const val RECENTS_TARGET = "com.amazon.tv.launcher/com.amazon.tv.launcher.deeplink.activity.RecentDeepLinkActivityDI"

    fun recents(context: Context): Boolean = prefs(context).getBoolean("recents", true)

    fun doubleMs(context: Context): Int = prefs(context).getInt("double_ms", 250)
    fun holdMs(context: Context): Int = prefs(context).getInt("hold_ms", 600)

    // Written into the rules file; remapd picks them up when the file changes
    fun setTiming(context: Context, doubleMs: Int, holdMs: Int) {
        prefs(context).edit().putInt("double_ms", doubleMs).putInt("hold_ms", holdMs).apply()
        updateRules(context, OverlayPrefs.homeLaunchTarget(context))
    }

    fun setRecents(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("recents", on).apply()
    }

    // Home opens the launcher the way a Home press does (HOME category); Play/Pause double press toggles the mouse
    private fun rules(context: Context, launch: String): List<String> {
        // one tap: the custom launcher; double press: the stock (FTV) home, as the accessibility path does
        val stock = OverlayPrefs.homeRedirectTarget(context)
        val start = "sh am start -a android.intent.action.MAIN -c android.intent.category.HOME -f 0x10200000 -n"
        val stockTarget = ShellText.component(stock)
        val home = HOME_CODES.flatMap { code -> listOfNotNull("$code tap sh $DIR/home.sh", stockTarget?.let { "$code double $start $it" }) }
        val mouse = "$MOUSE_TOGGLE_CODE double act mouse_toggle"
        val recentRule = if (recents(context)) listOf("$RECENT_CODE tap sh am start -n $RECENTS_TARGET") else emptyList()
        val timing = listOf("set double_ms ${doubleMs(context)}", "set hold_ms ${holdMs(context)}")
        return timing + home + mouse + recentRule
    }

    // remapd on the shell's side: the extracted native library if it exists, else copied out of the APK.
    private fun binaryPath(context: Context): String? {
        val native = context.applicationInfo.nativeLibraryDir + "/libremapd.so"
        if (Shell.fileExists(native)) return native
        val staged = context.getExternalFilesDir(null)?.resolve("remapd") ?: return null
        java.util.zip.ZipFile(context.packageCodePath).use { zip ->
            val entry = zip.getEntry("lib/armeabi-v7a/libremapd.so") ?: return null
            zip.getInputStream(entry).use { input -> staged.outputStream().use { input.copyTo(it) } }
        }
        val dest = "$DIR/remapd"
        return dest.takeIf { Shell.installExecutable(staged.path, DIR, dest) }
    }

    // Why the last start failed, for the toast
    var lastError = ""
        private set

    private fun logTail(): String =
        Shell.logTail("RemapD", 3).ifEmpty { "no RemapD log lines" }

    fun start(context: Context, launch: String): Boolean {
        lastError = ""
        if (!ShizukuManager.isReady) {
            lastError = "Shizuku isn't running"
            return false
        }
        if (running()) {
            up = true
            return true
        }
        val lib = binaryPath(context)
        if (lib == null) {
            lastError = "remapd not found in the APK or the native library folder"
            return false
        }
        val conf = rules(context, launch).joinToString(" ") { "'$it'" }
        val steps = listOf(
            "mkdir -p $DIR",
            "printf '%s' '${context.packageCodePath}' > $DIR/apk",
            "printf '%s\\n' $conf > $DIR/remap.conf",
            homeScriptWrite(launch),
            "( setsid $lib $DIR/remap.conf '$REMOTE' $DIR/remapd.pid || $lib $DIR/remap.conf '$REMOTE' $DIR/remapd.pid ) </dev/null >/dev/null 2>&1 &",
        )
        if (!Shell.script(steps).ok) {
            lastError = "couldn't write to $DIR"
            return false
        }
        repeat(40) {
            if (running()) {
                up = true
                return true
            }
            Thread.sleep(200)
        }
        lastError = "remapd exited: " + logTail()
        return false
    }

    // Used when the Advanced page opens: restart the engine after a reboot or a crash, if it was turned on.
    fun ensure(context: Context, launch: String) {
        up = running()
        // a running remapd may have older rules (before a rule was added): rewrite them every time
        if (up) Shell.script(listOf(rulesWrite(context, launch)))
        sync(context)
    }

    // The script behind the Home tap: One Touch Play wakes the TV and switches it to this input, then the launcher opens
    private fun homeScriptWrite(launch: String): String {
        // a name that isn't a plain package/class writes nothing: Home keeps its last script
        val target = ShellText.component(launch) ?: return ":"
        return ShellText.writeFile(
            "$DIR/home.sh",
            "cmd hdmi_control onetouchplay >/dev/null 2>&1; am start -a android.intent.action.MAIN -c android.intent.category.HOME -f 0x10200000 -n $target",
            executable = true,
        )
    }

    private fun rulesWrite(context: Context, launch: String): String =
        ShellText.writeFile("$DIR/remap.conf", rules(context, launch).joinToString("\n"))

    // Applies a new launch target to a running engine. remapd re-reads remap.conf when it changes.
    fun updateRules(context: Context, launch: String): Boolean =
        active(context) && Shell.script(listOf(homeScriptWrite(launch), rulesWrite(context, launch))).ok

    private const val MOUSE_CMD = "$DIR/mouse.cmd"

    fun mouseOn(context: Context): Boolean = prefs(context).getBoolean("mouse_on", false)

    // remapd reads "on" / "off" from mouse.cmd (the same file Stickman's app writes) and consumes it
    fun setMouse(context: Context, on: Boolean): Boolean {
        prefs(context).edit().putBoolean("mouse_on", on).apply()
        val running = sync(context)
        return running && Shell.writeText(MOUSE_CMD, if (on) "on" else "off").ok
    }

    fun stop(): Boolean {
        up = false
        return Shell.killPid("$DIR/remapd.pid", "remapd")
    }
}
