package com.tvsettings.app

import android.content.Context

/** The licences and sources of everything this app ships or fetches. Each licence text is split once and shared. */
object Licences {
    class Licence(val title: String, val copyright: String?, val url: String?, val paragraphs: List<String>)

    private fun asset(context: Context, path: String): String? =
        runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }.getOrNull()

    private fun paragraphs(text: String?): List<String> =
        text?.split(Regex("\\n\\s*\\n"))?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    fun load(context: Context): List<Licence> {
        val mit = paragraphs(asset(context, "licenses/LICENSE-MIT.txt"))
        val gpl = paragraphs(asset(context, "licenses/LICENSE-GPL-3.0.txt"))
        val apache = paragraphs(asset(context, "licenses/LICENSE-Apache-2.0.txt"))
        fun s(res: Int) = context.getString(res)
        return listOf(
            Licence(s(R.string.lic_tvsettings), s(R.string.lic_tvsettings_copy), "https://github.com/throwarray/tv-settings", emptyList()),
            Licence(s(R.string.lic_stickman), s(R.string.lic_stickman_copy), "https://github.com/MohamedNofal/stickman", mit),
            Licence(s(R.string.lic_shizuku), s(R.string.lic_shizuku_copy), "https://github.com/RikkaApps/Shizuku", mit),
            Licence(s(R.string.lic_firewall), s(R.string.lic_firewall_copy), "https://github.com/shynoiddev/FireWall-Blocks", mit),
            Licence(s(R.string.lic_cleanyourtv), s(R.string.lic_cleanyourtv_copy), "https://github.com/Gathin23/cleanyourtv", mit),
            Licence(s(R.string.lic_tellyscope), s(R.string.lic_tellyscope_copy), "https://github.com/man15h/tellyscope-devices", mit),
            Licence(s(R.string.lic_uad), s(R.string.lic_uad_copy),
                "https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation", gpl),
            Licence(s(R.string.lic_apache), s(R.string.lic_apache_copy), null, apache),
        )
    }
}
