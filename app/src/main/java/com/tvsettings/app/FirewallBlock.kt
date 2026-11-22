package com.tvsettings.app

import android.os.Build
import android.os.IBinder
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * Blocks one app's network through Shizuku: iptables rules first (FireWall-Blocks' method, MIT), then connectivity's firewall. Android 13 and later use the connectivity
 * shell; earlier versions call the firewall rule through a binder wrapper (the calls FireWall-Blocks makes, MIT).
 * Only works while Shizuku runs: without it, the block may not hold. Falls back to netpolicy (background data only).
 */
object FirewallBlock {
    private const val ALLOW = 1
    private const val DENY = 2

    fun apply(pkg: String, uid: Int, blocked: Boolean): Boolean {
        if (!ShizukuManager.isReady) return false
        // the reference's method: iptables rules on a chain of our own, which is what worked on the stick
        if (Shell.script(listOf(Shell.Cmd.iptablesSetup(), Shell.Cmd.iptablesRule(uid, blocked))).ok) return true
        val firewall = if (Build.VERSION.SDK_INT >= 33) {
            Shell.batch(listOf(Shell.Cmd.chain3(true), Shell.Cmd.packageNetworking(pkg, !blocked))).ok
        } else {
            viaBinder(uid, if (blocked) DENY else ALLOW)
        }
        // when the firewall path fails (or the firewall isn't there), the earlier netpolicy background block still applies
        return firewall || Shell.batch(listOf(Shell.Cmd.netRestrict(uid, blocked))).ok
    }

    private fun viaBinder(uid: Int, rule: Int): Boolean = try {
        val binder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("connectivity"))
        val service = Class.forName("android.net.IConnectivityManager\$Stub")
            .getMethod("asInterface", IBinder::class.java).invoke(null, binder)
        val cls = service!!.javaClass
        val method = runCatching {
            cls.getMethod("setUidFirewallRule", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        }.getOrElse {
            cls.getMethod("setFirewallUidRule", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        }
        // chains: doze and standby (the two that run in the background), numbered as this Android version has them
        val chains = if (Build.VERSION.SDK_INT >= 30) listOf(1, 2) else listOf(0, 1)
        chains.forEach { method.invoke(service, it, uid, rule) }
        true
    } catch (_: Throwable) {
        false
    }
}
