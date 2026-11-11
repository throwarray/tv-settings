package com.tvsettings.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import rikka.shizuku.Shizuku

data class ShellResult(val exitCode: Int, val output: String) {
    val ok: Boolean get() = exitCode == 0
    val clean: Boolean get() = ok && !output.contains("Error", ignoreCase = true)
    val lines: List<String> get() = output.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
}

object ShizukuManager {
    enum class State { NOT_RUNNING, NEEDS_PERMISSION, CONNECTING, READY }

    private const val REQUEST_CODE = 4021
    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private const val EXIT_MARKER = "__EXIT_CODE__:"
    private const val PREFS = "tvsettings"
    private const val KEY_SECURE_OPT_OUT = "secure_settings_opt_out"
    private const val KEY_AUTHORIZED = "shizuku_authorized"

    val state = mutableStateOf(State.NOT_RUNNING)

    // last known; the daemon being down doesn't revoke the permission
    val authorized = mutableStateOf(false)

    private var appContext: Context? = null
    private var initialized = false
    private var wantPermission = false
    private var selfGranted = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val binderReceived = Shizuku.OnBinderReceivedListener {
        refresh()
        if (wantPermission && state.value == State.NEEDS_PERMISSION) requestPermissionNow()
    }

    private val binderDead = Shizuku.OnBinderDeadListener {
        ElevatedShellManager.reset()
        selfGranted = false
        refresh()
    }

    private val permissionResult = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE) {
            wantPermission = false
            if (grantResult != PackageManager.PERMISSION_GRANTED) toast("Shizuku permission denied")
            refresh()
        }
    }

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        authorized.value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_AUTHORIZED, false)
        ElevatedShellManager.stateListener = { refresh() }
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        refresh()
    }

    fun isAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    fun hasPermission(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    val isReady: Boolean get() = state.value == State.READY

    fun refresh() {
        val ctx = appContext ?: return
        state.value = when {
            !isAvailable() -> State.NOT_RUNNING
            !hasPermission() -> State.NEEDS_PERMISSION
            ElevatedShellManager.isBound -> State.READY
            else -> {
                try {
                    ElevatedShellManager.bind(ctx)
                } catch (_: Throwable) {
                }
                State.CONNECTING
            }
        }
        when (state.value) {
            State.NEEDS_PERMISSION -> setAuthorized(false)
            State.CONNECTING, State.READY -> setAuthorized(true)
            State.NOT_RUNNING -> Unit
        }
    }

    private fun setAuthorized(value: Boolean) {
        if (authorized.value == value) return
        authorized.value = value
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putBoolean(KEY_AUTHORIZED, value)?.apply()
    }

    /** Authorise if running but not authorised, else open the Shizuku app. */
    fun requestPermission(activity: Activity) {
        appContext = activity.applicationContext
        refresh()
        when (state.value) {
            State.NEEDS_PERMISSION -> requestPermissionNow()
            State.NOT_RUNNING -> {
                wantPermission = true // ask automatically once the service is up and we're back
                if (!openShizukuApp(activity)) toast("Shizuku isn't running")
            }
            State.CONNECTING, State.READY -> toast("Shizuku is connected")
        }
    }

    private fun requestPermissionNow() {
        try {
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                toast("Shizuku permission was denied permanently. Re-enable it inside the Shizuku app.")
                wantPermission = false
                return
            }
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (e: Throwable) {
            toast("Shizuku request failed: ${e.message}")
        }
    }

    private fun openShizukuApp(activity: Activity): Boolean {
        val pm = activity.packageManager
        val intent = pm.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
            ?: pm.getLeanbackLaunchIntentForPackage(SHIZUKU_PACKAGE)
            ?: pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).setPackage(SHIZUKU_PACKAGE), 0)
                .firstOrNull()?.activityInfo
                ?.let { Intent(Intent.ACTION_MAIN).setClassName(it.packageName, it.name) }
            ?: return false
        return try {
            activity.startActivity(intent)
            toast("Start the Shizuku service, then come back.")
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** Runs as the shell user. Blocking. */
    fun exec(command: String): ShellResult = try {
        val raw = ElevatedShellManager.runCommand(command)
        val idx = raw.lastIndexOf(EXIT_MARKER)
        if (idx < 0) {
            ShellResult(-1, raw)
        } else {
            val code = raw.substring(idx + EXIT_MARKER.length).substringBefore('\n').trim().toIntOrNull() ?: -1
            ShellResult(code, raw.substring(0, idx))
        }
    } catch (e: Throwable) {
        ShellResult(-1, e.message ?: "Shizuku not connected")
    }

    /** Once per session; skipped if the user turned it off in Advanced. */
    fun grantWriteSecureSettings(): Boolean {
        val context = appContext ?: return false
        if (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SECURE_OPT_OUT, false)) return false
        if (selfGranted) return true
        selfGranted = exec("pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS").ok
        return selfGranted
    }

    /** Manual; revoking also stops the auto-grant. */
    fun setWriteSecureSettings(grant: Boolean): Boolean {
        val context = appContext ?: return false
        val verb = if (grant) "grant" else "revoke"
        val ok = exec("pm $verb ${context.packageName} android.permission.WRITE_SECURE_SETTINGS").ok
        if (ok) {
            selfGranted = grant
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_SECURE_OPT_OUT, !grant).apply()
        }
        return ok
    }

    private fun toast(message: String) {
        val ctx = appContext ?: return
        mainHandler.post { Toast.makeText(ctx, message, Toast.LENGTH_LONG).show() }
    }
}
