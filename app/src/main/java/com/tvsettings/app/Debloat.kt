package com.tvsettings.app

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.json.JSONObject

// Community debloat lists, downloaded on request and never bundled. The lists are maintained by community
// members who are not affiliated with this project. See the disclaimer in Advanced.
object Debloat {
    enum class Tone { RECOMMENDED, KEEP, PROTECTED, ADVANCED, UNSAFE }

    class Info(val tone: Tone, val note: String?)

    // tellyscope-devices (MIT): per-model lists built from cleanyourtv (MIT)
    const val TELLYSCOPE = "https://raw.githubusercontent.com/man15h/tellyscope-devices/main/"
    // Universal Android Debloater NG (GPL-3): one list for all devices, rated by removal safety
    const val UAD_NG = "https://raw.githubusercontent.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/main/resources/assets/uad_lists.json"

    private const val PREFS = "debloat"
    private const val FILE = "debloat.json"

    // Quiet telemetry and the OTA freeze (MIT, Mohamed Nofal, from Stickman): the same packages and commands
    val TELEMETRY_PKGS = listOf(
        "com.amazon.device.metrics", "com.amazon.client.metrics.api", "com.amazon.device.crashmanager",
        "com.fireos.usagestats.proxy", "com.amazon.ftvads.deeplinking", "com.amazon.tv.csapp", "com.amazon.ceviche",
        "com.amazon.diode",
    )
    private const val USAGE_PROXY = "com.fireos.usagestats.proxy"
    private val OTA_PKGS = listOf(
        "com.amazon.device.software.ota", "com.amazon.device.software.ota.override", "com.amazon.tv.forcedotaupdater.v2",
    )

    fun quietOn(context: Context): Boolean = prefs(context).getBoolean("quiet", false)
    fun setQuiet(context: Context, on: Boolean) { prefs(context).edit().putBoolean("quiet", on).apply() }
    fun otaOn(context: Context): Boolean = prefs(context).getBoolean("ota", false)
    fun setOta(context: Context, on: Boolean) { prefs(context).edit().putBoolean("ota", on).apply() }

    // on: no background run (and the usage proxy loses usage access), then stop them; off: back to default
    fun quietCommands(on: Boolean): List<String> = TELEMETRY_PKGS.flatMap { p ->
        val mode = if (on) "ignore" else "default"
        listOfNotNull(
            Shell.Cmd.appOps(p, "RUN_ANY_IN_BACKGROUND", mode),
            if (p == USAGE_PROXY) Shell.Cmd.appOps(p, "GET_USAGE_STATS", mode) else null,
            if (on) Shell.Cmd.kill(p) else null,
        )
    }

    fun otaCommands(on: Boolean): List<String> = OTA_PKGS.flatMap { p ->
        listOf(Shell.Cmd.suspend(p, on), Shell.Cmd.setEnabled(p, !on))
    }

    private val tableState = mutableStateOf<Map<String, Info>>(emptyMap())

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun customUrl(context: Context): String = prefs(context).getString("custom_url", "") ?: ""

    fun setCustomUrl(context: Context, url: String) {
        prefs(context).edit().putString("custom_url", url.trim()).apply()
    }

    fun loadedFrom(context: Context): String? = prefs(context).getString("source", null)

    fun loadedName(context: Context): String? = prefs(context).getString("source_name", null)

    // The device id of the applied tellyscope list, if that is what is applied
    fun appliedDeviceId(context: Context): String? =
        loadedFrom(context)?.takeIf { it.startsWith("tellyscope:") }?.removePrefix("tellyscope:")

    fun infoFor(pkg: String): Info? = tableState.value[pkg]

    // A base folder gets devices/<id>.json appended; a direct .json link is used as it is.
    fun resolve(base: String, id: String?): String? = when {
        base.endsWith(".json") -> base
        id == null -> null
        base.endsWith("/") -> base + "devices/$id.json"
        else -> "$base/devices/$id.json"
    }

    // Only installed packages are kept in memory: a list can name thousands of apps this device doesn't have
    private fun installed(context: Context): Set<String> =
        context.packageManager.getInstalledPackages(0).mapTo(HashSet()) { it.packageName }

    fun load(context: Context) {
        tableState.value = runCatching {
            val f = File(context.filesDir, FILE)
            if (f.exists()) parse(f.readText(), installed(context)).table else emptyMap()
        }.getOrDefault(emptyMap())
    }

    class Device(val id: String, val name: String, val match: Map<String, List<String>>?, val file: String, val sha256: String)

    private const val INDEX_FILE = "index.json"

    // The only index download: a button press, or the auto-update setting. Saved for later offline use.
    fun updateIndex(context: Context): List<Device> {
        val text = String(fetchBytes(TELLYSCOPE + INDEX_FILE), Charsets.UTF_8)
        val list = parseIndex(text)
        if (list.isEmpty()) throw IllegalStateException("the index has no devices")
        File(context.filesDir, INDEX_FILE).writeText(text)
        return list
    }

    // Cleared when the Advanced page closes: the index only exists to pick the right list
    fun clearIndex(context: Context) {
        File(context.filesDir, INDEX_FILE).delete()
    }

    // No network: whatever index was saved last
    fun cachedIndex(context: Context): List<Device> = runCatching {
        File(context.filesDir, INDEX_FILE).takeIf { it.exists() }?.let { parseIndex(it.readText()) } ?: emptyList()
    }.getOrDefault(emptyList())

    // Every model, with its match rule or null
    private fun parseIndex(text: String): List<Device> {
        val arr = JSONObject(text).getJSONArray("devices")
        return (0 until arr.length()).map { i ->
            val d = arr.getJSONObject(i)
            val m = d.optJSONObject("match")
            val rule = m?.let { mo ->
                mo.keys().asSequence().associateWith { k ->
                    val a = mo.getJSONArray(k)
                    (0 until a.length()).map { a.getString(it) }
                }
            }
            Device(d.getString("id"), d.getString("name"), rule, d.getString("file"), d.getString("sha256"))
        }
    }

    // Shown in the list: the name with its year, taken from the id when the name has none
    fun label(d: Device): String {
        val year = Regex("(19|20)\\d{2}").find(d.id)?.value
        return if (year != null && !d.name.contains(year)) "${d.name} ($year)" else d.name
    }

    fun downloadDevice(context: Context, d: Device): Int {
        val bytes = fetchBytes(TELLYSCOPE + d.file)
        val sum = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        if (!sum.equals(d.sha256, ignoreCase = true)) throw IllegalStateException("checksum doesn't match the index")
        return store(context, String(bytes, Charsets.UTF_8), "tellyscope:${d.id}", d.name)
    }

    // Fetches one list from any URL (UAD-NG or a custom one), keeps it on the device, and returns how many packages it names.
    fun download(context: Context, url: String, source: String): Int = store(context, String(fetchBytes(url), Charsets.UTF_8), source, source)

    private fun store(context: Context, text: String, source: String, name: String): Int {
        val parsed = parse(text, installed(context))
        if (parsed.total == 0) throw IllegalStateException("no packages in that file")
        File(context.filesDir, FILE).writeText(text)
        prefs(context).edit().putString("source", source).putString("source_name", name).apply()
        tableState.value = parsed.table
        return parsed.total
    }

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
        prefs(context).edit().remove("source").remove("source_name").apply()
        tableState.value = emptyMap()
    }

    private fun fetchBytes(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 20_000
        try {
            if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    // table: the entries for installed packages; total: how many packages the list names (shown to the user)
    private class Parsed(val table: Map<String, Info>, val total: Int)

    private fun parse(text: String, installed: Set<String>): Parsed {
        val root = JSONObject(text)
        val out = HashMap<String, Info>()
        val named = HashSet<String>()
        fun put(pkg: String, info: Info) {
            named.add(pkg)
            if (pkg in installed) out[pkg] = info
        }
        if (root.has("groups")) {
            // tellyscope device list: groups of packages, a keep list, and a protected group
            val keep = root.optJSONArray("keep")
            if (keep != null) for (i in 0 until keep.length()) put(keep.getString(i), Info(Tone.KEEP, null))
            val groups = root.getJSONArray("groups")
            for (g in 0 until groups.length()) {
                val group = groups.getJSONObject(g)
                val protectedGroup = group.optString("title").contains("Protected", ignoreCase = true)
                val pkgs = group.getJSONArray("packages")
                for (p in 0 until pkgs.length()) {
                    val e = pkgs.getJSONObject(p)
                    val tone = if (protectedGroup) Tone.PROTECTED else Tone.RECOMMENDED
                    val reason = e.optString("note").ifEmpty { group.optString("title").ifEmpty { null } }
                    put(e.getString("pkg"), Info(tone, reason))
                }
            }
        } else {
            // UAD-NG: package -> { list, description, removal }
            val keys = root.keys()
            while (keys.hasNext()) {
                val pkg = keys.next()
                val e = root.optJSONObject(pkg) ?: continue
                val tone = when (e.optString("removal")) {
                    "Recommended" -> Tone.RECOMMENDED
                    "Advanced", "Expert" -> Tone.ADVANCED
                    "Unsafe" -> Tone.UNSAFE
                    else -> continue
                }
                put(pkg, Info(tone, e.optString("description").ifEmpty { null }))
            }
        }
        return Parsed(out, named.size)
    }

    // sort order: safe to remove first, then use care, then protected or unsafe, then unlisted
    fun rank(tone: Tone?): Int = when (tone) {
        Tone.RECOMMENDED -> 0
        Tone.KEEP, Tone.ADVANCED -> 1
        Tone.PROTECTED, Tone.UNSAFE -> 2
        null -> 3
    }

    // green: safe to remove, yellow: use care or kept on purpose, tomato: protected or unsafe
    fun toneColor(tone: Tone?): Color = when (tone) {
        Tone.RECOMMENDED -> Oled.okGreen
        Tone.KEEP, Tone.ADVANCED -> Color(0xFFE6C84A)
        Tone.PROTECTED, Tone.UNSAFE -> Color(0xFFFF6347)
        null -> Oled.textSecondary
    }
}
