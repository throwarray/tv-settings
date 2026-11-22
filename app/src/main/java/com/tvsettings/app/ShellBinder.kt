package com.tvsettings.app

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

class ShellBinder : Binder() {
    companion object {
        private const val TAG = "ShellBinder"
        const val TRANSACTION_RUN = IBinder.FIRST_CALL_TRANSACTION
        const val TRANSACTION_POINTER = IBinder.FIRST_CALL_TRANSACTION + 1
    }

    private var generation = 0

    // the persistent pointer injector (PointerInjector) and its pipe
    private var pointer: Process? = null
    private var pointerIn: java.io.OutputStream? = null
    private var pointerOut: BufferedReader? = null

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == TRANSACTION_RUN) {
            val command = data.readString() ?: ""
            val output = runCommand(command)
            reply?.writeString(output)
            return true
        }
        if (code == TRANSACTION_POINTER) {
            reply?.writeString(pointerCommand(data.readString() ?: ""))
            return true
        }
        return super.onTransact(code, data, reply, flags)
    }

    // "START <apk>" starts the injector once; any other line goes to it and returns "ok" or "fail"
    @Synchronized
    private fun pointerCommand(line: String): String {
        if (line.startsWith("START ")) return if (startPointer(line.substring(6).trim())) "ok" else "fail"
        val out = pointerOut ?: return "fail"
        return try {
            pointerIn?.write((line + "\n").toByteArray())
            pointerIn?.flush()
            when (out.readLine()) {
                null -> { stopPointer(); "fail" }
                "ok" -> "ok"
                else -> "fail"
            }
        } catch (e: java.io.IOException) {
            stopPointer()
            "fail"
        }
    }

    private fun startPointer(apk: String): Boolean {
        pointer?.let { if (it.isAlive) return true }
        return try {
            val builder = ProcessBuilder(
                "app_process", "/system/bin", "--nice-name=tvsettings_pointer", "com.tvsettings.app.PointerInjector",
            )
            builder.environment().apply {
                remove("LD_LIBRARY_PATH")
                remove("LD_PRELOAD")
                put("CLASSPATH", apk)
                put("PATH", "/system/bin:/system/xbin:/vendor/bin" + (get("PATH")?.let { ":$it" } ?: ""))
            }
            builder.redirectError(java.io.File("/dev/null"))
            val proc = builder.start()
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            if (reader.readLine() != "READY") {
                proc.destroy()
                return false
            }
            pointer = proc
            pointerIn = proc.outputStream
            pointerOut = reader
            true
        } catch (e: Exception) {
            Log.e(TAG, "pointer injector didn't start: ${e.message}")
            false
        }
    }

    private fun stopPointer() {
        pointer?.destroy()
        pointer = null
        pointerIn = null
        pointerOut = null
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
