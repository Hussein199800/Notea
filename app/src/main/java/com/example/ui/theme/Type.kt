package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

/** IBM Plex Sans Arabic (OFL): modern Arabic + matching Latin in one family. */
val NoteaFont = FontFamily(
    Font(R.font.plex_arabic_regular, FontWeight.Normal),
    Font(R.font.plex_arabic_medium, FontWeight.Medium),
    Font(R.font.plex_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.plex_arabic_bold, FontWeight.Bold)
)

private fun style(size: Int, line: Int, weight: FontWeight, letter: Double = 0.0) =
    TextStyle(fontFamily = NoteaFont, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = letter.sp)

val NoteaTypography = Typography(
    displayLarge = style(44, 56, FontWeight.Bold),
    displayMedium = style(36, 46, FontWeight.Bold),
    displaySmall = style(30, 40, FontWeight.Bold),
    headlineLarge = style(28, 38, FontWeight.Bold),
    headlineMedium = style(24, 34, FontWeight.SemiBold),
    headlineSmall = style(21, 30, FontWeight.SemiBold),
    titleLarge = style(19, 28, FontWeight.SemiBold),
    titleMedium = style(16, 24, FontWeight.SemiBold),
    titleSmall = style(14, 21, FontWeight.Medium),
    bodyLarge = style(16, 27, FontWeight.Normal),
    bodyMedium = style(14, 22, FontWeight.Normal),
    bodySmall = style(12.5.toInt(), 19, FontWeight.Normal),
    labelLarge = style(14, 20, FontWeight.Medium),
    labelMedium = style(12, 17, FontWeight.Medium),
    labelSmall = style(11, 15, FontWeight.Medium)
)
