package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Notea identity: deep emerald, warm ivory, muted gold, charcoal night.
object NoteaPalette {
    val Emerald900 = Color(0xFF07382A)
    val Emerald700 = Color(0xFF0E5A43)
    val Emerald500 = Color(0xFF2E7D62)
    val Emerald200 = Color(0xFF9ED8BE)
    val Emerald100 = Color(0xFFCFE9DC)

    val Ivory = Color(0xFFFAF6EC)
    val IvorySurface = Color(0xFFFFFCF5)
    val IvoryDeep = Color(0xFFF2ECDD)
    val Parchment = Color(0xFFEBE3D1)

    val Gold = Color(0xFFA88A4E)
    val GoldSoft = Color(0xFFF1E4C3)
    val GoldNight = Color(0xFFD9BE84)

    val Charcoal = Color(0xFF121414)
    val CharcoalSurface = Color(0xFF181B1B)
    val CharcoalHigh = Color(0xFF222727)
    val CharcoalHighest = Color(0xFF2B3130)

    val Ink = Color(0xFF1D1B16)
    val InkSoft = Color(0xFF4A473D)
    val Danger = Color(0xFFB3261E)
}

/** Note colours: index 0 = default surface. Names are used for accessibility labels. */
val NoteCardLight = listOf(
    Color(0xFFFFFCF5), Color(0xFFE3F1E8), Color(0xFFF6EBCF), Color(0xFFF7E1DA),
    Color(0xFFDFEAF3), Color(0xFFEAE3F2), Color(0xFFEFE2D2), Color(0xFFDDF0EE)
)
val NoteCardDark = listOf(
    Color(0xFF1C2020), Color(0xFF16302A), Color(0xFF332B17), Color(0xFF35211D),
    Color(0xFF18283A), Color(0xFF282035), Color(0xFF30271E), Color(0xFF153130)
)
