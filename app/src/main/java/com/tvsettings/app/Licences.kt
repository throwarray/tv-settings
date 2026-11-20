package com.tvsettings.app

import android.content.Context

// The licence texts ship in the app's assets, so they are always in the APK.
object Licences {
    class Licence(val title: String, val text: String)

    private val FILES = listOf(
        R.string.licence_title_stickman to "licenses/LICENSE-Stickman-MIT.txt",
        R.string.licence_title_shizuku to "licenses/LICENSE-Shizuku-MIT.txt",
        R.string.licence_title_apache to "licenses/LICENSE-Apache-2.0.txt",
    )

    fun load(context: Context): List<Licence> = FILES.map { (titleRes, path) ->
        val text = runCatching { context.assets.open(path).bufferedReader().use { it.readText() } }
            .getOrDefault("Couldn't read this licence.")
        Licence(context.getString(titleRes), text)
    }
}
