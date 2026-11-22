package com.tvsettings.app

/**
 * Suggestions to improve the device. Not sponsored: nobody pays to be listed. Descriptions are strings, so they can be translated.
 * The ashell F-Droid search was not checked against the package page: confirm it.
 */
object Recommended {
    class App(val name: String, val description: Int, val url: String)

    val list = listOf(
        App("F-Droid", R.string.rec_desc_fdroid, "https://f-droid.org/packages/org.fdroid.fdroid/"),
        App("Aurora Store", R.string.rec_desc_aurora, "https://f-droid.org/packages/com.aurora.store/"),
        App("Obtainium", R.string.rec_desc_obtainium, "https://github.com/ImranR98/Obtainium/releases/latest"),
        App("Shizuku", R.string.rec_desc_shizuku, "https://github.com/RikkaApps/Shizuku/releases/latest"),
        App("SelfieStick", R.string.rec_desc_selfiestick, "https://github.com/throwarray/selfiestick/releases/latest"),
        App("RethinkDNS", R.string.rec_desc_rethinkdns, "https://github.com/celzero/rethink-app/releases/latest"),
        App("LeanKey keyboard", R.string.rec_desc_leankey, "https://github.com/yuliskov/LeanKeyboard"),
        App("Better Open With", R.string.rec_desc_betteropenwith, "https://github.com/Giorgi/BetterOpenWith/releases/latest"),
        App("aShell", R.string.rec_desc_ashell, "https://f-droid.org/packages/?q=ashell"),
        App("Heads-up Notifications", R.string.rec_desc_headsup, "https://github.com/SimenCodes/heads-up/releases/latest"),
    )
}
