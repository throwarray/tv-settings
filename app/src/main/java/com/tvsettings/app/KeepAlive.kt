package com.tvsettings.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings

/**
 * The enforcer service keeps the accessibility service enabled while the mouse or the dot is on, and only
 * while "Keep accessibility on" is enabled. A 60 s alarm backs it up.
 */
object KeepAlive {
    private const val INTERVAL_MS = 60_000L

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, KeepAliveReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun needed(context: Context) = OverlayPrefs.wantsService(context) || UsageWidget.hasWidgets(context)

    // only the accessibility features are enforced; a placed widget alone must not turn the service on
    fun enforcing(context: Context) = OverlayPrefs.wantsService(context) && OverlayPrefs.enforcerEnabled(context)

    fun sync(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val service = Intent(context, EnforcerService::class.java)
        if (needed(context)) {
            if (enforcing(context)) {
                alarms.setInexactRepeating(
                    AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + INTERVAL_MS, INTERVAL_MS, pending(context)
                )
            } else {
                alarms.cancel(pending(context))
            }
            try {
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service) else context.startService(service)
            } catch (_: Exception) {
                // background starts are restricted on newer Android; the alarm covers it
            }
        } else {
            alarms.cancel(pending(context))
            context.stopService(service)
        }
    }

    // Blocking. [force] writes the keys even when they look right; otherwise it only fixes what's out of step.
    fun enforce(context: Context, force: Boolean) {
        if (!enforcing(context)) return
        val component = ComponentName(context, OverlayService::class.java).flattenToString()
        if (force) ServiceSettings.writeAccessibility(context, component)
        else ServiceSettings.ensureAccessibility(context, component)
    }

    // Shell-only, so it needs Shizuku: WRITE_SECURE_SETTINGS can't change the idle whitelist.
    fun applyIdleWhitelist(context: Context, enabled: Boolean): Boolean =
        ShizukuManager.isReady && Shell.idleWhitelist(context.packageName, enabled).ok
}

/**
 * Foreground service behind the dot and the accessibility keep-alive. Watches the accessibility keys,
 * rewrites them every minute, and keeps the dot window in step with its setting.
 */
class EnforcerService : Service() {
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var observer: ContentObserver? = null
    private var lastWrite = 0L

    private val reactive: Runnable = Runnable { tick(force = false) }

    private val periodic: Runnable = Runnable {
        val now = SystemClock.uptimeMillis()
        val force = now - lastWrite >= WRITE_MS
        if (force) lastWrite = now
        tick(force)
        if (UsageWidget.hasWidgets(this)) UsageWidget.refresh(this)
        handler?.postDelayed(periodic, CHECK_MS)
    }

    private fun tick(force: Boolean) {
        if (!KeepAlive.needed(this)) {
            stopSelf()
            return
        }
        KeepAlive.enforce(this, force)
    }

    override fun onCreate() {
        super.onCreate()
        val t = HandlerThread("tvsettings-enforcer").apply { start() }
        val h = Handler(t.looper)
        thread = t
        handler = h
        // Reacts to changes by comparing only; writing here would re-trigger this observer.
        val o = object : ContentObserver(h) {
            override fun onChange(selfChange: Boolean) {
                h.removeCallbacks(reactive)
                h.postDelayed(reactive, DEBOUNCE_MS)
            }
        }
        observer = o
        contentResolver.registerContentObserver(Settings.Secure.getUriFor(KEY_LIST), false, o)
        contentResolver.registerContentObserver(Settings.Secure.getUriFor(KEY_FLAG), false, o)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        handler?.let {
            it.removeCallbacks(periodic)
            it.removeCallbacks(reactive)
            if (KeepAlive.enforcing(this)) it.post { KeepAlive.applyIdleWhitelist(this, true) }
            it.post(periodic)
        }
        return START_STICKY
    }

    private fun startForegroundCompat() {
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.enforcer_channel), NotificationManager.IMPORTANCE_MIN)
            )
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        val notification: Notification = builder
            .setSmallIcon(R.drawable.ic_settings_gear)
            .setContentTitle(getString(R.string.enforcer_title))
            .setOngoing(true)
            .setShowWhen(false)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        observer?.let { contentResolver.unregisterContentObserver(it) }
        observer = null
        handler?.removeCallbacksAndMessages(null)
        thread?.quitSafely()
        thread = null
        handler = null
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL_ID = "enforcer"
        const val NOTIFICATION_ID = 7023
        const val CHECK_MS = 5_000L
        const val WRITE_MS = 60_000L
        const val DEBOUNCE_MS = 200L
        const val KEY_LIST = "enabled_accessibility_services"
        const val KEY_FLAG = "accessibility_enabled"
    }
}

fun BroadcastReceiver.enforceAsync(context: Context) {
    val pending = goAsync()
    Thread {
        try {
            KeepAlive.enforce(context, force = true)
        } finally {
            pending.finish()
        }
    }.start()
}

class KeepAliveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = enforceAsync(context)
}

/** After boot or an update: mock location, the enforcer, and one forced write. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (MockLocation.isEnabled(context)) MockLocation.start(context)
        if (intent.action == android.content.Intent.ACTION_MY_PACKAGE_REPLACED) {
            // an update drops the accessibility binding; writing the saved setting again restores it
            OverlayPrefs.setHomeRedirect(
                context,
                OverlayPrefs.homeRedirectEnabled(context),
                OverlayPrefs.homeRedirectTarget(context),
                OverlayPrefs.homeLaunchTarget(context),
            )
        }
        if (intent.action == android.content.Intent.ACTION_BOOT_COMPLETED && OverlayPrefs.homeOnBoot(context)) {
            val live = overlayServiceInstance
            if (live != null) {
                runCatching { live.requestBootRedirect() }
            } else android.content.ComponentName.unflattenFromString(OverlayPrefs.homeLaunchTarget(context))?.let {
                context.startActivity(
                    android.content.Intent(android.content.Intent.ACTION_MAIN)
                        .addCategory(android.content.Intent.CATEGORY_HOME)
                        .setComponent(it)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
        KeepAlive.sync(context)
        enforceAsync(context)
    }
}
