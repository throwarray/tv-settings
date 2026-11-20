package com.tvsettings.app

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import java.io.File

// APK and other downloads through the system's download manager: it runs in the background and can cancel and show progress.
object ApkDownloads {
    private const val PREFS = "downloads"

    class Progress(val done: Long, val total: Long, val running: Boolean)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun manager(context: Context) = context.getSystemService(DownloadManager::class.java)

    fun last(context: Context): Long = prefs(context).getLong("last", -1L)

    // A full link is used as it is. A Downloader code (digits) goes through its short link. Anything else is no download.
    fun resolve(input: String): String? {
        val s = input.trim()
        return when {
            s.matches(Regex("\\d{4,}")) -> "https://aftv.news/$s"
            s.startsWith("https://") || s.startsWith("http://") -> s
            else -> null
        }
    }

    fun defaultName(url: String): String = Uri.parse(url).lastPathSegment?.takeIf { it.isNotBlank() } ?: "download"

    // folder null: the public Downloads folder. Any file type: a zip or other archive is as likely as an apk.
    fun start(context: Context, url: String, folder: String?, name: String): Long {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(name)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        if (folder == null) request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
        else request.setDestinationUri(Uri.fromFile(File(folder, name)))
        val id = manager(context).enqueue(request)
        prefs(context).edit().putLong("last", id).apply()
        return id
    }

    // Progress of the last download, or null when there isn't one
    fun progress(context: Context): Progress? {
        val id = last(context)
        if (id < 0) return null
        val cursor = manager(context).query(DownloadManager.Query().setFilterById(id)) ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            val done = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val running = status == DownloadManager.STATUS_PENDING ||
                status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PAUSED
            return Progress(done, total, running)
        }
    }

    // Cancels the last download, or removes it if it has finished
    fun cancel(context: Context) {
        val id = last(context)
        if (id >= 0) manager(context).remove(id)
        prefs(context).edit().remove("last").apply()
    }

    // The folder the download goes to, in the system's files app
    fun openFolder(context: Context, folder: String?) {
        val path = folder ?: "/sdcard/Download"
        val relative = path.removePrefix("/sdcard/").removePrefix("/storage/emulated/0/").trim('/')
        val document = Uri.encode("primary:$relative")
        val uri = Uri.parse("content://com.android.externalstorage.documents/document/$document")
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "vnd.android.document/directory")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            openDownloads(context)
        }
    }

    // The download manager's list (not the folder); the generic action if this build doesn't have it
    fun openDownloads(context: Context) {
        val manager = Intent().setComponent(
            android.content.ComponentName("com.android.providers.downloads.ui", "com.android.providers.downloads.ui.DownloadList")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(manager)
        } catch (_: Exception) {
            context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
