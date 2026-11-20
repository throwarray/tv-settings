package com.tvsettings.app

import android.content.ComponentName

/** Every shell command the app runs, in one place. Argument checks and quoting live here. */
object Shell {
    private val UNSAFE = ShellResult(-1, "unsafe argument")

    // names are checked, free-form values are quoted: both live in ShellText, which is tested against a real sh
    fun safe(vararg parts: String): Boolean = ShellText.safe(*parts)

    private fun quote(value: String) = ShellText.quote(value)

    private fun checked(vararg parts: String, command: () -> String): ShellResult =
        if (safe(*parts)) exec(command()) else UNSAFE

    fun exec(command: String): ShellResult = ShizukuManager.exec(command)

    // ---- several commands in one run ----

    /** One shell run for many commands, output silenced. One command failing doesn't stop the others. */
    fun batch(commands: Collection<String>): ShellResult =
        if (commands.isEmpty()) ShellResult(0, "") else exec(commands.joinToString("; ") { "$it >/dev/null 2>&1" })

    /** Steps that must all succeed, in order. */
    fun script(steps: List<String>): ShellResult = exec(steps.joinToString(" && "))

    /** The shell runner in Advanced: whatever was typed, as typed. The only free-form command. */
    fun runUserCommand(command: String): ShellResult = exec(command)

    /** Command text for batches. A name that isn't safe becomes a no-op, never part of a command. */
    object Cmd {
        private const val NOOP = ":"

        fun forceStop(pkg: String) = if (Shell.safe(pkg)) "am force-stop $pkg" else NOOP
        fun kill(pkg: String) = if (Shell.safe(pkg)) "am kill $pkg" else NOOP
        fun trimMemory(pkg: String) = if (Shell.safe(pkg)) "am send-trim-memory $pkg MODERATE" else NOOP
        fun forceStopIfBackgroundIgnored(pkg: String) =
            if (Shell.safe(pkg)) "cmd appops get $pkg RUN_ANY_IN_BACKGROUND | grep -q ignore && am force-stop $pkg" else NOOP
        fun appOps(pkg: String, op: String, mode: String) = if (Shell.safe(pkg, op, mode)) "cmd appops set $pkg $op $mode" else NOOP
        fun standbyBucket(pkg: String, bucket: String) = if (Shell.safe(pkg, bucket)) "am set-standby-bucket $pkg $bucket" else NOOP
        fun idleWhitelist(pkg: String, add: Boolean) = if (Shell.safe(pkg)) "cmd deviceidle whitelist ${if (add) "+" else "-"}$pkg" else NOOP
        fun clearCache(pkg: String) = if (Shell.safe(pkg)) "pm clear --cache-only $pkg" else NOOP
        fun clearExternalCache(pkg: String) = if (Shell.safe(pkg)) "rm -rf /sdcard/Android/data/$pkg/cache/*" else NOOP
        fun suspend(pkg: String, on: Boolean) = if (Shell.safe(pkg)) "pm ${if (on) "suspend" else "unsuspend"} --user 0 $pkg" else NOOP
        fun setEnabled(pkg: String, enabled: Boolean) =
            if (!Shell.safe(pkg)) NOOP else if (enabled) "pm enable $pkg" else "pm disable-user --user 0 $pkg"
    }

    // ---- packages ----
    fun trimCaches() = exec("pm trim-caches 99999999999999")

    fun installApk(path: String) = exec("pm install -r ${quote(path)}")

    fun clearData(pkg: String) = checked(pkg) { "pm clear $pkg" }

    /** Removes the app for this user. Unlike the system's own screen, this also works on system apps. */
    fun uninstallForUser(pkg: String) = checked(pkg) { "pm uninstall --user 0 $pkg" }

    // ---- files and the system ----
    fun id() = exec("id")

    fun copy(src: String, dest: String) = exec("cp ${quote(src)} ${quote(dest)}")

    fun listDir(path: String) = exec("ls -1Ap ${quote(path)} 2>/dev/null")

    fun fileExists(path: String): Boolean = exec("[ -f ${quote(path)} ] && echo ok").output.contains("ok")

    fun writeText(path: String, text: String) = exec("printf '%s' ${quote(text)} > ${quote(path)}")

    /** Copies a file into dir and makes it executable. */
    fun installExecutable(src: String, dir: String, dest: String): Boolean =
        exec("mkdir -p ${quote(dir)} && cp ${quote(src)} ${quote(dest)} && chmod 755 ${quote(dest)} && echo ok").output.contains("ok")

    fun logTail(tag: String, lines: Int): String =
        if (safe(tag)) exec("logcat -d -s $tag | tail -n $lines").output.trim() else ""

    fun wifi(enabled: Boolean) = exec("svc wifi ${if (enabled) "enable" else "disable"}")

    fun meminfo() = exec("cat /proc/meminfo")

    fun procStat() = exec("head -n 1 /proc/stat")

    // ---- a process kept in a pid file ----
    private fun pidCheck(pidFile: String, comm: String) =
        "p=\$(cat ${quote(pidFile)} 2>/dev/null); [ -n \"\$p\" ] && grep -q $comm /proc/\$p/comm 2>/dev/null"

    fun pidAlive(pidFile: String, comm: String): Boolean =
        safe(comm) && exec(pidCheck(pidFile, comm) + " && echo yes").output.contains("yes")

    fun killPid(pidFile: String, comm: String): Boolean =
        safe(comm) && exec(pidCheck(pidFile, comm) + " && kill \$p; rm -f ${quote(pidFile)}").ok

    // ---- usage sampling: ResourceUsage reads the sections by these markers ----
    const val MARK_MEM = "@@MEM"
    const val MARK_PS = "@@PS"
    const val MARK_STAT = "@@STAT"

    /** One run: total CPU, memory, then every process. */
    fun usageSnapshot(): ShellResult = exec(
        "head -n 1 /proc/stat; echo $MARK_MEM; head -n 3 /proc/meminfo; echo $MARK_PS; " +
            "ps -A -o PID,RSS,NAME; echo $MARK_STAT; " +
            "for f in /proc/[0-9]*/stat; do read -r l 2>/dev/null < \"\$f\" && echo \"\$l\"; done"
    )

    // Starts an activity as the shell, which can open ones that aren't exported. The names are checked and quoted
    // (class names may contain $), never passed through as typed.
    fun startActivity(pkg: String, cls: String): ShellResult =
        ShellText.component("$pkg/$cls")?.let { exec("am start -n $it") } ?: UNSAFE

    // settings
    fun settingsGet(table: String, key: String): String? {
        if (!ShizukuManager.isReady || !safe(table, key)) return null
        val r = exec("settings get $table $key")
        return r.lines.firstOrNull()?.takeIf { r.ok && it != "null" }
    }

    fun settingsPut(table: String, key: String, value: String) =
        checked(table, key) { "settings put $table $key ${quote(value)}" }

    fun settingsDelete(table: String, key: String) = checked(table, key) { "settings delete $table $key" }

    // app ops and permissions
    fun appOpsGet(pkg: String, op: String? = null) =
        if (op == null) checked(pkg) { "appops get $pkg" } else checked(pkg, op) { "appops get $pkg $op" }

    fun appOpsSet(pkg: String, op: String, mode: String, uid: Boolean = false) =
        checked(pkg, op, mode) { "appops set ${if (uid) "--uid " else ""}$pkg $op $mode" }

    fun permission(pkg: String, permission: String, grant: Boolean) =
        checked(pkg, permission) { "pm ${if (grant) "grant" else "revoke"} $pkg $permission" }

    // apps
    fun setEnabled(pkg: String, enabled: Boolean) =
        checked(pkg) { if (enabled) "pm enable $pkg" else "pm disable-user --user 0 $pkg" }

    fun forceStop(pkg: String) = checked(pkg) { "am force-stop $pkg" }

    fun setHomeActivity(component: ComponentName) =
        exec("pm set-home-activity --user 0 ${quote(component.flattenToString())}")

    // 65536 = MATCH_DEFAULT_ONLY
    fun resolveHome() =
        exec("cmd package resolve-activity --brief --query-flags 65536 -a android.intent.action.MAIN -c android.intent.category.HOME")

    fun processNames() = exec("ps -A -o NAME")

    fun dumpsys(service: String, vararg args: String) =
        checked(service, *args) { (listOf("dumpsys", service) + args).joinToString(" ") }

    fun idleWhitelist(pkg: String, enabled: Boolean) =
        checked(pkg) { "dumpsys deviceidle whitelist ${if (enabled) "+" else "-"}$pkg" }

    fun widgetHasBind(pkg: String) = checked(pkg) { "cmd appwidget hasbindpermission --package $pkg --user 0" }

    fun widgetBind(pkg: String, enabled: Boolean) =
        checked(pkg) { "cmd appwidget ${if (enabled) "grantbind" else "revokebind"} --package $pkg --user 0" }

    fun notificationListener(component: ComponentName, enabled: Boolean) =
        exec("cmd notification ${if (enabled) "allow_listener" else "disallow_listener"} ${quote(component.flattenToString())}")

    fun ime(action: String, id: String) = checked(action) { "ime $action ${quote(id)}" }

    // system
    fun setLocationEnabled(enabled: Boolean) = exec("cmd location set-location-enabled $enabled")

    // the newer window-manager route; the older one is the user_rotation / accelerometer_rotation settings
    fun rotationLock(rotation: Int) = exec("wm user-rotation lock $rotation")

    fun rotationFree() = exec("wm user-rotation free")

    // input
    fun tap(x: Int, y: Int) = exec("input tap $x $y")

    // DOWN, MOVE or UP at a point; a press can be held while the pointer moves
    fun motion(action: String, x: Int, y: Int) = exec("input motionevent $action $x $y")

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int) = exec("input swipe $x1 $y1 $x2 $y2 $durationMs")
}
