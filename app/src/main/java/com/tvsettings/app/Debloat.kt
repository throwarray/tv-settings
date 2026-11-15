package com.tvsettings.app

import android.content.Context
import android.os.Build
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// Community debloat lists, downloaded on request and never bundled. The lists are maintained by community
// members who are not affiliated with this project. See the disclaimer in Advanced.
object Debloat {
    enum class Tone { RECOMMENDED, KEEP, PROTECTED, ADVANCED, UNSAFE }

    class Info(val tone: Tone, val note: String?)

    // tellyscope-devices (MIT): per-model lists built from cleanyourtv (MIT)
    const val TELLYSCOPE = "https://raw.githubusercontent.com/man15h/tellyscope-devices/main/"
    // Universal Android Debloater NG (GPL-3): one list for all devices, rated by removal safety
    const val UAD_NG = "https://raw.githubusercontent.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/main/resources/assets/uad_lists.json"

    // Build.MODEL codenames -> tellyscope device ids
    private val DEVICES = mapOf(
        "AFTKRT" to "amazon-fire-tv-stick-4k-max-2023",
        "AFTKM" to "amazon-fire-tv-stick-4k-2023",
    )

    private const val PREFS = "debloat"
    private const val FILE = "debloat.json"

    private val tableState = mutableStateOf<Map<String, Info>>(emptyMap())

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun deviceId(): String? = DEVICES.entries.firstOrNull { Build.MODEL.contains(it.key, ignoreCase = true) }?.value

    fun customUrl(context: Context): String = prefs(context).getString("custom_url", "") ?: ""

    fun setCustomUrl(context: Context, url: String) {
        prefs(context).edit().putString("custom_url", url.trim()).apply()
    }

    fun loadedFrom(context: Context): String? = prefs(context).getString("source", null)

    fun infoFor(pkg: String): Info? = tableState.value[pkg]

    // A base folder gets devices/<id>.json appended; a direct .json link is used as it is.
    fun resolve(base: String, id: String?): String? = when {
        base.endsWith(".json") -> base
        id == null -> null
        base.endsWith("/") -> base + "devices/$id.json"
        else -> "$base/devices/$id.json"
    }

    fun load(context: Context) {
        tableState.value = runCatching {
            val f = File(context.filesDir, FILE)
            if (f.exists()) parse(f.readText()) else emptyMap()
        }.getOrDefault(emptyMap())
    }

    // Fetches one list, keeps it on the device, and returns how many packages it names.
    fun download(context: Context, url: String, source: String): Int {
        val text = fetch(url)
        val parsed = parse(text)
        if (parsed.isEmpty()) throw IllegalStateException("no packages in that file")
        File(context.filesDir, FILE).writeText(text)
        prefs(context).edit().putString("source", source).apply()
        tableState.value = parsed
        return parsed.size
    }

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
        prefs(context).edit().remove("source").apply()
        tableState.value = emptyMap()
    }

    private fun fetch(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 20_000
        try {
            if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().readText()
        } finally {
            c.disconnect()
        }
    }

    private fun parse(text: String): Map<String, Info> {
        val root = JSONObject(text)
        val out = HashMap<String, Info>()
        if (root.has("groups")) {
            // tellyscope device list: groups of packages, a keep list, and a protected group
            val keep = root.optJSONArray("keep")
            if (keep != null) for (i in 0 until keep.length()) out[keep.getString(i)] = Info(Tone.KEEP, null)
            val groups = root.getJSONArray("groups")
            for (g in 0 until groups.length()) {
                val group = groups.getJSONObject(g)
                val protectedGroup = group.optString("title").contains("Protected", ignoreCase = true)
                val pkgs = group.getJSONArray("packages")
                for (p in 0 until pkgs.length()) {
                    val e = pkgs.getJSONObject(p)
                    val tone = if (protectedGroup) Tone.PROTECTED else Tone.RECOMMENDED
                    val reason = e.optString("note").ifEmpty { group.optString("title").ifEmpty { null } }
                    out[e.getString("pkg")] = Info(tone, reason)
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
                out[pkg] = Info(tone, e.optString("description").ifEmpty { null })
            }
        }
        return out
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
