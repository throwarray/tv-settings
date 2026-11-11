package com.tvsettings.app

import android.os.Build
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.provider.Settings
import java.util.UUID

data class PrivateDns(val mode: String, val host: String)

object SystemSettings {
    val GLOBAL_KEYS = listOf(
        "adb_enabled", "adb_wifi_enabled", "install_non_market_apps",
        "wifi_connected_mac_randomization_enabled", "private_dns_mode", "private_dns_specifier",
    )

    private const val MAC_KEY = "wifi_connected_mac_randomization_enabled"

    val SYSTEM_KEYS = listOf("accelerometer_rotation", "user_rotation")

    val PRIVATE_DNS_PRESETS = listOf(
        Triple("cloudflare", "Cloudflare", "one.one.one.one"),
        Triple("google", "Google", "dns.google"),
        Triple("adguard", "AdGuard", "dns.adguard-dns.com"),
        Triple("quad9", "Quad9", "dns.quad9.net"),
    )

    // Direct write when this app holds WRITE_SECURE_SETTINGS, otherwise through Shizuku.
    private fun put(context: Context, table: String, key: String, value: String): Boolean {
        val granted = context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") ==
            PackageManager.PERMISSION_GRANTED
        if (granted && table != "system") { // System writes need WRITE_SETTINGS, so they go through the shell
            try {
                val resolver = context.contentResolver
                val ok = if (table == "global") {
                    Settings.Global.putString(resolver, key, value)
                } else {
                    Settings.Secure.putString(resolver, key, value)
                }
                if (ok) return true
            } catch (_: SecurityException) {
            }
        }
        return Shell.settingsPut(table, key, value).ok
    }

    // Direct read, falling back to the shell for keys apps can't read on newer Android. null = unset.
    private fun settingValue(context: Context, table: String, key: String): String? {
        val resolver = context.contentResolver
        val direct = try {
            when (table) {
                "global" -> Settings.Global.getString(resolver, key)
                "system" -> Settings.System.getString(resolver, key)
                else -> Settings.Secure.getString(resolver, key)
            }
        } catch (_: SecurityException) {
            null
        }
        if (direct != null) return direct
        return Shell.settingsGet(table, key)
    }

    private fun globalFlag(context: Context, key: String): Boolean = settingValue(context, "global", key) == "1"

    fun locationEnabled(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 28) {
            context.getSystemService(LocationManager::class.java)?.isLocationEnabled ?: false
        } else {
            Settings.Secure.getInt(context.contentResolver, "location_mode", 0) != 0
        }

    fun setLocationEnabled(context: Context, enabled: Boolean): Boolean {
        if (Shell.setLocationEnabled(enabled).ok) return true
        return put(context, "secure", "location_mode", if (enabled) "3" else "0")
    }

    fun rotationForced(context: Context): Boolean = settingValue(context, "system", "accelerometer_rotation") == "0"

    fun userRotation(context: Context): Int = settingValue(context, "system", "user_rotation")?.toIntOrNull() ?: 0

    // Both routes: the older settings and the newer `wm user-rotation`. rotation is 0..3 (0, 90, 180, 270 degrees).
    fun setRotation(context: Context, forced: Boolean, rotation: Int): Boolean {
        val r = rotation.coerceIn(0, 3)
        val ok = put(context, "system", "user_rotation", r.toString()) &&
            put(context, "system", "accelerometer_rotation", if (forced) "0" else "1")
        if (forced) Shell.rotationLock(r) else Shell.rotationFree()
        return ok
    }

    fun adbDebugging(context: Context): Boolean = globalFlag(context, "adb_enabled")

    fun setAdbDebugging(context: Context, enabled: Boolean): Boolean =
        put(context, "global", "adb_enabled", if (enabled) "1" else "0")

    // Only offered when the device exposes the flag.
    fun macRandomizationSupported(context: Context): Boolean = settingValue(context, "global", MAC_KEY) != null

    fun macRandomization(context: Context): Boolean = globalFlag(context, MAC_KEY)

    fun setMacRandomization(context: Context, enabled: Boolean): Boolean =
        put(context, "global", MAC_KEY, if (enabled) "1" else "0")

    // Unset means Automatic, which is Android's default.
    fun privateDns(context: Context): PrivateDns = PrivateDns(
        settingValue(context, "global", "private_dns_mode") ?: "opportunistic",
        settingValue(context, "global", "private_dns_specifier") ?: "",
    )

    // mode: off | opportunistic | hostname (needs [host])
    fun setPrivateDns(context: Context, mode: String, host: String = ""): Boolean {
        if (mode == "hostname") {
            if (!Regex("[A-Za-z0-9.-]+").matches(host) || '.' !in host) return false
            if (!put(context, "global", "private_dns_specifier", host)) return false
        }
        return put(context, "global", "private_dns_mode", mode)
    }

    private const val AD_ID = "advertising_id"
    private const val AD_ID_APP = "advertisingIdApp:advertisingId:value"
    private const val AD_ID_SYNC = "advertisingIdApp:advertisingId:syncPending"

    fun advertisingId(context: Context): String? = settingValue(context, "secure", AD_ID)

    fun advertisingDetails(context: Context): String =
        "${settingValue(context, "secure", AD_ID_APP) ?: "(none)"} · sync pending ${settingValue(context, "secure", AD_ID_SYNC) ?: "(none)"}"

    // Fire OS keeps the ID twice (advertising_id and the app's copy) plus a sync flag, so all three are updated.
    fun resetAdvertisingId(context: Context): Boolean {
        val id = UUID.randomUUID().toString()
        var ok = put(context, "secure", AD_ID, id)
        if (settingValue(context, "secure", AD_ID_APP) != null) ok = put(context, "secure", AD_ID_APP, id) && ok
        settingValue(context, "secure", AD_ID_SYNC)?.let { current -> // mirror the stored style (true/false or 1/0)
            ok = put(context, "secure", AD_ID_SYNC, if (current == "0" || current == "1") "1" else "true") && ok
        }
        return ok
    }

    private fun wifi(context: Context): WifiManager =
        context.applicationContext.getSystemService(WifiManager::class.java)

    @Suppress("DEPRECATION")
    fun wifiEnabled(context: Context): Boolean = wifi(context).isWifiEnabled

    // Shell when Shizuku is up (works on every Android version); otherwise the WifiManager call.
    @Suppress("DEPRECATION")
    fun setWifi(context: Context, enabled: Boolean): Boolean {
        if (ShizukuManager.isReady) return Shell.exec("svc wifi ${if (enabled) "enable" else "disable"}").ok
        return wifi(context).setWifiEnabled(enabled)
    }

    // location_mode: 0 off, 1 GPS only, 2 Wi-Fi and network, 3 high accuracy
    fun locationMode(context: Context): Int = Settings.Secure.getInt(context.contentResolver, "location_mode", 0)

    fun setLocationMode(context: Context, mode: Int): Boolean = put(context, "secure", "location_mode", mode.toString())

    fun wirelessDebugging(context: Context): Boolean = globalFlag(context, "adb_wifi_enabled")

    fun setWirelessDebugging(context: Context, enabled: Boolean): Boolean =
        put(context, "global", "adb_wifi_enabled", if (enabled) "1" else "0")

    fun unknownSources(context: Context): Boolean = globalFlag(context, "install_non_market_apps")

    fun setUnknownSources(context: Context, enabled: Boolean): Boolean =
        put(context, "global", "install_non_market_apps", if (enabled) "1" else "0")
}
