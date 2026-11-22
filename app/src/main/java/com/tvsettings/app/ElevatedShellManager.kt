package com.tvsettings.app

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import rikka.shizuku.Shizuku

object ElevatedShellManager {
    private const val TAG = "ElevatedShellManager"

    private var binder: IBinder? = null
    private var connection: ServiceConnection? = null
    private var args: Shizuku.UserServiceArgs? = null

    val isBound: Boolean get() = binder != null

    var stateListener: (() -> Unit)? = null

    private val connectCallbacks = mutableListOf<() -> Unit>()
    private val disconnectCallbacks = mutableListOf<() -> Unit>()

    fun onConnected(callback: () -> Unit) {
        if (binder != null) callback() else connectCallbacks.add(callback)
    }

    fun bind(context: Context) {
        if (connection != null) return

        val name = ComponentName(context, ShellBinder::class.java)
        val conn = object : ServiceConnection {
            override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
                binder = service
                Log.i(TAG, "UserService connected")
                connectCallbacks.forEach { it() }
                connectCallbacks.clear()
                stateListener?.invoke()
            }

            override fun onServiceDisconnected(componentName: ComponentName) {
                binder = null
                connection = null
                args = null
                Log.i(TAG, "UserService disconnected")
                disconnectCallbacks.forEach { it() }
                disconnectCallbacks.clear()
                stateListener?.invoke()
            }
        }

        connection = conn
        val userServiceArgs = Shizuku.UserServiceArgs(name)
            .processNameSuffix("shell_service")
            .debuggable(false)
            .version(2)
        args = userServiceArgs
        Shizuku.bindUserService(userServiceArgs, conn)
        Log.i(TAG, "UserService binding…")
    }

    fun unbind() {
        val conn = connection ?: return
        val currentArgs = args ?: return
        try {
            Shizuku.unbindUserService(currentArgs, conn, false)
        } catch (e: Exception) {
            Log.w(TAG, "unbind: ${e.message}")
        }
        connection = null
        args = null
        binder = null
    }

    // doesn't talk to the (possibly dead) server
    fun reset() {
        connection = null
        args = null
        binder = null
    }

    // One line to the pointer injector (see PointerPipe): "ok" or "fail"; null when the shell service isn't bound.
    fun pointer(command: String): String? {
        val bound = binder ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeString(command)
            bound.transact(ShellBinder.TRANSACTION_POINTER, data, reply, 0)
            reply.readString()
        } catch (e: Exception) {
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    fun runCommand(command: String): String {
        val bound = binder ?: throw IllegalStateException("UserService not bound — call bind() and wait for onConnected first")
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeString(command)
            bound.transact(ShellBinder.TRANSACTION_RUN, data, reply, 0)
            reply.readString() ?: ""
        } finally {
            data.recycle()
            reply.recycle()
        }
    }
}

/** Receives Home presses from the log watcher in the shell service. */
