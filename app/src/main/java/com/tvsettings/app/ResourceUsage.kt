package com.tvsettings.app

import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

data class PkgUsage(val cpuPercent: Float, val rssKb: Long)

data class UsageSnapshot(
    val cpuPercent: Float,
    val memUsedKb: Long,
    val memTotalKb: Long,
    val perPackage: Map<String, PkgUsage>,
)

/** What the main screen shows: whether it's in front (so sampling runs) and the latest numbers. */
object UsageFeed {
    var active by mutableStateOf(false)
    var snapshot by mutableStateOf<UsageSnapshot?>(null)
}

fun formatCpu(percent: Float): String =
    if (percent >= 10f) "${percent.toInt()}%" else String.format(Locale.US, "%.1f%%", percent)

fun formatKb(kb: Long): String =
    if (kb >= 1_048_576) String.format(Locale.US, "%.1f GB", kb / 1_048_576.0) else "${kb / 1024} MB"

/** CPU and RAM from /proc, read through the shell: apps can't read /proc/stat on newer Android. */
object ResourceUsage {
    private class Raw(
        val cpuTotal: Long,
        val cpuIdle: Long,
        val memTotalKb: Long,
        val memAvailKb: Long,
        val ticks: Map<Int, Long>,
        val procs: List<Triple<Int, Long, String>>, // pid, rss in kb, name
    )

    private var previous: Raw? = null
    private var previousGlobal: Pair<Long, Long>? = null

    // One shell run: total CPU, memory, then every process
    private const val FULL = "head -n 1 /proc/stat; echo @@MEM; head -n 3 /proc/meminfo; echo @@PS; " +
        "ps -A -o PID,RSS,NAME; echo @@STAT; " +
        "for f in /proc/[0-9]*/stat; do read -r l 2>/dev/null < \"\$f\" && echo \"\$l\"; done"

    private fun kb(line: String): Long = line.split(Regex("\\s+")).getOrNull(1)?.toLongOrNull() ?: 0L

    private fun parse(output: String): Raw? {
        var section = "CPU"
        var cpuTotal = 0L
        var cpuIdle = 0L
        var memTotal = 0L
        var memAvail = 0L
        val ticks = HashMap<Int, Long>()
        val procs = ArrayList<Triple<Int, Long, String>>()
        for (raw in output.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line == "@@MEM") { section = "MEM"; continue }
            if (line == "@@PS") { section = "PS"; continue }
            if (line == "@@STAT") { section = "STAT"; continue }
            when (section) {
                "CPU" -> if (line.startsWith("cpu ")) {
                    val f = line.split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
                    if (f.size >= 5) {
                        cpuTotal = f.take(8).sum()
                        cpuIdle = f[3] + f[4]
                    }
                }
                "MEM" -> when {
                    line.startsWith("MemTotal:") -> memTotal = kb(line)
                    line.startsWith("MemAvailable:") -> memAvail = kb(line)
                }
                "PS" -> {
                    val p = line.split(Regex("\\s+"), limit = 3)
                    val pid = p.getOrNull(0)?.toIntOrNull()
                    val rss = p.getOrNull(1)?.toLongOrNull()
                    if (pid != null && rss != null && p.size == 3) procs.add(Triple(pid, rss, p[2]))
                }
                "STAT" -> {
                    val close = line.lastIndexOf(')')
                    val pid = line.substringBefore(' ').toIntOrNull()
                    if (close > 0 && pid != null && close + 2 <= line.length) {
                        val t = line.substring(close + 2).split(' ')
                        val user = t.getOrNull(11)?.toLongOrNull()
                        val system = t.getOrNull(12)?.toLongOrNull()
                        if (user != null && system != null) ticks[pid] = user + system
                    }
                }
            }
        }
        if (cpuTotal == 0L) return null
        return Raw(cpuTotal, cpuIdle, memTotal, memAvail, ticks, procs)
    }

    /** Blocking. Percentages are of total CPU since the previous call, so the first call has none. */
    fun sample(): UsageSnapshot? {
        val result = Shell.exec(FULL)
        if (!result.ok) return null
        val raw = parse(result.output) ?: return null
        val prev = previous
        previous = raw
        val memUsed = (raw.memTotalKb - raw.memAvailKb).coerceAtLeast(0)
        if (prev == null) return UsageSnapshot(0f, memUsed, raw.memTotalKb, emptyMap())
        val dTotal = (raw.cpuTotal - prev.cpuTotal).coerceAtLeast(1)
        val dIdle = raw.cpuIdle - prev.cpuIdle
        val cpu = (100f * (dTotal - dIdle) / dTotal).coerceIn(0f, 100f)
        val perPackage = HashMap<String, PkgUsage>()
        for ((pid, rss, name) in raw.procs) {
            if (!name.contains('.') || name.startsWith("/") || name.startsWith("[")) continue
            val pkg = name.substringBefore(':')
            val now = raw.ticks[pid]
            val old = prev.ticks[pid]
            val delta = if (now != null && old != null) (now - old).coerceAtLeast(0) else 0L
            val current = perPackage[pkg]
            perPackage[pkg] = PkgUsage((current?.cpuPercent ?: 0f) + 100f * delta / dTotal, (current?.rssKb ?: 0L) + rss)
        }
        return UsageSnapshot(cpu, memUsed, raw.memTotalKb, perPackage)
    }

    /** Blocking, for the widget. Null without Shizuku, or on the first call. */
    fun globalCpu(): Float? {
        if (!ShizukuManager.isReady) return null
        val line = Shell.exec("head -n 1 /proc/stat").lines.firstOrNull { it.startsWith("cpu ") } ?: return null
        val f = line.split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        if (f.size < 5) return null
        val total = f.take(8).sum()
        val idle = f[3] + f[4]
        val prev = previousGlobal
        previousGlobal = total to idle
        if (prev == null) return null
        val dTotal = (total - prev.first).coerceAtLeast(1)
        return (100f * (dTotal - (idle - prev.second)) / dTotal).coerceIn(0f, 100f)
    }

    /** Used and total RAM in kb, from the activity manager. */
    fun ram(context: Context): Pair<Long, Long> {
        val info = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
        return (info.totalMem - info.availMem) / 1024 to info.totalMem / 1024
    }
}
