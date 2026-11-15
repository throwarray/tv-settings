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
    fun enabled(context: Context): Boolean = homeRemap(context) || mouseOn(context)

    // Brings the engine in line with the settings: started when wanted, stopped when nothing needs it
    fun sync(context: Context): Boolean {
        val launch = OverlayPrefs.homeLaunchTarget(context)
        if (!enabled(context)) return if (up) stop() else true
        return (up && ShizukuManager.isReady) || start(context, launch)
    }

    fun running(): Boolean = Shell.exec(
        "p=\$(cat $DIR/remapd.pid 2>/dev/null); [ -n \"\$p\" ] && grep -q remapd /proc/\$p/comm 2>/dev/null && echo yes"
    ).output.contains("yes")

    // remap.conf v2 lines: "<code> <layer> <action>"
    // Home opens the launcher the way a Home press does (HOME category), not as a LAUNCHER app
    private fun rules(launch: String): List<String> =
        HOME_CODES.map { "$it tap sh am start -a android.intent.action.MAIN -c android.intent.category.HOME -f 0x10200000 -n $launch" } +
            "$MOUSE_TOGGLE_CODE double act mouse_toggle"

    // remapd on the shell's side: the extracted native library if it exists, else copied out of the APK.
    private fun binaryPath(context: Context): String? {
        val native = context.applicationInfo.nativeLibraryDir + "/libremapd.so"
        if (Shell.exec("[ -f '$native' ] && echo ok").output.contains("ok")) return native
        val staged = context.getExternalFilesDir(null)?.resolve("remapd") ?: return null
        java.util.zip.ZipFile(context.packageCodePath).use { zip ->
            val entry = zip.getEntry("lib/armeabi-v7a/libremapd.so") ?: return null
            zip.getInputStream(entry).use { input -> staged.outputStream().use { input.copyTo(it) } }
        }
        val dest = "$DIR/remapd"
        val copied = Shell.exec("mkdir -p $DIR && cp '${staged.path}' '$dest' && chmod 755 '$dest' && echo ok")
        return dest.takeIf { copied.output.contains("ok") }
    }

    // Why the last start failed, for the toast
    var lastError = ""
        private set

    private fun logTail(): String =
        Shell.exec("logcat -d -s RemapD | tail -n 3").output.trim().ifEmpty { "no RemapD log lines" }

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
        val conf = rules(launch).joinToString(" ") { "'$it'" }
        val steps = listOf(
            "mkdir -p $DIR",
            "printf '%s' '${context.packageCodePath}' > $DIR/apk",
            "printf '%s\\n' $conf > $DIR/remap.conf",
            "( setsid $lib $DIR/remap.conf '$REMOTE' $DIR/remapd.pid || $lib $DIR/remap.conf '$REMOTE' $DIR/remapd.pid ) </dev/null >/dev/null 2>&1 &",
        )
        if (!Shell.exec(steps.joinToString(" && ")).ok) {
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
        sync(context)
    }

    private fun rulesWrite(launch: String): String =
        "printf '%s\\n' ${rules(launch).joinToString(" ") { "'$it'" }} > $DIR/remap.conf"

    // Applies a new launch target to a running engine. remapd re-reads remap.conf when it changes.
    fun updateRules(context: Context, launch: String): Boolean =
        active(context) && Shell.exec(rulesWrite(launch)).ok

    private const val MOUSE_CMD = "$DIR/mouse.cmd"

    fun mouseOn(context: Context): Boolean = prefs(context).getBoolean("mouse_on", false)

    // remapd reads "on" / "off" from mouse.cmd (the same file Stickman's app writes) and consumes it
    fun setMouse(context: Context, on: Boolean): Boolean {
        prefs(context).edit().putBoolean("mouse_on", on).apply()
        val running = sync(context)
        return running && Shell.exec("printf '%s' ${if (on) "on" else "off"} > $MOUSE_CMD").ok
    }

    fun stop(): Boolean {
        up = false
        return Shell.exec(
            "p=\$(cat $DIR/remapd.pid 2>/dev/null); [ -n \"\$p\" ] && grep -q remapd /proc/\$p/comm 2>/dev/null && kill \$p; rm -f $DIR/remapd.pid"
        ).ok
    }
}
