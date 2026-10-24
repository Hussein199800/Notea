package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = VaultDarkPrimary,
    onPrimary = VaultDarkOnPrimary,
    primaryContainer = VaultDarkPrimaryContainer,
    onPrimaryContainer = VaultDarkOnPrimaryContainer,
    secondary = VaultDarkSecondary,
    onSecondary = VaultDarkOnSecondary,
    secondaryContainer = VaultDarkSecondaryContainer,
    onSecondaryContainer = VaultDarkOnSecondaryContainer,
    tertiary = VaultTerracottaContainer,
    onTertiary = VaultOnTerracottaContainer,
    background = VaultDarkBackground,
    surface = VaultDarkSurface,
    surfaceVariant = VaultDarkSurfaceVariant,
    onBackground = VaultDarkOnSurface,
    onSurface = VaultDarkOnSurface,
    onSurfaceVariant = VaultDarkOnSurfaceVariant
)

private val LightColorScheme = lightColorScheme(
    primary = VaultEmeraldPrimary,
    onPrimary = VaultEmeraldOnPrimary,
    primaryContainer = VaultEmeraldContainer,
    onPrimaryContainer = VaultOnEmeraldContainer,
    secondary = VaultGoldSecondary,
    onSecondary = VaultOnGoldSecondary,
    secondaryContainer = VaultGoldContainer,
    onSecondaryContainer = VaultOnGoldContainer,
    tertiary = VaultTerracottaTertiary,
    onTertiary = VaultOnTerracottaTertiary,
    tertiaryContainer = VaultTerracottaContainer,
    onTertiaryContainer = VaultOnTerracottaContainer,
    background = VaultLightBackground,
    surface = VaultLightSurface,
    surfaceVariant = VaultLightSurfaceVariant,
    onBackground = VaultLightOnSurface,
    onSurface = VaultLightOnSurface,
    onSurfaceVariant = VaultLightOnSurfaceVariant
)

val VaultShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun VaultNotesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = VaultShapes,
        content = content
    )
}
