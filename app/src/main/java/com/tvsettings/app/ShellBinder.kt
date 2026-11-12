package com.tvsettings.app

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

class ShellBinder : Binder() {
    companion object {
        private const val TAG = "ShellBinder"
        const val TRANSACTION_RUN = IBinder.FIRST_CALL_TRANSACTION
        const val TRANSACTION_WATCH_HOME = IBinder.FIRST_CALL_TRANSACTION + 1
        const val CALLBACK_PRESS = IBinder.FIRST_CALL_TRANSACTION
    }

    private var watchProcess: Process? = null
    private var generation = 0

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == TRANSACTION_RUN) {
            val command = data.readString() ?: ""
            val output = runCommand(command)
            reply?.writeString(output)
            return true
        }
        if (code == TRANSACTION_WATCH_HOME) {
            val callback: IBinder? = data.readStrongBinder()
            watchHome(callback)
            reply?.writeInt(1)
            return true
        }
        return super.onTransact(code, data, reply, flags)
    }

    fun runCommand(command: String): String {
        return try {
            val wrapped = "( $command ) 2>&1; status=\$?; printf '\\n__EXIT_CODE__:%s\\n' \"\$status\""
            val process = ProcessBuilder("sh", "-c", "export PATH=/system/bin:/system/xbin:\$PATH; " + wrapped)
                .redirectErrorStream(true)
                .start()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append('\n')
            }

            process.waitFor()
            output.toString()
        } catch (e: Throwable) {
            Log.e(TAG, "runCommand failed: ${e.message}", e)
            "exec failed: ${e.message ?: "unknown error"}"
        }
    }

    // Streams Amazon's key policy log and tells the callback about each Home press (1 short, 2 long).
    // A null callback stops it. Runs in this service's process, so it only needs the shell's log access.
    @Synchronized
    private fun watchHome(callback: IBinder?) {
        watchProcess?.destroy()
        watchProcess = null
        val gen = ++generation
        if (callback == null) return
        Thread {
            var process: Process? = null
            try {
                process = ProcessBuilder("sh", "-c", "exec logcat -v brief -T 1 -s FireTVKeyPolicyManager:V")
                    .redirectErrorStream(true)
                    .start()
                synchronized(this@ShellBinder) {
                    if (gen != generation) {
                        process.destroy()
                        return@Thread
                    }
                    watchProcess = process
                }
                val started = SystemClock.uptimeMillis()
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (gen != generation) break
                    val text = line ?: continue
                    // the first line is the log's backlog, not a press
                    if (SystemClock.uptimeMillis() - started < 1_500) continue
                    val kind = when {
                        text.contains("LongPressOnHome") -> 2
                        text.contains("ShortPressOnHome") -> 1
                        else -> 0
                    }
                    if (kind == 0) continue
                    val data = Parcel.obtain()
                    try {
                        data.writeInt(kind)
                        callback.transact(CALLBACK_PRESS, data, null, IBinder.FLAG_ONEWAY)
                    } catch (e: Throwable) {
                        break // the app is gone
                    } finally {
                        data.recycle()
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "home watcher stopped: ${e.message}")
            } finally {
                process?.destroy()
            }
        }.apply { isDaemon = true }.start()
    }
}
