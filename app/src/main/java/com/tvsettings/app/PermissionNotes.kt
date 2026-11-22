package com.tvsettings.app

import android.content.Context

/**
 * Notes shown with a few permissions, keyed by the last part of the permission's name. The text is in strings.xml,
 * so it can be translated.
 */
object PermissionNotes {
    private val NOTES = mapOf(
        "RECEIVER_NOT_EXPORTED" to R.string.perm_note_receiver_not_exported,
        "RECEIVER_EXPORTED" to R.string.perm_note_receiver_exported,
        "WRITE_SECURE_SETTINGS" to R.string.perm_note_write_secure_settings,
    )

    /** Shown for information only: the toggle is not offered. */
    private val FIXED = setOf("RECEIVER_NOT_EXPORTED", "RECEIVER_EXPORTED")

    fun note(context: Context, name: String): String? = NOTES[name.substringAfterLast('.')]?.let { context.getString(it) }

    /** The system's description first, then our note when there is one */
    fun subtitle(context: Context, name: String, systemDescription: String?): String? =
        listOfNotNull(systemDescription, note(context, name)).joinToString(" ").takeIf { it.isNotBlank() }

    fun fixed(name: String): Boolean = name.substringAfterLast('.') in FIXED
}
