package com.tvsettings.app

import android.content.pm.PackageManager
import android.accessibilityservice.AccessibilityService
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.AccessibilityService.GestureResultCallback
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import kotlin.math.abs
import java.util.concurrent.Executors

object OverlayPrefs {
    const val PREFS = "tvsettings"
    private const val MOUSE = "mouse_enabled"
    private const val DOT = "vpn_dot_enabled"
    private const val BACKEND = "mouse_backend"
    private const val ENFORCER = "enforcer_enabled"
    private const val HOME_REDIRECT = "home_redirect"
    private const val HOME_TARGET = "home_redirect_target"
    private const val HOME_LAUNCH = "home_redirect_launch"
    private const val HOME_BOOT = "home_on_boot"
    private const val DEFAULT_INTERCEPT = "com.amazon.tv.launcher/com.amazon.tv.launcher.ui.HomeActivity_vNext"
    private const val SPEED = "mouse_speed"
    private const val MOUSE_COLOR = "mouse_color"
    private const val SCROLL_SPEED = "scroll_speed"
    private const val HOME_DELAY = "home_delay_ms"
    private const val BLACK_ACTIVITY = "black_activity"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun mouseEnabled(context: Context): Boolean = prefs(context).getBoolean(MOUSE, false)
    fun dotEnabled(context: Context): Boolean = prefs(context).getBoolean(DOT, false)

    // auto | adb | accessibility
    fun backend(context: Context): String = prefs(context).getString(BACKEND, null) ?: "auto"

    fun setBackend(context: Context, backend: String) {
        prefs(context).edit().putString(BACKEND, backend).apply()
    }

    fun speed(context: Context): String = prefs(context).getString(SPEED, null) ?: "medium"

    fun setSpeed(context: Context, speed: String) {
        prefs(context).edit().putString(SPEED, speed).apply()
    }

    // slow | normal | fast: how far a scroll key drags, in pixels
    fun scrollSpeedName(context: Context): String = prefs(context).getString(SCROLL_SPEED, null) ?: "normal"

    fun setScrollSpeed(context: Context, name: String) {
        prefs(context).edit().putString(SCROLL_SPEED, name).apply()
    }

    fun scrollDistance(context: Context): Int = when (scrollSpeedName(context)) {
        "slow" -> 160
        "fast" -> 560
        else -> 320
    }

    fun blackActivity(context: Context): Boolean = prefs(context).getBoolean(BLACK_ACTIVITY, false)

    fun setBlackActivity(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(BLACK_ACTIVITY, on).apply()
    }

    // black-screen time between the intercepted home appearing and the redirect
    fun homeDelayMs(context: Context): Int = prefs(context).getInt(HOME_DELAY, 1000)

    fun setHomeDelayMs(context: Context, ms: Int) {
        prefs(context).edit().putInt(HOME_DELAY, ms).apply()
    }

    // white | yellow | cyan | green | red | magenta
    fun cursorColorName(context: Context): String = prefs(context).getString(MOUSE_COLOR, null) ?: "white"

    fun setCursorColor(context: Context, name: String) {
        prefs(context).edit().putString(MOUSE_COLOR, name).apply()
    }

    fun cursorColor(context: Context): Int = when (cursorColorName(context)) {
        "yellow" -> Color.YELLOW
        "cyan" -> Color.CYAN
        "green" -> Color.GREEN
        "red" -> Color.RED
        "magenta" -> Color.MAGENTA
        else -> Color.WHITE
    }

    fun speedFactor(context: Context): Float = when (speed(context)) {
        "slow" -> 0.4f
        "fast" -> 1f
        "faster" -> 1.4f
        else -> 0.65f
    }

    fun setMouse(context: Context, enabled: Boolean): Boolean = set(context, MOUSE, enabled)

    fun setDot(context: Context, enabled: Boolean): Boolean = set(context, DOT, enabled)

    fun enforcerEnabled(context: Context): Boolean = prefs(context).getBoolean(ENFORCER, true)

    fun homeRedirectEnabled(context: Context): Boolean = prefs(context).getBoolean(HOME_REDIRECT, false)

    // flattened component of the home activity to intercept (Amazon's by default)
    fun homeRedirectTarget(context: Context): String = prefs(context).getString(HOME_TARGET, null) ?: DEFAULT_INTERCEPT

    // flattened component of the launcher to open instead
    fun homeLaunchTarget(context: Context): String = prefs(context).getString(HOME_LAUNCH, null) ?: ""

    fun homeOnBoot(context: Context): Boolean = prefs(context).getBoolean(HOME_BOOT, false)

    fun setHomeOnBoot(context: Context, on: Boolean): Boolean {
        prefs(context).edit().putBoolean(HOME_BOOT, on).apply()
        return true
    }

    // the accessibility service is needed by the mouse, the dot and the home redirect
    fun wantsService(context: Context): Boolean = mouseEnabled(context) || dotEnabled(context) || homeRedirectEnabled(context)

    fun setHomeRedirect(context: Context, enabled: Boolean, intercept: String, launch: String): Boolean {
        prefs(context).edit()
            .putBoolean(HOME_REDIRECT, enabled)
            .putString(HOME_TARGET, intercept)
            .putString(HOME_LAUNCH, launch)
            .apply()
        val component = ComponentName(context, OverlayService::class.java).flattenToString()
        val ok = ServiceSettings.setAccessibilityEnabled(context, component, wantsService(context))
        KeepAlive.sync(context)
        return ok
    }


    fun setEnforcer(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(ENFORCER, enabled).apply()
    }

    // Blocking. The mouse and the dot both need the accessibility service.
    private fun set(context: Context, key: String, enabled: Boolean): Boolean {
        val previous = prefs(context).getBoolean(key, false)
        prefs(context).edit().putBoolean(key, enabled).apply()
        val component = ComponentName(context, OverlayService::class.java).flattenToString()
        val ok = ServiceSettings.setAccessibilityEnabled(context, component, wantsService(context))
        if (!ok) prefs(context).edit().putBoolean(key, previous).apply()
        KeepAlive.sync(context)
        return ok
    }
}

/**
 * Draws the VPN status dot and, when enabled, a remote-driven mouse: double-press Play toggles it,
 * D-pad moves the cursor (held = continuous), OK clicks, fast-forward / rewind scroll, Back leaves.
 */
class OverlayService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var holding = false
    private val moveInFlight = java.util.concurrent.atomic.AtomicBoolean(false)
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onPrefsChanged() }

    private var cursor: View? = null
    private var cursorParams: WindowManager.LayoutParams? = null
    private var cursorHalf = 0
    private var active = false
    private var x = 0
    private var y = 0
    private var maxX = 0
    private var maxY = 0
    private var lastPlay = 0L
    private var swallowPlayUp = false
    private val held = mutableSetOf<Int>()
    private var heldSince = 0L
    private var ticking = false

    private var dot: View? = null
    private var dotParams: WindowManager.LayoutParams? = null
    private var dotStart = 0L
    private var dotMargin = 0
    private var dotColorAt = 0L
    private var dotColorCache = 0
    private var dotRegistered = false
    private val vpnNetworks = mutableSetOf<Network>()

    private val vpnCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            handler.post {
                vpnNetworks.add(network)
                renderDot()
            }
        }

        override fun onLost(network: Network) {
            handler.post {
                vpnNetworks.remove(network)
                renderDot()
            }
        }
    }

    // Moves the fade. If the system has removed the window, rebuilds it.
    private val dotTick: Runnable = Runnable {
        if (dot != null) {
            if (updateDot()) {
                handler.postDelayed(dotTick, DOT_TICK_MS)
            } else {
                hideDot()
                showDot()
            }
        }
    }

    private val moveTick = object : Runnable {
        override fun run() {
            val heldMs = SystemClock.uptimeMillis() - heldSince
            if (!active || held.isEmpty() || heldMs > MAX_HOLD_MS) { // the cap guards against a lost key-up
                held.clear()
                ticking = false
                return
            }
            moveBy(scaled((BASE_STEP + heldMs / RAMP_MS_PER_PX).coerceAtMost(MAX_STEP.toLong()).toInt()))
            handler.postDelayed(this, TICK_MS)
        }
    }

    // Home redirect. A Home press on the stock home starts a pending redirect: black screen, then the launch app after
    // the delay. A press or a navigation during the delay keeps stock. A press on the custom launcher lets stock show.
    // A stock arrival after a recent key press also redirects. Logs and the broadcast only add a press on the stock
    // home that no key event reported.
    private var homeHeld = false
    private var homeKeysSeen = false
    private var lastHomeDownAt = 0L
    private var lastKeyPressAt = 0L
    private var lastPressMs = 0L
    private var redirectPending = false
    private var pendingSince = 0L
    private var cooldownUntil = 0L
    private var ignoreUntil = 0L
    private var curPkg: String? = null
    private var broadcastOn = false
    private var watcherOn = false
    @Volatile private var localOn = false
    private var localProcess: Process? = null

    private val homeCallback = HomeCallback { kind -> handler.post { onPress(kind) } }

    private val homeReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handler.post { onPress(1) }
        }
    }

    private fun registerHomeReceiver() {
        if (broadcastOn) return
        broadcastOn = runCatching {
            registerReceiver(homeReceiver, android.content.IntentFilter("com.amazon.tv.devicecontrol.HOME_PRESSED"))
        }.isSuccess
        android.util.Log.d("HomeRedirect", "home broadcast receiver ${if (broadcastOn) "on" else "failed"}")
    }

    private fun stockPkg(): String? = ComponentName.unflattenFromString(OverlayPrefs.homeRedirectTarget(this))?.packageName
    private fun launchPkg(): String? = ComponentName.unflattenFromString(OverlayPrefs.homeLaunchTarget(this))?.packageName

    private fun pendingActive(): Boolean =
        redirectPending && SystemClock.uptimeMillis() - pendingSince < OverlayPrefs.homeDelayMs(this) + 1_500L

    private fun stayOnStock(reason: String, forMs: Long = 0L) {
        android.util.Log.d("HomeRedirect", "stay on stock: $reason")
        redirectPending = false
        if (forMs > 0) ignoreUntil = SystemClock.uptimeMillis() + forMs
        handler.removeCallbacks(redirect)
        hideBlackout.run()
    }

    private fun startPending() {
        pendingSince = SystemClock.uptimeMillis()
        redirectPending = true
        android.util.Log.d("HomeRedirect", "pending redirect")
        showBlackout()
        handler.removeCallbacks(redirect)
        handler.postDelayed(redirect, OverlayPrefs.homeDelayMs(this).toLong())
    }

    private val redirect: Runnable = Runnable {
        redirectPending = false
        if (homeHeld) {
            hideBlackout.run()
            return@Runnable
        }
        val launch = ComponentName.unflattenFromString(OverlayPrefs.homeLaunchTarget(this))
        if (launch == null) {
            hideBlackout.run()
            toast("Home redirect: pick a launch app in Advanced")
            return@Runnable
        }
        cooldownUntil = SystemClock.uptimeMillis() + REDIRECT_COOLDOWN_MS
        android.util.Log.d("HomeRedirect", "redirecting to $launch")
        try {
            startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .setComponent(launch).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            hideBlackout.run()
            toast("Home redirect: can't open the launch app")
        }
    }

    // Key path: every Home press reaches here first.
    private fun onHomeKey(event: KeyEvent) {
        if (event.action == KeyEvent.ACTION_UP) {
            homeHeld = false
            return
        }
        if (event.action != KeyEvent.ACTION_DOWN) return
        // a long press is the launcher's own action
        if (event.repeatCount > 0) {
            stayOnStock("long press")
            return
        }
        val now = SystemClock.uptimeMillis()
        homeHeld = true
        homeKeysSeen = true
        lastHomeDownAt = now
        lastKeyPressAt = now
        android.util.Log.d("HomeRedirect", "home key down, curPkg=$curPkg")
        if (pendingActive()) {
            stayOnStock("second press")
            return
        }
        val launch = launchPkg()
        if (launch != null && curPkg == launch) {
            // the custom launcher is in front: the stock home shows and its arrival must not redirect back
            ignoreUntil = now + HOME_CAUSE_MS
            return
        }
        if (curPkg != null && curPkg == stockPkg()) startPending()
    }

    // Broadcast and log sources. Only counts when no key event came in, and only from the stock home.
    private fun onPress(kind: Int) {
        val now = SystemClock.uptimeMillis()
        if (now - lastKeyPressAt < 1000 || now - lastPressMs < 400) return
        lastPressMs = now
        if (!OverlayPrefs.homeRedirectEnabled(this) || kind == 2) return
        android.util.Log.d("HomeRedirect", "press from a non-key source, curPkg=$curPkg")
        if (pendingActive()) {
            stayOnStock("second press (source)")
            return
        }
        if (curPkg != null && curPkg == stockPkg()) startPending()
    }

    // Press sources for the log: READ_LOGS read in-process when granted, else the Shizuku shell's log.
    private fun syncHomeWatcher() {
        val enabled = OverlayPrefs.homeRedirectEnabled(this)
        val logs = checkSelfPermission("android.permission.READ_LOGS") == PackageManager.PERMISSION_GRANTED
        if (enabled && logs) startLocalWatcher() else stopLocalWatcher()
        val wantShell = enabled && !logs && ShizukuManager.isReady && ElevatedShellManager.isBound
        if (!wantShell) {
            if (watcherOn) {
                watcherOn = false
                io.execute { ElevatedShellManager.watchHome(null) }
            }
            return
        }
        if (!watcherOn) {
            watcherOn = true
            io.execute {
                val ok = ElevatedShellManager.watchHome(homeCallback)
                handler.post { watcherOn = ok }
            }
        }
    }

    private fun startLocalWatcher() {
        if (localOn) return
        localOn = true
        Thread {
            var process: Process? = null
            try {
                process = ProcessBuilder("/system/bin/logcat", "-v", "brief", "-T", "1", "-s", "FireTVKeyPolicyManager:V")
                    .redirectErrorStream(true)
                    .start()
                localProcess = process
                val started = SystemClock.uptimeMillis()
                val reader = process.inputStream.bufferedReader()
                while (localOn) {
                    val line = reader.readLine() ?: break
                    // the first lines are the log's backlog, not presses
                    if (SystemClock.uptimeMillis() - started < 1_500) continue
                    val kind = when {
                        line.contains("LongPressOnHome") -> 2
                        line.contains("ShortPressOnHome") -> 1
                        else -> 0
                    }
                    if (kind != 0) handler.post { onPress(kind) }
                }
            } catch (_: Throwable) {
            } finally {
                val finished = process
                finished?.destroy()
                handler.post {
                    if (localProcess === finished) {
                        localProcess = null
                        localOn = false
                    }
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun stopLocalWatcher() {
        localOn = false
        localProcess?.destroy()
        localProcess = null
    }

    private val watcherTick: Runnable = Runnable {
        syncHomeWatcher()
        handler.postDelayed(watcherTick, WATCHER_MS)
    }

    // Boot: the same pending redirect as a Home press.
    fun requestBootRedirect() = startPending()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString()
        val cls = event.className?.toString()
        val before = curPkg
        if (pkg != null && pkg != "com.android.systemui" && pkg != packageName) curPkg = pkg
        val now = SystemClock.uptimeMillis()
        val launch = launchPkg()
        // the launch app showing up means the redirect worked: drop the black screen
        if (blackout != null && pkg != null && pkg == launch && now < cooldownUntil) handler.postDelayed(hideBlackout, 250)
        if (!OverlayPrefs.homeRedirectEnabled(this)) return
        val target = ComponentName.unflattenFromString(OverlayPrefs.homeRedirectTarget(this)) ?: return
        // exact package and class only, so the stock home's own screens never match
        if (pkg != target.packageName || cls != target.className) {
            // navigating anywhere else cancels a pending redirect
            if (pkg != null && pendingActive() && now - pendingSince > 150 &&
                pkg != packageName && pkg != launch && pkg != "com.android.systemui"
            ) {
                stayOnStock("navigated to $pkg")
            }
            return
        }
        // while a redirect is pending, key presses decide; arrivals don't
        if (pendingActive()) return
        if (now < ignoreUntil) return
        // the stock home moving between its own screens is not a Home press
        if (before == target.packageName) return
        if (now < cooldownUntil) {
            cooldownUntil = 0
            stayOnStock("late second press", LONG_SUPPRESS_MS)
            return
        }
        if (homeKeysSeen && now - lastHomeDownAt > HOME_CAUSE_MS) return
        startPending()
    }

    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        overlayServiceInstance = this
        registerHomeReceiver()
        getSharedPreferences(OverlayPrefs.PREFS, Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(prefsListener)
        onPrefsChanged()
        handler.removeCallbacks(watcherTick)
        handler.post(watcherTick)
    }

    private fun onPrefsChanged() {
        if (OverlayPrefs.dotEnabled(this)) showDot() else hideDot()
        if (!OverlayPrefs.mouseEnabled(this)) setActive(false)
        syncHomeWatcher()
    }

    private fun windowManager(): WindowManager = getSystemService(WindowManager::class.java)

    // ---------------------------------------------------------------- mouse

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_HOME) {
            if (OverlayPrefs.homeRedirectEnabled(this)) onHomeKey(event)
            return false
        }
        if (OverlayPrefs.mouseEnabled(this) && (event.keyCode == KeyEvent.KEYCODE_MENU || event.keyCode == KeyEvent.KEYCODE_MEDIA_NEXT)) {
            // scroll keys replace their default actions while the mouse is on
            if (event.action == KeyEvent.ACTION_DOWN) scrollAt(if (event.keyCode == KeyEvent.KEYCODE_MENU) -1 else 1)
            return true
        }
        if (!OverlayPrefs.mouseEnabled(this)) return false
        val down = event.action == KeyEvent.ACTION_DOWN
        when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (!down) return swallowPlayUp.also { swallowPlayUp = false }
                if (event.repeatCount == 0) {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastPlay < DOUBLE_PRESS_MS) {
                        // the first press already reached the app; only the second is swallowed
                        lastPlay = 0
                        swallowPlayUp = true
                        setActive(!active)
                        return true
                    }
                    lastPlay = now
                }
                return false
            }
        }
        if (!active) return false
        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                direction(event.keyCode, down)
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (event.repeatCount == 0) {
                    if (down) pressDown() else pressUp()
                }
                true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                if (down && event.repeatCount == 0) scroll(-1)
                true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (down && event.repeatCount == 0) scroll(1)
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (!down) setActive(false)
                true
            }
            else -> false
        }
    }

    // Movement is driven by our own timer while a key is held, not by key-repeat events.
    private fun direction(code: Int, down: Boolean) {
        if (!down) {
            held.remove(code)
            return
        }
        if (code in held) return
        held.add(code)
        moveBy(scaled(BASE_STEP)) // so a quick tap still moves
        if (!ticking) {
            ticking = true
            heldSince = SystemClock.uptimeMillis()
            handler.postDelayed(moveTick, TICK_MS)
        }
    }

    private fun scaled(step: Int) = (step * OverlayPrefs.speedFactor(this)).toInt().coerceAtLeast(1)

    private fun moveBy(step: Int) {
        var dx = 0
        var dy = 0
        if (KeyEvent.KEYCODE_DPAD_LEFT in held) dx -= step
        if (KeyEvent.KEYCODE_DPAD_RIGHT in held) dx += step
        if (KeyEvent.KEYCODE_DPAD_UP in held) dy -= step
        if (KeyEvent.KEYCODE_DPAD_DOWN in held) dy += step
        if (dx == 0 && dy == 0) return
        x = (x + dx).coerceIn(0, maxX)
        y = (y + dy).coerceIn(0, maxY)
        val layout = cursorParams
        val view = cursor
        if (layout != null && view != null) {
            layout.x = x - cursorHalf
            layout.y = y - cursorHalf
            windowManager().updateViewLayout(view, layout)
        }
        if (holding) sendMove()
    }

    private fun setActive(on: Boolean) {
        if (on == active) return
        active = on
        held.clear()
        if (!on) pressUp()
        if (on) showCursor() else hideCursor()
        toast(getString(if (active) R.string.mouse_on else R.string.mouse_off))
    }

    // Redraws a visible cursor so a colour change shows at once
    fun refreshCursor() {
        if (cursor != null) {
            hideCursor()
            showCursor()
        }
    }

    private fun showCursor() {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager().defaultDisplay.getRealMetrics(metrics)
        maxX = metrics.widthPixels - 1
        maxY = metrics.heightPixels - 1
        if (x == 0 && y == 0) {
            x = maxX / 2
            y = maxY / 2
        }
        val size = (16 * metrics.density).toInt()
        cursorHalf = size / 2
        val tint = OverlayPrefs.cursorColor(this)
        val view = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(tint)
                setStroke((2 * metrics.density).toInt(), Color.BLACK)
            }
        }
        val layout = overlayParams(size)
        layout.gravity = Gravity.TOP or Gravity.START
        layout.x = x - cursorHalf
        layout.y = y - cursorHalf
        try {
            windowManager().addView(view, layout)
            cursor = view
            cursorParams = layout
        } catch (_: Exception) {
            active = false
        }
    }

    private fun hideCursor() {
        cursor?.let {
            try {
                windowManager().removeView(it)
            } catch (_: Exception) {
            }
        }
        cursor = null
        cursorParams = null
    }

    // Shizuku when it's connected, otherwise an accessibility gesture
    private fun press(viaShell: () -> ShellResult, viaGesture: () -> Unit) {
        if (!ShizukuManager.isReady) {
            viaGesture()
            return
        }
        io.execute { if (!viaShell().ok) handler.post { viaGesture() } }
    }

    private fun tap(px: Int, py: Int) =
        press({ Shell.tap(px, py) }) { gesture(Path().apply { moveTo(px.toFloat(), py.toFloat()) }, 50) }

    // With Shizuku, OK sends a real DOWN and UP, so holding OK holds the press while the cursor moves.
    // Without Shizuku a press is only a tap.
    // Swipe through the cursor point: +1 scrolls down (finger moves up), -1 scrolls up. Needs Shizuku.
    private fun scrollAt(direction: Int) {
        val half = OverlayPrefs.scrollDistance(this) / 2
        val fromY = (y + direction * half).coerceAtLeast(0)
        val toY = (y - direction * half).coerceAtLeast(0)
        val px = x
        io.execute {
            if (!Shell.scrollSwipe(px, fromY, px, toY).ok) handler.post { toast("Scroll failed: Shizuku isn't running") }
        }
    }

    // Full-screen black while the redirect is pending, so the stock launcher never shows.
    private var blackout: View? = null

    private val hideBlackout = Runnable {
        blackout?.let { runCatching { windowManager().removeView(it) } }
        blackout = null
        BlackActivity.dismiss()
    }

    private fun showBlackout() {
        if (OverlayPrefs.blackActivity(this)) {
            BlackActivity.show(this)
        } else if (blackout == null) {
            val view = View(this).apply { setBackgroundColor(Color.BLACK) }
            val params = android.view.WindowManager.LayoutParams(
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.OPAQUE
            )
            runCatching { windowManager().addView(view, params) }.onSuccess { blackout = view }
        }
        // safety net: the screen never stays black for long
        handler.removeCallbacks(hideBlackout)
        handler.postDelayed(hideBlackout, OverlayPrefs.homeDelayMs(this).toLong() + 2000L)
    }

    private fun pressDown() {
        if (!ShizukuManager.isReady) {
            tap(x, y)
            return
        }
        if (holding) return
        holding = true
        sendMotion("DOWN", x, y)
    }

    private fun pressUp() {
        if (!holding) return
        holding = false
        sendMotion("UP", x, y)
    }

    private fun sendMotion(action: String, px: Int, py: Int) {
        io.execute {
            if (!Shell.motion(action, px, py).ok) handler.post { toast("Press failed: Shizuku isn't running") }
        }
    }

    // One move is queued at a time, so the cursor never falls far behind the pointer.
    private fun sendMove() {
        if (!moveInFlight.compareAndSet(false, true)) return
        val px = x
        val py = y
        io.execute {
            try {
                Shell.motion("MOVE", px, py)
            } finally {
                moveInFlight.set(false)
            }
        }
    }

    // direction: 1 swipes down (content moves down), -1 swipes up
    private fun scroll(direction: Int) {
        val half = 200 * direction
        val from = (y - half).coerceIn(0, maxY)
        val to = (y + half).coerceIn(0, maxY)
        val column = x
        press({ Shell.swipe(column, from, column, to, 250) }) {
            gesture(
                Path().apply {
                    moveTo(column.toFloat(), from.toFloat())
                    lineTo(column.toFloat(), to.toFloat())
                },
                250
            )
        }
    }

    private fun gesture(path: Path, durationMs: Long) {
        dispatchGesture(
            GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, durationMs)).build(),
            object : GestureResultCallback() {
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    toast("Click failed: Shizuku isn't running, so clicks are cancelled")
                }
            },
            null
        )
    }

    // ---------------------------------------------------------------- cursor overlay

    private fun overlayParams(size: Int) = WindowManager.LayoutParams(
        size, size,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    )

    private fun showDot() {
        if (dot != null) return
        val density = resources.displayMetrics.density
        val size = (10 * density).toInt()
        val margin = (12 * density).toInt()
        val view = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(DOT_OFF)
            }
        }
        val params = overlayParams(size)
        params.gravity = Gravity.BOTTOM or Gravity.END
        params.x = margin
        dotMargin = margin
        params.y = margin
        try {
            windowManager().addView(view, params)
        } catch (_: Exception) {
            return
        }
        dot = view
        dotParams = params
        dotStart = SystemClock.uptimeMillis()
        // VPN networks lack NOT_VPN, which the builder adds by default
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        getSystemService(ConnectivityManager::class.java).registerNetworkCallback(request, vpnCallback)
        dotRegistered = true
        updateDot()
        renderDot()
        handler.removeCallbacks(dotTick)
        handler.postDelayed(dotTick, DOT_TICK_MS)
    }

    // Eases the dot between a visible floor and near-full brightness while it drifts. Position and alpha come from
    // elapsed time, so a late tick lands in the right place instead of slowing the motion.
    // False if the window is gone, so the caller rebuilds it.
    private fun updateDot(): Boolean {
        val view = dot ?: return false
        val params = dotParams ?: return false
        val now = SystemClock.uptimeMillis()
        val phase = ((now - dotStart) % DOT_CYCLE_MS).toFloat() / DOT_CYCLE_MS
        val ease = 0.5f - 0.5f * kotlin.math.cos(2.0 * Math.PI * phase).toFloat()
        if (now - dotColorAt > DOT_COLOR_MS) syncDotColor()
        params.alpha = 0.25f + 0.65f * ease
        params.x = dotMargin + (params.width * DOT_DRIFT * ease).toInt()
        return try {
            windowManager().updateViewLayout(view, params)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun dotColor(): Int {
        if (!hasInternet()) return DOT_WARN
        return if (vpnNetworks.isNotEmpty()) DOT_ON else DOT_OFF
    }

    // Validated internet on a network that isn't a VPN, so a tunnel left up with no link doesn't count.
    @Suppress("DEPRECATION")
    private fun hasInternet(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return true
        return cm.allNetworks.any { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@any false
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
    }

    private fun syncDotColor() {
        dotColorAt = SystemClock.uptimeMillis()
        val color = dotColor()
        if (color != dotColorCache) {
            dotColorCache = color
            (dot?.background as? GradientDrawable)?.setColor(color)
        }
    }

    private fun renderDot() {
        dotColorCache = 0
        syncDotColor()
    }

    private fun hideDot() {
        handler.removeCallbacks(dotTick)
        if (dotRegistered) {
            try {
                getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(vpnCallback)
            } catch (_: Exception) {
            }
            dotRegistered = false
        }
        dot?.let {
            try {
                windowManager().removeView(it)
            } catch (_: Exception) {
            }
        }
        dot = null
        dotParams = null
        vpnNetworks.clear()
    }

    private fun release() {
        getSharedPreferences(OverlayPrefs.PREFS, Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(prefsListener)
        handler.removeCallbacks(moveTick)
        hideDot()
        hideCursor()
        active = false
    }

    override fun onUnbind(intent: Intent?): Boolean {
        release()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (overlayServiceInstance === this) overlayServiceInstance = null
        runCatching { unregisterReceiver(homeReceiver) }
        broadcastOn = false
        handler.removeCallbacks(watcherTick)
        stopLocalWatcher()
        if (watcherOn) {
            watcherOn = false
            Thread { ElevatedShellManager.watchHome(null) }.start()
        }
        release()
        io.shutdown()
        super.onDestroy()
    }

    companion object {
        const val DOUBLE_PRESS_MS = 400L
        const val DOT_TICK_MS = 50L
        const val DOT_COLOR_MS = 500L
        const val DOT_DRIFT = 1.0f
        const val WATCHER_MS = 5_000L
        const val LATE_AUTH_MS = 350L
        const val LONG_PRESS_MS = 500L
        const val HOME_CAUSE_MS = 3_000L
        const val REDIRECT_COOLDOWN_MS = 2_500L
        const val DOUBLE_GUARD_MS = 150L
        const val DOUBLE_TAP_MS = 600L
        const val SKIP_ARRIVAL_MS = 3_000L
        const val DOUBLE_HOME_MS = 800L
        const val REDIRECT_DELAY_MS = 1_000L
        const val LONG_SUPPRESS_MS = 1_500L
        const val DOT_CYCLE_MS = 4_000L
        val DOT_ON = 0xFF8CF3A5.toInt()
        val DOT_OFF = 0xFFFF5A5A.toInt()
        val DOT_WARN = 0xFFFFC107.toInt()
        const val TICK_MS = 16L
        const val BASE_STEP = 10
        const val MAX_STEP = 56
        const val RAMP_MS_PER_PX = 20L // +1 px per tick for every 20 ms held
        const val MAX_HOLD_MS = 15_000L
        val CONNECTED = 0xFF8CF3A5.toInt()
        val DISCONNECTED = 0xFFFF5A5A.toInt()
    }
}

// The running accessibility service, for the boot receiver to hand off to.
@Volatile var overlayServiceInstance: OverlayService? = null
