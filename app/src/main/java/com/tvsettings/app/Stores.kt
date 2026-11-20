package com.tvsettings.app

import android.content.Context
import android.content.Intent
import android.net.Uri

// Opens an app in the store that installed it, through the standard intents. Without a known link,
// the installer app itself is launched.
object Stores {
    fun open(context: Context, pkg: String): Boolean {
        val pm = context.packageManager
        val installer = runCatching { pm.getInstallerPackageName(pkg) }.getOrNull()
        val links = buildList {
            when (installer) {
                "com.amazon.venezia" -> add(Uri.parse("amzn://apps/android?p=$pkg"))
                "org.fdroid.fdroid" -> add(Uri.parse("https://f-droid.org/packages/$pkg/"))
            }
            add(Uri.parse("market://details?id=$pkg"))
        }
        for (uri in links) {
            val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(pm) != null) {
                context.startActivity(intent)
                return true
            }
        }
        val launch = installer?.let { pm.getLaunchIntentForPackage(it) } ?: return false
        context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
}
