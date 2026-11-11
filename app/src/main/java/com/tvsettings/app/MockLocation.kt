package com.tvsettings.app

import android.os.Build
import android.app.AppOpsManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import java.util.Locale

object MockLocation {
    private const val PREFS = "tvsettings"
    private const val KEY_ENABLED = "mock_enabled"
    private const val KEY_LAT = "mock_lat"
    private const val KEY_LON = "mock_lon"
    private const val DEFAULT_LAT = "40.7128" // New York
    private const val DEFAULT_LON = "-74.0060"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)
    fun latitude(context: Context): String = prefs(context).getString(KEY_LAT, null) ?: DEFAULT_LAT
    fun longitude(context: Context): String = prefs(context).getString(KEY_LON, null) ?: DEFAULT_LON

    fun coordinates(context: Context): Pair<Double, Double> =
        (latitude(context).toDoubleOrNull() ?: 0.0) to (longitude(context).toDoubleOrNull() ?: 0.0)

    fun setCoordinates(context: Context, latitude: Double, longitude: Double) {
        prefs(context).edit().putString(KEY_LAT, latitude.toString()).putString(KEY_LON, longitude.toString()).apply()
    }

    @Suppress("DEPRECATION")
    private fun isMockApp(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow("android:mock_location", Process.myUid(), context.packageName)
        } else {
            ops.checkOpNoThrow("android:mock_location", Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private const val FINE = "android.permission.ACCESS_FINE_LOCATION"

    // Blocking. Grants the location permission through Shizuku if needed, re-applies the saved coordinates, waits for
    // a fix and describes the location the system resolves.
    fun applyAndResolve(context: Context): String {
        if (context.checkSelfPermission(FINE) != PackageManager.PERMISSION_GRANTED) {
            Shell.permission(context.packageName, FINE, true)
        }
        if (context.checkSelfPermission(FINE) != PackageManager.PERMISSION_GRANTED) return "Needs the location permission"
        if (isEnabled(context)) start(context)
        Thread.sleep(1500)
        val manager = context.getSystemService(LocationManager::class.java)
        val best = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull {
                try {
                    manager.getLastKnownLocation(it)
                } catch (_: Exception) {
                    null
                }
            }
            .maxByOrNull { it.time }
            ?: return "(no fix)"
        return String.format(Locale.US, "%.5f, %.5f  %s ±%.0f m", best.latitude, best.longitude, best.provider, best.accuracy)
    }

    // Last known fix from the system, without applying anything. Needs the location permission.
    fun currentLocation(context: Context): String {
        if (context.checkSelfPermission(FINE) != PackageManager.PERMISSION_GRANTED) return "needs the location permission"
        val manager = context.getSystemService(LocationManager::class.java)
        val fix = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull {
                try {
                    manager.getLastKnownLocation(it)
                } catch (_: Exception) {
                    null
                }
            }
            .maxByOrNull { it.time } ?: return "(no fix yet)"
        return String.format(Locale.US, "%.5f, %.5f  %s ±%.0f m", fix.latitude, fix.longitude, fix.provider, fix.accuracy)
    }

    // Blocking; needs Shizuku.
    fun grantLocation(context: Context): Boolean = Shell.permission(context.packageName, FINE, true).ok

    fun start(context: Context) {
        val intent = Intent(context, MockLocationService::class.java)
        if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
    }

    // Blocking. Enabling makes this app the mock-location app (an app-op) through Shizuku, then starts the service.
    fun setEnabled(context: Context, enabled: Boolean): Boolean {
        if (!enabled) {
            prefs(context).edit().putBoolean(KEY_ENABLED, false).apply()
            context.stopService(Intent(context, MockLocationService::class.java))
            return true
        }
        if (!isMockApp(context)) {
            Shell.appOpsSet(context.packageName, "android:mock_location", "allow")
            for (attempt in 0 until 5) {
                if (isMockApp(context)) break
                Thread.sleep(150)
            }
        }
        if (!isMockApp(context)) return false
        prefs(context).edit().putBoolean(KEY_ENABLED, true).apply()
        start(context)
        return true
    }
}

/** Feeds the saved coordinates to the GPS and network providers once a second. */
class MockLocationService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
    private var ready = false

    private val tick = object : Runnable {
        override fun run() {
            push()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        if (!ready) ready = setUpProviders()
        if (!ready) {
            stopSelf()
            return START_NOT_STICKY
        }
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    private fun setUpProviders(): Boolean {
        val manager = getSystemService(LocationManager::class.java)
        return try {
            providers.forEach { name ->
                try {
                    manager.removeTestProvider(name)
                } catch (_: Exception) {
                }
                manager.addTestProvider(
                    name, false, false, false, false, true, true, true, Criteria.POWER_LOW, Criteria.ACCURACY_FINE
                )
                manager.setTestProviderEnabled(name, true)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun push() {
        val (lat, lon) = MockLocation.coordinates(this)
        val manager = getSystemService(LocationManager::class.java)
        providers.forEach { name ->
            val location = Location(name).apply {
                latitude = lat
                longitude = lon
                altitude = 0.0
                accuracy = 5f
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
            try {
                manager.setTestProviderLocation(name, location)
            } catch (_: Exception) {
            }
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.mock_location_channel), NotificationManager.IMPORTANCE_MIN)
            )
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        return builder
            .setSmallIcon(R.drawable.ic_settings_gear)
            .setContentTitle(getString(R.string.mock_location_title))
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        val manager = getSystemService(LocationManager::class.java)
        providers.forEach {
            try {
                manager.removeTestProvider(it)
            } catch (_: Exception) {
            }
        }
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL_ID = "mock_location"
        const val NOTIFICATION_ID = 7022
    }
}
