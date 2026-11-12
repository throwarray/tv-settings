package com.tvsettings.app

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build

object AppInspector {


    // "OP: mode; ..." (older) or "OP (mode):" (newer); uid ops are prefixed "Uid mode: "
    private val OP_LINE = Regex("""^(Uid mode:\s*)?([A-Z][A-Z0-9_]+)(?::\s*|\s+\()([a-z_]+)""")

    // ignore = app gets mock data, deny = app gets an error
    val APP_OP_MODES = listOf(
        "default" to "Default",
        "allow" to "Allow",
        "ignore" to "Ignore (mock data)",
        "deny" to "Deny (error)",
        "foreground" to "Foreground only",
    )

    fun modeLabel(mode: String): String = APP_OP_MODES.firstOrNull { it.first == mode }?.second ?: mode

    /** Always listed, even with no recorded entry (as "default"). */
    private val CURATED = listOf(
        "CAMERA" to 29, "RECORD_AUDIO" to 29, "COARSE_LOCATION" to 29, "FINE_LOCATION" to 29,
        "READ_EXTERNAL_STORAGE" to 29, "WRITE_EXTERNAL_STORAGE" to 29,
        "SYSTEM_ALERT_WINDOW" to 29, "WRITE_SETTINGS" to 29, "REQUEST_INSTALL_PACKAGES" to 29,
        "GET_USAGE_STATS" to 29, "RUN_IN_BACKGROUND" to 29, "RUN_ANY_IN_BACKGROUND" to 29,
        "WAKE_LOCK" to 29, "START_FOREGROUND" to 29,
        "MANAGE_EXTERNAL_STORAGE" to 30,
    )

    /** Inverted modes: ignore = exempt, default/allow = auto-revoke applies. Has its own toggle. */
    const val AUTO_REVOKE_OP = "AUTO_REVOKE_PERMISSIONS_IF_UNUSED"

    fun setAutoRevoke(pkg: String, enabled: Boolean): Boolean {
        return Shell.appOpsSet(pkg, AUTO_REVOKE_OP, if (enabled) "default" else "ignore").clean
    }

    const val NOTIFICATIONS_OP = "POST_NOTIFICATION"

    // Before Android 13 this op alone gates an app's notifications; 13+ also has a runtime permission.
    fun setNotifications(pkg: String, allowed: Boolean): Boolean {
        val r = Shell.appOpsSet(pkg, NOTIFICATIONS_OP, if (allowed) "allow" else "ignore")
        if (Build.VERSION.SDK_INT >= 33) Shell.permission(pkg, "android.permission.POST_NOTIFICATIONS", allowed)
        return r.clean
    }


    private fun pretty(raw: String): String =
        raw.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

    // ---- Runtime permissions ----

    private val GRANT_LINE = Regex("""^\s+([A-Za-z0-9_.]+): granted=(true|false)""")

    /** From `dumpsys package`; unlike PackageInfo flags it can't go stale after grant/revoke. */
    private fun readGrantStates(pkg: String): Map<String, Boolean> {
        if (!ShizukuManager.isReady) return emptyMap()
        val r = Shell.dumpsys("package", pkg)
        if (!r.ok) return emptyMap()
        val states = HashMap<String, Boolean>()
        r.output.lineSequence().forEach { line ->
            GRANT_LINE.find(line)?.let { states[it.groupValues[1]] = it.groupValues[2] == "true" }
        }
        return states
    }

    private fun protLabel(pi: PermissionInfo): String = when (pi.protection) {
        PermissionInfo.PROTECTION_DANGEROUS -> "dangerous"
        PermissionInfo.PROTECTION_SIGNATURE -> "signature"
        PermissionInfo.PROTECTION_NORMAL -> "normal"
        else -> "other"
    }

    fun loadPermissions(pm: PackageManager, pkg: String): List<PermissionEntry> {
        val info = try {
            pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
        } catch (_: Exception) {
            return emptyList()
        }
        val names = info.requestedPermissions ?: return emptyList()
        val flags = info.requestedPermissionsFlags
        val dumped = readGrantStates(pkg)

        return names.indices.mapNotNull { i ->
            val name = names[i]
            val pi = try {
                pm.getPermissionInfo(name, 0)
            } catch (_: Exception) {
                return@mapNotNull null
            }
            val runtime = pi.protection == PermissionInfo.PROTECTION_DANGEROUS
            val development = pi.protection == PermissionInfo.PROTECTION_SIGNATURE &&
                (pi.protectionFlags and PermissionInfo.PROTECTION_FLAG_DEVELOPMENT) != 0

            val granted = dumped[name]
                ?: (flags != null && (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0)
            val description = try {
                pi.loadDescription(pm)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            } catch (_: Exception) {
                null
            }
            val tag = if (runtime) "" else "[${protLabel(pi)}] "
            val kind = when {
                name == "android.permission.ACCESS_MOCK_LOCATION" -> "appop"
                runtime -> "runtime"
                (pi.protectionFlags and PermissionInfo.PROTECTION_FLAG_APPOP) != 0 -> "appop"
                pi.protection == PermissionInfo.PROTECTION_NORMAL -> "fixed"
                else -> "signature"
            }
            PermissionEntry(name, pretty(name.substringAfterLast('.')), granted, (tag + (description ?: "")).takeIf { it.isNotBlank() }, kind)
        }.distinctBy { it.name }.sortedBy { it.label }
    }

    fun setPermission(pkg: String, permission: String, grant: Boolean): Boolean {
        return Shell.permission(pkg, permission, grant).ok
    }

    // ---- App-ops ----

    /** Ops that matter only if the app requests the matching permission; unlisted ops apply to all. */
    private val OP_PERMISSION: Map<String, String> = mapOf(
        "COARSE_LOCATION" to "ACCESS_COARSE_LOCATION",
        "FINE_LOCATION" to "ACCESS_FINE_LOCATION",
        "GET_USAGE_STATS" to "PACKAGE_USAGE_STATS",
        "START_FOREGROUND" to "FOREGROUND_SERVICE",
        "MOCK_LOCATION" to "ACCESS_MOCK_LOCATION",
    ) + listOf(
        "CAMERA", "RECORD_AUDIO", "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE", "SYSTEM_ALERT_WINDOW",
        "WRITE_SETTINGS", "REQUEST_INSTALL_PACKAGES", "MANAGE_EXTERNAL_STORAGE", "WAKE_LOCK",
        "READ_CONTACTS", "WRITE_CONTACTS", "READ_CALENDAR", "WRITE_CALENDAR", "READ_SMS", "SEND_SMS",
        "RECEIVE_SMS", "RECEIVE_MMS", "RECEIVE_WAP_PUSH", "READ_CALL_LOG", "WRITE_CALL_LOG", "CALL_PHONE",
        "READ_PHONE_STATE", "ANSWER_PHONE_CALLS", "BODY_SENSORS", "ACTIVITY_RECOGNITION", "BLUETOOTH_SCAN",
        "BLUETOOTH_CONNECT", "BLUETOOTH_ADVERTISE", "NEARBY_WIFI_DEVICES", "READ_MEDIA_AUDIO",
        "READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO", "ACCESS_MEDIA_LOCATION", "GET_ACCOUNTS", "ADD_VOICEMAIL",
        "USE_SIP", "PROCESS_OUTGOING_CALLS",
    ).associateWith { it }

    /** Hide gated ops unless the permission is requested or the op was explicitly restricted. */
    // every op is listed; its permission, when requested, is the one that matters
    private fun isRelevant(entry: AppOpEntry, requested: Set<String>): Boolean {
        if (entry.op == AUTO_REVOKE_OP || entry.op == NOTIFICATIONS_OP) return true
        val permission = OP_PERMISSION[entry.op] ?: entry.op
        return "android.permission.$permission" in requested
    }

    fun loadAppOps(pm: PackageManager, pkg: String): List<AppOpEntry> {
        val result = Shell.appOpsGet(pkg)
        if (!result.ok) return emptyList()

        val found = LinkedHashMap<String, AppOpEntry>()
        result.output.lineSequence().forEach { line ->
            val m = OP_LINE.find(line) ?: return@forEach
            val uidMode = m.groupValues[1].isNotEmpty()
            val op = m.groupValues[2]
            val key = (if (uidMode) "uid:" else "pkg:") + op
            if (key !in found) found[key] = entry(op, m.groupValues[3], uidMode)
        }
        CURATED.forEach { (op, minSdk) ->
            if (Build.VERSION.SDK_INT >= minSdk && found.keys.none { it.endsWith(":$op") }) {
                found["pkg:$op"] = entry(op, "default", false)
            }
        }

        // null = unreadable, so don't filter
        val requested: Set<String>? = try {
            pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS).requestedPermissions?.toSet().orEmpty()
        } catch (_: Exception) {
            null
        }
        return found.values
            .filter { requested == null || isRelevant(it, requested) }
            .sortedBy { it.label }
    }

    private fun entry(op: String, mode: String, uidMode: Boolean) =
        AppOpEntry(op = op, label = pretty(op), mode = mode, uidMode = uidMode)

    // The AppOp that backs an AppOp-protected permission, e.g. PACKAGE_USAGE_STATS -> GET_USAGE_STATS.
    fun opForPermission(permission: String): String {
        val short = permission.substringAfterLast('.')
        return OP_PERMISSION.entries.firstOrNull { it.value == short }?.key ?: short
    }

    fun setAppOp(pkg: String, entry: AppOpEntry, mode: String): Boolean {
        if (APP_OP_MODES.none { it.first == mode }) return false
        return Shell.appOpsSet(pkg, entry.op, mode, uid = entry.uidMode).clean
    }
}
