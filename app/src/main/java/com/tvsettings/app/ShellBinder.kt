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
    }

    private var generation = 0

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == TRANSACTION_RUN) {
            val command = data.readString() ?: ""
            val output = runCommand(command)
            reply?.writeString(output)
            return true
        }
        return super.onTransact(code, data, reply, flags)
    }

    fun runCommand(command: String): String {
        return try {
            val wrapped = "( $command ) 2>&1; status=\$?; printf '\\n__EXIT_CODE__:%s\\n' \"\$status\""
            val builder = ProcessBuilder("sh", "-c", wrapped).redirectErrorStream(true)
            builder.environment().apply {
                // the service process is started by Shizuku; its library and class paths would break system tools
                remove("LD_LIBRARY_PATH")
                remove("LD_PRELOAD")
                remove("CLASSPATH")
                put("PATH", "/system/bin:/system/xbin:/vendor/bin" + (get("PATH")?.let { ":$it" } ?: ""))
            }
            val process = builder.start()

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
}
