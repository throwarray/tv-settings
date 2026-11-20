package com.tvsettings.app

import android.content.Context

/** Per-app background behaviour: normal, closed when left, or on the idle whitelist (exempt from battery optimisation). */
object Doze {
    const val NORMAL = "normal"
    const val EXIT = "exit"
    const val IDLE = "idle"

    /** Screens that take the foreground for a moment and give it back: the app under them hasn't been left. */
    val TRANSIENT_UI = setOf(
        "android", "com.android.systemui",
        "com.android.packageinstaller", "com.google.android.packageinstaller",
        "com.android.permissioncontroller", "com.google.android.permissioncontroller",
        "com.android.documentsui", "com.google.android.documentsui",
    )

    private const val PREFS = "doze"
    private val MODES = setOf(NORMAL, EXIT, IDLE)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // anything else saved (older versions had a "background off" mode) counts as normal
    fun mode(context: Context, pkg: String): String = prefs(context).getString(pkg, NORMAL)?.takeIf { it in MODES } ?: NORMAL

    fun isExit(context: Context, pkg: String): Boolean = mode(context, pkg) == EXIT

    // back to the system's defaults: not restricted, not whitelisted
    private fun reset(pkg: String) = listOf(
        Shell.Cmd.standbyBucket(pkg, "active"),
        Shell.Cmd.appOps(pkg, "RUN_ANY_IN_BACKGROUND", "default"),
        Shell.Cmd.appOps(pkg, "RUN_IN_BACKGROUND", "default"),
        Shell.Cmd.idleWhitelist(pkg, false),
    )

    private fun commands(pkg: String, mode: String): List<String> =
        if (mode == IDLE) listOf(
            Shell.Cmd.appOps(pkg, "RUN_ANY_IN_BACKGROUND", "default"),
            Shell.Cmd.appOps(pkg, "RUN_IN_BACKGROUND", "default"),
            Shell.Cmd.idleWhitelist(pkg, true),
        ) else reset(pkg)

    // Applies the mode now and saves it. "Close on exit" is done by the accessibility service when the app is left.
    fun apply(context: Context, pkg: String, mode: String): Boolean {
        prefs(context).edit().putString(pkg, mode).apply()
        return Shell.batch(commands(pkg, mode)).ok
    }

    // The old "background off" mode restricted apps and force-stopped them over and over. Apps that still carry it are put back to normal once.
    fun migrate(context: Context) {
        val p = prefs(context)
        for ((pkg, value) in p.all) {
            if (value == "background_off") {
                Shell.batch(reset(pkg))
                p.edit().putString(pkg, NORMAL).apply()
            }
        }
    }
}
