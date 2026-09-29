package de.oliverpekel.karfunkel.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// OLED: reines Schwarz als Fläche, Farbe nur für Akzente.
val Black = Color(0xFF000000)
val Gold = Color(0xFFD4A04A)
val GoldDim = Color(0xFF6B5227)
val Karfunkel = Color(0xFFC0262D)
val KarfunkelDark = Color(0xFF6E1418)
val TextPrimary = Color(0xFFE6E1D6)
val TextSecondary = Color(0xFF9A9387)
val Hairline = Color(0xFF1C1A17)
val RowAlt = Color(0xFF080807)

private val scheme = darkColorScheme(
    primary = Gold,
    onPrimary = Black,
    primaryContainer = GoldDim,
    onPrimaryContainer = TextPrimary,
    secondary = Karfunkel,
    onSecondary = TextPrimary,
    secondaryContainer = KarfunkelDark,
    onSecondaryContainer = TextPrimary,
    background = Black,
    onBackground = TextPrimary,
    surface = Black,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF121110),
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = Black,
    surfaceContainerLow = Color(0xFF050505),
    surfaceContainer = Color(0xFF0A0A09),
    surfaceContainerHigh = Color(0xFF0F0E0D),
    surfaceContainerHighest = Color(0xFF151413),
    inverseSurface = Color(0xFF2A2724),
    inverseOnSurface = TextPrimary,
    outline = Color(0xFF3A352E),
    outlineVariant = Hairline,
    error = Color(0xFFE5737A),
    onError = Black,
)

@Composable
fun KarfunkelTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
