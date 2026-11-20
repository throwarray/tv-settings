package com.tvsettings.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.SystemClock
import android.widget.RemoteViews
import java.net.URL

// Any app's media session, found through notification access: play/pause, skip, 30 s back and forward, art and title.
// A widget can't hold a draggable scrubber, so the progress is shown and the 30 s buttons seek.
object NowPlaying {
    const val ACTION = "com.tvsettings.app.NOWPLAYING."
    const val PLAY_PAUSE = ACTION + "PLAY_PAUSE"
    const val NEXT = ACTION + "NEXT"
    const val PREVIOUS = ACTION + "PREVIOUS"
    const val BACK30 = ACTION + "BACK30"
    const val FORWARD30 = ACTION + "FORWARD30"

    private const val ART_SIZE = 144

    private var artUri: String? = null
    private var artBitmap: Bitmap? = null

    private fun component(context: Context) = ComponentName(context, NowPlayingListener::class.java)

    fun active(context: Context): MediaController? {
        val sessions = runCatching {
            context.getSystemService(MediaSessionManager::class.java).getActiveSessions(component(context))
        }.getOrNull() ?: return null
        return sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: sessions.firstOrNull()
    }

    // The position moves on while playing: extrapolated from the last report the app made
    fun position(state: PlaybackState?): Long {
        if (state == null) return 0L
        if (state.state != PlaybackState.STATE_PLAYING) return state.position
        val elapsed = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
        return state.position + (elapsed * state.playbackSpeed).toLong()
    }

    fun command(context: Context, action: String) {
        val controller = active(context) ?: return
        val controls = controller.transportControls
        val state = controller.playbackState
        val now = position(state)
        when (action) {
            PLAY_PAUSE -> if (state?.state == PlaybackState.STATE_PLAYING) controls.pause() else controls.play()
            NEXT -> controls.skipToNext()
            PREVIOUS -> controls.skipToPrevious()
            BACK30 -> controls.seekTo((now - 30_000L).coerceAtLeast(0L))
            FORWARD30 -> controls.seekTo(now + 30_000L)
        }
    }

    // Redraws every placed widget
    fun push(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, NowPlayingWidget::class.java))
        if (ids.isNotEmpty()) manager.updateAppWidget(ids, build(context))
    }

    fun build(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.nowplaying_widget)
        val controller = active(context)
        val metadata = controller?.metadata
        views.setTextViewText(
            R.id.np_title,
            metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: context.getString(R.string.np_nothing),
        )
        val art = artFor(context, metadata)
        if (art != null) views.setImageViewBitmap(R.id.np_art, art)
        else views.setImageViewResource(R.id.np_art, android.R.drawable.ic_media_play)
        fillProgress(views, controller)

        views.setOnClickPendingIntent(R.id.np_back30, pending(context, BACK30))
        views.setOnClickPendingIntent(R.id.np_previous, pending(context, PREVIOUS))
        views.setOnClickPendingIntent(R.id.np_play, pending(context, PLAY_PAUSE))
        views.setOnClickPendingIntent(R.id.np_next, pending(context, NEXT))
        views.setOnClickPendingIntent(R.id.np_fwd30, pending(context, FORWARD30))
        return views
    }

    // The play icon, the time and the progress: the only parts that change while a track plays
    private fun fillProgress(views: RemoteViews, controller: MediaController?) {
        val state = controller?.playbackState
        views.setImageViewResource(
            R.id.np_play,
            if (state?.state == PlaybackState.STATE_PLAYING) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
        )
        val duration = controller?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val position = position(state)
        val percent = if (duration > 0) ((position * 100) / duration).toInt().coerceIn(0, 100) else 0
        views.setProgressBar(R.id.np_progress, 100, percent, false)
        views.setTextViewText(R.id.np_time, "${clock(position)} / ${clock(duration)}")
    }

    // The per-second redraw: only fillProgress's parts are sent, so the art isn't sent again every second
    fun tick(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, NowPlayingWidget::class.java))
        if (ids.isEmpty()) return
        val views = RemoteViews(context.packageName, R.layout.nowplaying_widget)
        fillProgress(views, active(context))
        manager.partiallyUpdateAppWidget(ids, views)
    }

    // Art comes as a bitmap, or as a URI loaded in the background; either way it is scaled down to fit a widget
    private fun artFor(context: Context, metadata: MediaMetadata?): Bitmap? {
        metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)?.let { return scaled(it) }
        metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)?.let { return scaled(it) }
        val uri = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: return null
        if (uri == artUri) return artBitmap
        artUri = uri
        artBitmap = null
        Thread {
            val loaded = runCatching {
                val parsed = Uri.parse(uri)
                val stream = if (parsed.scheme == "content") context.contentResolver.openInputStream(parsed)
                else URL(uri).openStream()
                stream?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
            artBitmap = loaded?.let { scaled(it) }
            push(context)
        }.start()
        return null
    }

    private var scaledFrom: Bitmap? = null
    private var scaledTo: Bitmap? = null

    // Scaled once per source bitmap: a redraw must not allocate a new bitmap every time
    @Synchronized
    private fun scaled(bitmap: Bitmap): Bitmap {
        if (bitmap === scaledFrom) scaledTo?.let { return it }
        val result = Bitmap.createScaledBitmap(bitmap, ART_SIZE, ART_SIZE, true)
        scaledFrom = bitmap
        scaledTo = result
        return result
    }

    private fun clock(ms: Long): String {
        val seconds = ms / 1000
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }

    private fun pending(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        action.hashCode(),
        Intent(action).setComponent(ComponentName(context, NowPlayingWidget::class.java)),
        PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
