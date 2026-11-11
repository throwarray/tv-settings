package com.tvsettings.app

import android.content.ComponentName

/** Every shell command the app runs, in one place. Argument checks and quoting live here. */
object Shell {
    // package names, ops, keys, permissions; free-form values are quoted instead
    private val SAFE = Regex("[A-Za-z0-9_.:-]+")
    private val UNSAFE = ShellResult(-1, "unsafe argument")

    fun safe(vararg parts: String): Boolean = parts.all { SAFE.matches(it) }

    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"

    private fun checked(vararg parts: String, command: () -> String): ShellResult =
        if (safe(*parts)) exec(command()) else UNSAFE

    fun exec(command: String): ShellResult = ShizukuManager.exec(command)

    // Starts an activity as the shell, which can open ones that aren't exported. The names are checked and quoted
    // (class names may contain $), never passed through as typed.
    private val COMPONENT_PACKAGE = Regex("[A-Za-z0-9_.]+")
    private val COMPONENT_CLASS = Regex("[A-Za-z0-9_.\$]+")

    fun startActivity(pkg: String, cls: String): ShellResult =
        if (COMPONENT_PACKAGE.matches(pkg) && COMPONENT_CLASS.matches(cls)) exec("am start -n ${quote("$pkg/$cls")}") else UNSAFE

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
    fun appOpsGet(pkg: String) = checked(pkg) { "appops get $pkg" }

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

    fun scrollSwipe(x1: Int, y1: Int, x2: Int, y2: Int) = exec("input swipe $x1 $y1 $x2 $y2 200")

    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int) = exec("input swipe $x1 $y1 $x2 $y2 $durationMs")
}
