package com.tvsettings.app

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.service.notification.NotificationListenerService

// Notification access is what lets the widget see any app's media session. It posts no notifications.
class NowPlayingListener : NotificationListenerService() {
    private var controller: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())

    // Redraws once a second while something plays, so the time keeps moving
    private val tick = object : Runnable {
        override fun run() {
            // nothing is drawn while the screen is off, but the loop keeps going while something plays
            if (getSystemService(PowerManager::class.java).isInteractive) NowPlaying.tick(this@NowPlayingListener)
            if (controller?.playbackState?.state == PlaybackState.STATE_PLAYING) handler.postDelayed(this, 1000)
        }
    }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = NowPlaying.push(this@NowPlayingListener)
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            NowPlaying.push(this@NowPlayingListener)
            startTicking()
        }
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { attach(it) }

    override fun onListenerConnected() {
        val manager = getSystemService(MediaSessionManager::class.java)
        val component = ComponentName(this, NowPlayingListener::class.java)
        manager.addOnActiveSessionsChangedListener(sessionsListener, component)
        attach(manager.getActiveSessions(component))
    }

    override fun onListenerDisconnected() {
        handler.removeCallbacks(tick)
        // the manager holds this listener until it is removed, and with it this whole service object
        getSystemService(MediaSessionManager::class.java).removeOnActiveSessionsChangedListener(sessionsListener)
        controller?.unregisterCallback(callback)
        controller = null
    }

    // Follows the session that is playing (or the first one), and redraws the widget when it changes
    private fun attach(sessions: List<MediaController>?) {
        val chosen = sessions?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: sessions?.firstOrNull()
        if (chosen?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = chosen
            chosen?.registerCallback(callback)
        }
        NowPlaying.push(this)
        startTicking()
    }

    private fun startTicking() {
        handler.removeCallbacks(tick)
        handler.post(tick)
    }
}
