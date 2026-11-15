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
    private const val DOT = "vpn_dot_enabled"
    private const val BACKEND = "mouse_backend"
    private const val ENFORCER = "enforcer_enabled"
    private const val HOME_REDIRECT = "home_redirect"
    private const val HOME_TARGET = "home_redirect_target"
    private const val HOME_LAUNCH = "home_redirect_launch"
    private const val HOME_BOOT = "home_on_boot"
    private const val DEFAULT_INTERCEPT = "com.amazon.tv.launcher/com.amazon.tv.launcher.ui.HomeActivity_vNext"
    private const val HOME_DELAY = "home_delay_ms"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun dotEnabled(context: Context): Boolean = prefs(context).getBoolean(DOT, false)

    // auto | adb | accessibility
    fun backend(context: Context): String = prefs(context).getString(BACKEND, null) ?: "auto"

    fun setBackend(context: Context, backend: String) {
        prefs(context).edit().putString(BACKEND, backend).apply()
    }



    // slow | normal | fast: how far a scroll key drags, in pixels





    // black-screen time between the intercepted home appearing and the redirect
    fun homeDelayMs(context: Context): Int = prefs(context).getInt(HOME_DELAY, 1000)

    fun setHomeDelayMs(context: Context, ms: Int) {
        prefs(context).edit().putInt(HOME_DELAY, ms).apply()
    }

    // white | yellow | cyan | green | red | magenta





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
    fun wantsService(context: Context): Boolean = dotEnabled(context) || homeRedirectEnabled(context)

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
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onPrefsChanged() }


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
        // the engine grabs the remote while it runs: these keys never reach here
        if (RemapEngine.active(this)) return false
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

    // The engine comes back after a reboot, a crash or Shizuku starting late: check every 30 s
    private val watcherTick: Runnable = Runnable {
        if (SystemClock.uptimeMillis() >= nextEnsure) {
            nextEnsure = SystemClock.uptimeMillis() + 30_000
            val launch = OverlayPrefs.homeLaunchTarget(this)
            if (RemapEngine.enabled(this) && ShizukuManager.isReady) io.execute { RemapEngine.ensure(this, launch) }
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
        if (pkg != null && pkg != "com.android.systemui" && pkg != packageName) curPkg = pkg
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
