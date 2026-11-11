package com.tvsettings.app

import android.app.Application
import rikka.shizuku.ShizukuProvider

class TVSettingsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ShizukuProvider.enableMultiProcessSupport(true)
        ShizukuManager.init(this)
        KeepAlive.sync(this)
    }
}
