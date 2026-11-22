package com.tvsettings.app

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Network, VPN and Bluetooth state. The network callbacks update it at once; a slow poll backs them up. */
class DeviceStatus(val network: Boolean, val vpn: Boolean, val bluetooth: Boolean)

fun readDeviceStatus(context: Context): DeviceStatus {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
    val network = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    val vpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    val bluetooth = runCatching { BluetoothAdapter.getDefaultAdapter()?.isEnabled == true }.getOrDefault(false)
    return DeviceStatus(network, vpn, bluetooth)
}

@Composable
fun StatusBar(context: Context) {
    var status by remember { mutableStateOf(readDeviceStatus(context)) }
    DisposableEffect(Unit) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val main = Handler(Looper.getMainLooper())
        val refresh: () -> Unit = { main.post { status = readDeviceStatus(context) } }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { refresh() }
            override fun onLost(network: Network) { refresh() }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { refresh() }
        }
        runCatching { cm?.registerNetworkCallback(NetworkRequest.Builder().build(), callback) }
        onDispose { runCatching { cm?.unregisterNetworkCallback(callback) } }
    }
    LaunchedEffect(Unit) {
        while (true) {
            status = readDeviceStatus(context)
            delay(5_000)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusIcon(AppIcons.Network, status.network, "Network")
        Spacer(modifier = Modifier.width(8.dp))
        StatusIcon(AppIcons.Vpn, status.vpn, "VPN")
        Spacer(modifier = Modifier.width(8.dp))
        StatusIcon(AppIcons.Bluetooth, status.bluetooth, "Bluetooth")
        Spacer(modifier = Modifier.width(10.dp))
    }
}

// A disconnected icon is dimmed and crossed out, so the state reads at a glance on a TV
@Composable
private fun StatusIcon(icon: ImageVector, on: Boolean, label: String) {
    Box(modifier = Modifier.size(16.dp)) {
        Icon(
            icon, contentDescription = label,
            tint = if (on) Oled.textPrimary else Oled.textSecondary.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxSize(),
        )
        if (!on) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawLine(
                    color = Oled.textSecondary,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, 0f),
                    strokeWidth = 2f,
                )
            }
        }
    }
}
