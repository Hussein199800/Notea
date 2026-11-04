package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.NoteaPalette as P

private val Light = lightColorScheme(
    primary = P.Emerald700, onPrimary = Color.White,
    primaryContainer = P.Emerald100, onPrimaryContainer = P.Emerald900,
    secondary = P.Gold, onSecondary = Color.White,
    secondaryContainer = P.GoldSoft, onSecondaryContainer = Color(0xFF2B2108),
    tertiary = P.Emerald500, onTertiary = Color.White,
    background = P.Ivory, onBackground = P.Ink,
    surface = P.Ivory, onSurface = P.Ink,
    surfaceVariant = P.IvoryDeep, onSurfaceVariant = P.InkSoft,
    surfaceContainerLowest = P.IvorySurface,
    surfaceContainerLow = Color(0xFFF6F1E5),
    surfaceContainer = P.IvoryDeep,
    surfaceContainerHigh = Color(0xFFEDE6D6),
    surfaceContainerHighest = P.Parchment,
    outline = Color(0xFF8A8576), outlineVariant = Color(0xFFDCD5C4),
    error = P.Danger, onError = Color.White,
    inverseSurface = Color(0xFF32302A), inverseOnSurface = Color(0xFFF5F0E6)
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7FD3AE), onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF0F513D), onPrimaryContainer = Color(0xFFA6F0CC),
    secondary = P.GoldNight, onSecondary = Color(0xFF3B2E0A),
    secondaryContainer = Color(0xFF4A3C16), onSecondaryContainer = Color(0xFFF6E3B6),
    tertiary = P.Emerald200, onTertiary = Color(0xFF00382A),
    background = P.Charcoal, onBackground = Color(0xFFE4E2DC),
    surface = P.Charcoal, onSurface = Color(0xFFE4E2DC),
    surfaceVariant = P.CharcoalHigh, onSurfaceVariant = Color(0xFFC2C6BF),
    surfaceContainerLowest = Color(0xFF0D0F0F),
    surfaceContainerLow = P.CharcoalSurface,
    surfaceContainer = Color(0xFF1D2121),
    surfaceContainerHigh = P.CharcoalHigh,
    surfaceContainerHighest = P.CharcoalHighest,
    outline = Color(0xFF8C918B), outlineVariant = Color(0xFF3A403E),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005)
)

val NoteaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

val LocalIsDark = staticCompositionLocalOf { false }

@Composable
fun NoteaTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalIsDark provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) Dark else Light,
            typography = NoteaTypography,
            shapes = NoteaShapes,
            content = content
        )
    }
}

@Composable
fun noteColor(index: Int): Color {
    val list = if (LocalIsDark.current) NoteCardDark else NoteCardLight
    return list[index.coerceIn(0, list.lastIndex)]
}
