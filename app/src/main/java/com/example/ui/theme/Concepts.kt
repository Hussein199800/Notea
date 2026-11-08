package com.example.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * One fixed colour per concept, everywhere in the app (not one colour for the whole app).
 * g1/g2: vivid gradient for icons and bars (never under text).
 * t1/t2 + on: text-safe gradient, contrast >= 4.5:1 at every point (ConceptContrastTest).
 */
enum class Concept(val g1: Color, val g2: Color, val t1: Color, val t2: Color, val on: Color = Color.White) {
    NOTES(Color(0xFF34D399), Color(0xFF059669), Color(0xFF047857), Color(0xFF065F46)),
    TASKS(Color(0xFFFBBF24), Color(0xFFEA580C), Color(0xFFFCD34D), Color(0xFFF59E0B), Color(0xFF2A1700)),
    FOLDERS(Color(0xFFA78BFA), Color(0xFF7C3AED), Color(0xFF6D28D9), Color(0xFF4C1D95)),
    REMINDERS(Color(0xFF38BDF8), Color(0xFF2563EB), Color(0xFF0369A1), Color(0xFF1E40AF)),
    ARCHIVE(Color(0xFF94A3B8), Color(0xFF475569), Color(0xFF334155), Color(0xFF1E293B)),
    TRASH(Color(0xFFFB7185), Color(0xFFE11D48), Color(0xFFBE123C), Color(0xFF881337)),
    ASSISTANT(Color(0xFF818CF8), Color(0xFF4F46E5), Color(0xFF4338CA), Color(0xFF312E81)),
    DATA(Color(0xFF2DD4BF), Color(0xFF0D9488), Color(0xFF0F766E), Color(0xFF134E4A)),
    SECURITY(Color(0xFFD9BE84), Color(0xFFA88A4E), Color(0xFFF1E4C3), Color(0xFFD9BE84), Color(0xFF2B2108));
}

/** WCAG relative-luminance contrast ratio. */
fun contrastRatio(a: Color, b: Color): Double {
    fun lum(c: Color): Double {
        fun ch(v: Float): Double { val x = v.toDouble(); return if (x <= 0.03928) x / 12.92 else Math.pow((x + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }
    val l1 = lum(a); val l2 = lum(b)
    return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
}

/**
 * Applies the text-size setting and tablet scaling: a tablet in portrait shows the phone layout
 * scaled to a 560dp-wide canvas (k = width/560, 1..1.6); landscape keeps real dp (two columns).
 */
@androidx.compose.runtime.Composable
fun ScaledLayout(textScale: Float, content: @androidx.compose.runtime.Composable () -> Unit) {
    val cfg = androidx.compose.ui.platform.LocalConfiguration.current
    val base = androidx.compose.ui.platform.LocalDensity.current
    val k = tabletScale(cfg.screenWidthDp, cfg.screenHeightDp)
    val d = androidx.compose.runtime.remember(base, k, textScale) { androidx.compose.ui.unit.Density(base.density * k, base.fontScale * textScale) }
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides d, content = content)
}

fun tabletScale(widthDp: Int, heightDp: Int): Float =
    if (widthDp >= 600 && heightDp > widthDp) (widthDp / 560f).coerceIn(1f, 1.6f) else 1f
