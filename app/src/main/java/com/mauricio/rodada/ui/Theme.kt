package com.mauricio.rodada.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Colores claros estilo Apple, con naranja de acento (los que aprobó Mauricio). */
object C {
    val bg = Color(0xFFF5F5F7)
    val card = Color.White
    val text = Color(0xFF1D1D1F)
    val text2 = Color(0xFF6E6E73)
    val line = Color(0xFFEDEDF0)
    val accent = Color(0xFFD9480F)
    val accentText = Color(0xFFB8400B)
    val accentSoft = Color(0xFFFDEBDD)
    val green = Color(0xFF1E7B34)
    val greenBright = Color(0xFF2F9E44)
    val greenSoft = Color(0xFFE8F3EC)
    val amber = Color(0xFF8A5A00)
    val amberSoft = Color(0xFFFFF4E0)
    val orange = Color(0xFFF08C00)
    val red = Color(0xFFC92A2A)
    val redSoft = Color(0xFFFDECEC)
    val blue = Color(0xFF1E6FD9)
    val blueSoft = Color(0xFFE6F0FC)
    val map = Color(0xFFEEF1EC)

    fun parse(hex: String, fallback: Color = text2): Color = runCatching {
        Color(android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex"))
    }.getOrDefault(fallback)

    /** Color de la inclinación: verde suave, naranja medio, rojo fuerte. */
    fun lean(deg: Double): Color = when {
        kotlin.math.abs(deg) < 20 -> greenBright
        kotlin.math.abs(deg) < 35 -> orange
        else -> accent
    }
}

private val T = Typography(
    displayLarge = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Black, letterSpacing = (-2).sp),
    headlineLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 15.sp),
    bodySmall = TextStyle(fontSize = 13.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun RodadaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = C.accent, onPrimary = Color.White,
            secondary = C.blue, background = C.bg, surface = C.card,
            onBackground = C.text, onSurface = C.text, surfaceVariant = C.bg, onSurfaceVariant = C.text2,
            error = C.red, outline = C.line,
        ),
        typography = T,
        content = content,
    )
}
