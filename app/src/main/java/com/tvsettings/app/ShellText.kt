package com.tvsettings.app

/**
 * Argument checks and quoting for shell commands. Pure Kotlin on purpose: tests/shell/ShellTextTest.kt runs the
 * results through a real sh with hostile strings. Everything that puts a name or a path into command text goes
 * through here (Shell.kt, and RemapEngine for the files remapd runs).
 */
object ShellText {
    // package names, ops, keys, permissions. Never starts with '-', so a value can't be read as an option.
    private val SAFE = Regex("[A-Za-z0-9_.:][A-Za-z0-9_.:-]*")

    // package/class, where the class may contain $ (inner classes) and may start with '.'
    private val COMPONENT = Regex("[A-Za-z0-9_.]+/[A-Za-z0-9_.\$]+")

    fun safe(vararg parts: String): Boolean = parts.all { SAFE.matches(it) }

    /** One shell word that means exactly [value]. */
    fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    /** A component as one quoted shell word, or null when it isn't a plain package/class name. */
    fun component(value: String): String? = if (COMPONENT.matches(value)) quote(value) else null

    /** A command that writes [content] to a file, and makes it executable when asked. */
    fun writeFile(path: String, content: String, executable: Boolean = false): String =
        "printf '%s\\n' ${quote(content)} > ${quote(path)}" + if (executable) " && chmod 755 ${quote(path)}" else ""
}
