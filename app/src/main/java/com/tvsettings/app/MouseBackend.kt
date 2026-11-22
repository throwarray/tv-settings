package com.tvsettings.app

import android.content.Context

// One remote mouse, two engines.
// Remap: remapd holds the remote and draws a pointer through a uinput or uhid device it creates. Shizuku starts it.
// Accessibility: our overlay cursor. The arrows and Play reach it because remapd forwards the keys it doesn't map.
// Moving and clicking go through a persistent pointer injector in Shizuku's shell (see PointerPipe).
object MouseBackend {
    const val AUTO = "auto"
    const val REMAP = "remap"
    const val ACCESSIBILITY = "accessibility"

    // Why the last change failed, for a toast
    var lastError = ""
        private set

    fun enabled(context: Context): Boolean = OverlayPrefs.mouseEnabled(context) || RemapEngine.mouseOn(context)

    // The engine that runs the mouse for the current setting. Auto takes remap only where the shell can create a
    // HID device (uinput or uhid); elsewhere the accessibility cursor runs.
    fun resolve(context: Context): String = when (OverlayPrefs.backend(context)) {
        REMAP -> REMAP
        ACCESSIBILITY -> ACCESSIBILITY
        else -> if (RemapEngine.remapperOn(context) && hidAvailable()) REMAP else ACCESSIBILITY
    }

    private fun hidAvailable(): Boolean =
        ShizukuManager.isReady && ShizukuManager.exec("[ -w /dev/uinput ] || [ -w /dev/uhid ]").ok

    // Blocking. False, with lastError set, when the chosen engine can't run.
    fun setEnabled(context: Context, on: Boolean): Boolean {
        lastError = ""
        if (!on) {
            OverlayPrefs.setMouse(context, false)
            RemapEngine.clearMouse(context)
            RemapEngine.sync(context)
            return true
        }
        if (resolve(context) == REMAP) {
            OverlayPrefs.setMouse(context, false)
            if (RemapEngine.setMouse(context, true)) return true
            RemapEngine.clearMouse(context)
            if (OverlayPrefs.backend(context) == REMAP) {
                lastError = "Remap mouse didn't start: ${RemapEngine.lastError}"
                return false
            }
            // auto: remapd couldn't start, so the accessibility cursor takes over
        }
        RemapEngine.clearMouse(context)
        if (!OverlayPrefs.setMouse(context, true)) {
            lastError = "Couldn't enable the accessibility service"
            return false
        }
        return true
    }

    // Switches the engine. A mouse that is on moves to the new one. The Play/Pause toggle follows the backend too.
    fun changeBackend(context: Context, backend: String): Boolean {
        val on = enabled(context)
        OverlayPrefs.setBackend(context, backend)
        RemapEngine.updateRules(context, OverlayPrefs.homeLaunchTarget(context))
        if (!on) return true
        setEnabled(context, false)
        return setEnabled(context, true)
    }

    // One speed for both engines (OverlayPrefs.speedPercent): the cursor's step and remapd's steps
    fun setSpeed(context: Context, speed: String) = OverlayPrefs.setSpeed(context, speed)
}
