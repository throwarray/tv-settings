package com.tvsettings.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.GestureResultCallback
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

// The running service, so boot can ask it for a redirect (see KeepAlive)
@Volatile
var overlayServiceInstance: OverlayService? = null

object OverlayPrefs {
    const val PREFS = "tvsettings"
    private const val DOT = "vpn_dot_enabled"
    private const val ENFORCER = "enforcer_enabled"
    private const val HOME_REDIRECT = "home_redirect"
    private const val HOME_TARGET = "home_redirect_target"
    private const val HOME_LAUNCH = "home_redirect_launch"
    private const val HOME_BOOT = "home_on_boot"
    private const val DEFAULT_INTERCEPT = "com.amazon.tv.launcher/com.amazon.tv.launcher.ui.HomeActivity_vNext"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun dotEnabled(context: Context): Boolean = prefs(context).getBoolean(DOT, false)

    // black-screen time between the intercepted home and the redirect: the remap double-press delay
    fun homeDelayMs(context: Context): Int = RemapEngine.doubleMs(context)

    fun setDot(context: Context, enabled: Boolean): Boolean = set(context, DOT, enabled)

    fun enforcerEnabled(context: Context): Boolean = prefs(context).getBoolean(ENFORCER, false)

    // Fallback for when the remap engine is down: on by default
    fun homeRedirectEnabled(context: Context): Boolean = prefs(context).getBoolean(HOME_REDIRECT, true)

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
    fun wantsService(context: Context): Boolean = dotEnabled(context) || homeRedirectEnabled(context) || mouseEnabled(context)

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

    private const val MOUSE = "mouse_enabled"
    private const val BACKEND = "mouse_backend"
    private const val SPEED = "mouse_speed"
    private const val MOUSE_COLOR = "mouse_color"
    private const val SCROLL_SPEED = "scroll_speed"
    fun mouseEnabled(context: Context): Boolean = prefs(context).getBoolean(MOUSE, false)

    // auto | remap | accessibility (an old "adb" setting means accessibility)
    fun backend(context: Context): String = when (prefs(context).getString(BACKEND, null)) {
        MouseBackend.REMAP -> MouseBackend.REMAP
        MouseBackend.ACCESSIBILITY, "adb" -> MouseBackend.ACCESSIBILITY
        else -> MouseBackend.AUTO
    }

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

    // The same speed in percent: the remap pointer's steps (set mouse_speed) and the cursor's step both use it
    fun speedPercent(context: Context): Int = when (speed(context)) {
        "slow" -> 40
        "fast" -> 100
        "faster" -> 140
        else -> 65
    }

    fun speedFactor(context: Context): Float = when (speed(context)) {
        "slow" -> 0.4f
        "fast" -> 1f
        "faster" -> 1.4f
        else -> 0.65f
    }

    fun setMouse(context: Context, enabled: Boolean): Boolean = set(context, MOUSE, enabled)
}

/**
 * Draws the VPN status dot and, when enabled, a remote-driven mouse: double-press Play toggles it,
 * D-pad moves the cursor (held = continuous), OK clicks, fast-forward / rewind scroll, Back leaves.
 */
class OverlayService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onPrefsChanged() }

    private var dot: View? = null
    private var dotParams: WindowManager.LayoutParams? = null
    private var dotStart = 0L
    private var dotMargin = 0
    private var dotColorAt = 0L
    private var dotColorCache = 0
    private var dotRegistered = false
    private val vpnNetworks = mutableSetOf<Network>()

    // accessibility mouse state
    private var active = false                 // cursor shown; keys drive it
    private var lastPlay = 0L                  // last Play press, for the double press
    private var swallowPlayUp = false          // the second Play press's key-up is eaten too
    private var x = 0
    private var y = 0
    private var maxX = 0
    private var maxY = 0
    private var cursorHalf = 0
    private var cursor: View? = null
    private var cursorParams: WindowManager.LayoutParams? = null
    private var holding = false                // OK is down (a real DOWN has been sent)
    private val held = mutableSetOf<Int>()     // D-pad keys currently down
    private var ticking = false
    private var heldSince = 0L
    private val moveInFlight = AtomicBoolean(false)

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
    // The home screens: whatever answers the Home intent, plus the stock and custom launcher chosen in Remapping
    private fun launcherPackages(): Set<String> = buildSet {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.queryIntentActivities(home, 0).forEach { add(it.activityInfo.packageName) }
        add(OverlayPrefs.homeLaunchTarget(this@OverlayService).substringBefore('/'))
        add(OverlayPrefs.homeRedirectTarget(this@OverlayService).substringBefore('/'))
    }

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
    override fun onKeyEvent(event: KeyEvent): Boolean {
        // remapd forwards the keys it doesn't map, so the cursor still gets the arrows and Play while it runs
        if (mouseKey(event)) return true
        if (event.keyCode == KeyEvent.KEYCODE_APP_SWITCH && RemapEngine.recents(this)) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                try {
                    startActivity(
                        Intent().setComponent(ComponentName.unflattenFromString(RemapEngine.RECENTS_TARGET))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (e: Exception) {
                    toast("Recents: can't open the launcher's recent apps")
                }
            }
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_HOME && OverlayPrefs.homeRedirectEnabled(this)) onHomeKey(event)
        return false
    }

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
        if (!OverlayPrefs.homeRedirectEnabled(this) || kind == 2 || RemapEngine.active(this)) return
        android.util.Log.d("HomeRedirect", "press from a non-key source, curPkg=$curPkg")
        if (pendingActive()) {
            stayOnStock("second press (source)")
            return
        }
        if (curPkg != null && curPkg == stockPkg()) startPending()
    }

    private var nextEnsure = 0L
    private var nextEnforce = 0L
    private var nextWidget = 0L

    // The engine comes back after a reboot, a crash or Shizuku starting late: check every 30 s
    private val watcherTick: Runnable = Runnable {
        if (SystemClock.uptimeMillis() >= nextEnsure) {
            nextEnsure = SystemClock.uptimeMillis() + 30_000
            val launch = OverlayPrefs.homeLaunchTarget(this)
            if (RemapEngine.enabled(this) && ShizukuManager.isReady) io.execute { RemapEngine.ensure(this, launch) }
        }
        // the accessibility keep-alive: rewrites the setting if it was turned off, once a minute
        if (SystemClock.uptimeMillis() >= nextEnforce) {
            nextEnforce = SystemClock.uptimeMillis() + 60_000
            io.execute { KeepAlive.enforce(this, false) }
        }
        if (SystemClock.uptimeMillis() >= nextWidget && getSystemService(android.os.PowerManager::class.java).isInteractive && UsageWidget.hasWidgets(this)) {
            nextWidget = SystemClock.uptimeMillis() + 5_000
            io.execute { UsageWidget.refresh(this) }
        }
        handler.postDelayed(watcherTick, WATCHER_MS)
    }

    // Boot: the same pending redirect as a Home press.
    fun requestBootRedirect() = startPending()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString()
        val cls = event.className?.toString()
        val before = curPkg
        // the installer, permission dialogs and file pickers take the screen for a moment: they never count as leaving an app
        if (pkg != null && pkg !in Doze.TRANSIENT_UI && pkg != packageName) curPkg = pkg
        // "close on exit": only when the app was left for the launcher (backing out or Home). Another app, a picker or a
        // dialog taking the screen is not an exit, so nothing is stopped while the user is still working in it.
        if (before != null && before != curPkg && Doze.isExit(this, before) && curPkg in launcherPackages()) {
            val left = before
            handler.postDelayed({ if (curPkg in launcherPackages()) io.execute { Shell.forceStop(left) } }, 1500)
        }
        val now = SystemClock.uptimeMillis()
        val launch = launchPkg()
        // the launch app showing up means the redirect worked: drop the black screen
        if (pkg != null && pkg == launch && now < cooldownUntil) handler.postDelayed(hideBlackout, 250)
        if (!OverlayPrefs.homeRedirectEnabled(this) || RemapEngine.active(this)) return
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
        io.execute { if (ShizukuManager.isReady) { Doze.migrate(this); NetBlock.reapply(this) } }
        getSharedPreferences(OverlayPrefs.PREFS, Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(prefsListener)
        onPrefsChanged()
        handler.removeCallbacks(watcherTick)
        handler.post(watcherTick)
    }

    private fun onPrefsChanged() {
        if (OverlayPrefs.dotEnabled(this)) showDot() else hideDot()
    }

    private fun windowManager(): WindowManager = getSystemService(WindowManager::class.java)

    // ---------------------------------------------------------------- mouse

    // Movement is driven by our own timer while a key is held, not by key-repeat events.

    // Redraws a visible cursor so a colour change shows at once

    // Shizuku when it's connected, otherwise an accessibility gesture

    // With Shizuku, OK sends a real DOWN and UP, so holding OK holds the press while the cursor moves.
    // Without Shizuku a press is only a tap.
    // Swipe through the cursor point: +1 scrolls down (finger moves up), -1 scrolls up. Needs Shizuku.

    // remapd's pointer lines, forwarded by the key injector: "P on", "P off", "P dx dy wheel button".

    // Full-screen black while the redirect is pending, so the stock launcher never shows.

    private val hideBlackout = Runnable {
        BlackActivity.dismiss()
    }

    private fun showBlackout() = BlackActivity.show(this)

    // One move is queued at a time, so the cursor never falls far behind the pointer.

    // direction: 1 swipes down (content moves down), -1 swipes up

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
        hideDot()
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
        release()
        io.shutdown()
        super.onDestroy()
    }

    companion object {
        const val DOT_TICK_MS = 50L
        const val DOT_COLOR_MS = 500L
        const val DOT_DRIFT = 1.0f
        const val WATCHER_MS = 5_000L
        const val HOME_CAUSE_MS = 3_000L
        const val REDIRECT_COOLDOWN_MS = 2_500L
        const val LONG_SUPPRESS_MS = 1_500L
        const val DOT_CYCLE_MS = 4_000L
        const val DOUBLE_PRESS_MS = 400L
        const val BASE_STEP = 12
        const val TICK_MS = 16L
        val DOT_ON = 0xFF8CF3A5.toInt()
        val DOT_OFF = 0xFFFF5A5A.toInt()
        val DOT_WARN = 0xFFFFC107.toInt()
    }

    // ---------------------------------------------------------------- mouse

    // the accessibility mouse: its keys come here only while the remap engine is not running
    private fun mouseKey(event: KeyEvent): Boolean {
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
    // Keeps moving while a D-pad key is held; the step grows the longer it is held
    private val moveTick: Runnable = Runnable {
        if (held.isEmpty()) {
            ticking = false
            return@Runnable
        }
        val heldFor = SystemClock.uptimeMillis() - heldSince
        val ramp = 1f + 1.5f * (heldFor / 1000f).coerceAtMost(1f)
        moveBy((scaled(BASE_STEP) * ramp).toInt().coerceAtLeast(1))
        handler.postDelayed(moveTick, TICK_MS)
    }

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
        val layout = mouseOverlayParams(size)
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
    private fun press(viaShell: () -> Boolean, viaGesture: () -> Unit) {
        if (!ShizukuManager.isReady) {
            viaGesture()
            return
        }
        io.execute { if (!viaShell()) handler.post { viaGesture() } }
    }

    private fun tap(px: Int, py: Int) =
        press({ PointerPipe.tap(this, px, py) || Shell.tap(px, py).ok }) { gesture(Path().apply { moveTo(px.toFloat(), py.toFloat()) }, 50) }

    // With Shizuku, OK sends a real DOWN and UP, so holding OK holds the press while the cursor moves.
    // Without Shizuku a press is only a tap.
    // Swipe through the cursor point: +1 scrolls down (finger moves up), -1 scrolls up. Needs Shizuku.
    private fun scrollAt(direction: Int) {
        val half = OverlayPrefs.scrollDistance(this) / 2
        val fromY = (y + direction * half).coerceAtLeast(0)
        val toY = (y - direction * half).coerceAtLeast(0)
        val px = x
        io.execute {
            if (!(PointerPipe.swipe(this, px, fromY, px, toY, 250) || Shell.swipe(px, fromY, px, toY, 250).ok)) {
                handler.post { toast("Scroll failed: Shizuku isn't running") }
            }
        }
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
            if (!(PointerPipe.motion(this, action, px, py) || Shell.motion(action, px, py).ok)) {
                handler.post { toast("Press failed: Shizuku isn't running") }
            }
        }
    }

    // One move is queued at a time, so the cursor never falls far behind the pointer.
    private fun sendMove() {
        if (!moveInFlight.compareAndSet(false, true)) return
        val px = x
        val py = y
        io.execute {
            try {
                PointerPipe.motion(this, "MOVE", px, py) || Shell.motion("MOVE", px, py).ok
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
        press({ PointerPipe.swipe(this, column, from, column, to, 250) || Shell.swipe(column, from, column, to, 250).ok }) {
            gesture(
                Path().apply {
                    moveTo(column.toFloat(), from.toFloat())
                    lineTo(column.toFloat(), to.toFloat())
                },
                250
            )
        }
    }



    private fun mouseOverlayParams(size: Int) = WindowManager.LayoutParams(
        size, size,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    )

}
