package com.tvsettings.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Hand-drawn icons for actions. The core icon set here lacks these names, so they are drawn directly.
object AppIcons {
    private fun icon(name: String, build: androidx.compose.ui.graphics.vector.ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(build).build()

    val Trash: ImageVector by lazy {
        icon("Trash") {
            path(fill = SolidColor(Color.White)) {
                moveTo(6f, 9f); lineTo(18f, 9f); lineTo(17f, 20f); lineTo(7f, 20f); close()
                moveTo(9f, 4f); lineTo(15f, 4f); lineTo(15f, 7f); lineTo(9f, 7f); close()
            }
        }
    }

    val Exit: ImageVector by lazy {
        icon("Exit") {
            path(fill = SolidColor(Color.White)) {
                moveTo(3f, 9f); lineTo(12f, 9f); lineTo(12f, 15f); lineTo(3f, 15f); close()
                moveTo(10f, 4f); lineTo(21f, 12f); lineTo(10f, 20f); close()
            }
        }
    }

    val Layers: ImageVector by lazy {
        icon("Layers") {
            path(fill = SolidColor(Color.White)) {
                moveTo(4f, 5f); lineTo(20f, 5f); lineTo(20f, 8f); lineTo(4f, 8f); close()
                moveTo(4f, 10f); lineTo(20f, 10f); lineTo(20f, 13f); lineTo(4f, 13f); close()
                moveTo(4f, 15f); lineTo(20f, 15f); lineTo(20f, 18f); lineTo(4f, 18f); close()
            }
        }
    }

    val Card: ImageVector by lazy {
        icon("Card") {
            path(fill = SolidColor(Color.White)) {
                moveTo(3f, 6f); lineTo(21f, 6f); lineTo(21f, 18f); lineTo(3f, 18f); close()
            }
        }
    }

    val Stop: ImageVector by lazy {
        icon("Stop") {
            path(fill = SolidColor(Color.White)) {
                moveTo(6f, 6f); lineTo(18f, 6f); lineTo(18f, 18f); lineTo(6f, 18f); close()
            }
        }
    }

    val Backup: ImageVector by lazy {
        icon("Backup") {
            path(fill = SolidColor(Color.White)) {
                moveTo(3f, 9f); lineTo(21f, 9f); lineTo(21f, 20f); lineTo(3f, 20f); close()
                moveTo(10f, 7f); lineTo(10f, 4f); lineTo(7f, 4f); lineTo(12f, 1f); lineTo(17f, 4f)
                lineTo(14f, 4f); lineTo(14f, 7f); close()
                moveTo(8f, 11f); lineTo(16f, 11f); lineTo(16f, 13f); lineTo(8f, 13f); close()
            }
        }
    }

    val Undo: ImageVector by lazy {
        icon("Undo") {
            path(fill = SolidColor(Color.White)) {
                moveTo(10f, 5f); lineTo(3f, 12f); lineTo(10f, 19f); close()
                moveTo(10f, 10f); lineTo(18f, 10f); lineTo(18f, 14f); lineTo(10f, 14f); close()
            }
        }
    }
}
