package com.tvsettings.app

import android.content.Context

// Pointer commands for the accessibility cursor. They go to PointerInjector, a process the shell service keeps
// running, so a press or a move is one line on a pipe instead of a new shell. Callers fall back to the per-command
// shell when this returns false.
object PointerPipe {
    @Volatile private var started = false

    private fun send(context: Context, line: String): Boolean {
        if (!started) started = ElevatedShellManager.pointer("START ${context.packageCodePath}") == "ok"
        if (!started) return false
        val ok = ElevatedShellManager.pointer(line) == "ok"
        if (!ok) started = false
        return ok
    }

    fun motion(context: Context, action: String, x: Int, y: Int): Boolean {
        val code = when (action) {
            "DOWN" -> "D"
            "UP" -> "U"
            else -> "M"
        }
        return send(context, "$code $x $y")
    }

    fun tap(context: Context, x: Int, y: Int): Boolean = send(context, "C $x $y")

    fun swipe(context: Context, x1: Int, y1: Int, x2: Int, y2: Int, ms: Int): Boolean =
        send(context, "S $x1 $y1 $x2 $y2 $ms")
}
