package com.tvsettings.app

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.provider.OpenableColumns

// Installs through PackageInstaller under REQUEST_INSTALL_PACKAGES. Shizuku can grant that app-op once.
object ApkInstaller {
    fun prepareAndInstall(context: Context, uri: Uri): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls() && ShizukuManager.isReady) {
            Shell.appOpsSet(context.packageName, "REQUEST_INSTALL_PACKAGES", "allow")
        }
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val input = context.contentResolver.openInputStream(uri) ?: return false
        input.use { apkInput ->
            val pi = context.packageManager.packageInstaller
            val id = pi.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
            pi.openSession(id).use { session ->
                session.openWrite("base.apk", 0, -1).use { out ->
                    apkInput.copyTo(out)
                    session.fsync(out)
                }
                val result = Intent(context, InstallResultReceiver::class.java)
                val pending = PendingIntent.getBroadcast(
                    context, id, result, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(pending.intentSender)
            }
        }
        return true
    }

    fun displayName(context: Context, uri: Uri): String = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment ?: context.getString(R.string.choose_apk_title)
    } catch (_: Exception) {
        uri.lastPathSegment ?: context.getString(R.string.choose_apk_title)
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> context.toast("Installed")
            else -> context.toast("Install failed: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
        }
    }
}

// The document picker grants temporary read access to the selected APK, independent of Shizuku.
fun ApkInstaller.installFromUri(context: Context, uri: Uri): Boolean = try {
    prepareAndInstall(context, uri)
} catch (_: Throwable) {
    false
}
