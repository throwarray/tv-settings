package com.tvsettings.app

import android.app.ActivityManager
import android.app.Application
import android.os.Process
import android.os.SystemClock
import rikka.shizuku.ShizukuProvider

class TVSettingsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ShizukuProvider.enableMultiProcessSupport(true)
        ShizukuManager.init(this)
        KeepAlive.sync(this)
        if (isMainProcess()) Thread({ restartRemapAtStart() }, "remapd-start").start()
    }

    // remapd runs under Shizuku's shell from an earlier build of this APK unless it is restarted: do that once per app start
    private fun restartRemapAtStart() {
        val deadline = SystemClock.uptimeMillis() + 60_000
        while (!ShizukuManager.isReady && SystemClock.uptimeMillis() < deadline) Thread.sleep(500)
        if (ShizukuManager.isReady) RemapEngine.restartIfEnabled(this)
    }

    // The shell service runs in its own process and also starts this class: only the app's own process restarts remapd
    private fun isMainProcess(): Boolean {
        val pid = Process.myPid()
        val running = getSystemService(ActivityManager::class.java)?.runningAppProcesses ?: return true
        return running.firstOrNull { it.pid == pid }?.processName?.let { it == packageName } ?: true
    }
}
