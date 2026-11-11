package com.tvsettings.app

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object AppManifest {
    // The declarations from the installed package: what the manifest says, without decoding the XML.
    fun describe(context: Context, pkg: String): String = try {
        val pm = context.packageManager
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS or PackageManager.GET_PERMISSIONS
        val info = pm.getPackageInfo(pkg, flags)
        val app = info.applicationInfo
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        val sb = StringBuilder()
        sb.append("package: ").append(pkg).append('\n')
        sb.append("version: ").append(info.versionName ?: "0").append(" (").append(code).append(")\n")
        sb.append("minSdk: ").append(app.minSdkVersion).append(", targetSdk: ").append(app.targetSdkVersion).append('\n')
        info.sharedUserId?.let { sb.append("sharedUserId: ").append(it).append('\n') }
        sb.append("\nuses-permission:\n")
        info.requestedPermissions?.forEach { sb.append("  ").append(it).append('\n') }
        fun section(title: String, items: Array<out android.content.pm.ComponentInfo>?, exported: (android.content.pm.ComponentInfo) -> Boolean) {
            sb.append("\n").append(title).append(":\n")
            items?.forEach { c ->
                sb.append("  ").append(c.name)
                if (!exported(c)) sb.append("  (not exported)")
                sb.append('\n')
            }
        }
        section("activity", info.activities) { it.exported }
        section("service", info.services) { it.exported }
        section("receiver", info.receivers) { it.exported }
        section("provider", info.providers) { it.exported }
        sb.toString()
    } catch (e: Exception) {
        "Couldn't read the manifest: ${e.message}"
    }
}

object AppBackup {
    // Copies the installed base APK to Downloads as <package>_v<versionCode>.apk. Needs Shizuku.
    fun run(context: Context, pkg: String, dir: String): Pair<Boolean, String> {
        if (!Regex("[A-Za-z0-9_./ -]+").matches(dir)) return false to "bad folder"
        if (!Regex("[A-Za-z0-9_.]+").matches(pkg)) return false to "bad package name"
        val pm = context.packageManager
        val info = pm.getPackageInfo(pkg, 0)
        val src = pm.getApplicationInfo(pkg, 0).sourceDir
        if (!Regex("[A-Za-z0-9_./-]+").matches(src)) return false to "unexpected path"
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        val dest = "$dir/${pkg}_v$code.apk"
        val ok = Shell.exec("cp '$src' '$dest'").ok
        return ok to dest
    }
}
