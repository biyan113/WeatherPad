package app.weatherpad.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.weatherpad.Sky
import kotlin.math.roundToInt

object W {
    val Text = Color.White
    val Dim = Color.White.copy(alpha = 0.72f)
    val Faint = Color.White.copy(alpha = 0.5f)
    val Glass = Color.White.copy(alpha = 0.13f)
    val GlassHi = Color.White.copy(alpha = 0.22f)
    val Rain = Color(0xFF9AD4FF)
    val Drawer = Color(0xFF18202C)
}

private val scheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color(0xFF1B2A3D),
    background = Color(0xFF1B2A3D),
    surface = W.Drawer,
    onSurface = Color.White,
    surfaceContainer = Color(0xFF223042),
    surfaceContainerHigh = Color(0xFF2A394D),
    onSurfaceVariant = W.Dim,
)

private val base = Typography()
private val typography = base.copy(
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium, fontSize = 13.sp),
)

@Composable
fun WeatherTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography) {
        CompositionLocalProvider(LocalContentColor provides Color.White, content = content)
    }
}

/** 按天气和昼夜给背景渐变（上, 下） */
fun skyGradient(sky: Sky, day: Boolean): Pair<Color, Color> = when (sky) {
    Sky.CLEAR -> if (day) Color(0xFF2F7BE0) to Color(0xFF63C3F2) else Color(0xFF0B1033) to Color(0xFF2B3A67)
    Sky.PARTLY -> if (day) Color(0xFF3F74B8) to Color(0xFF8DB6DD) else Color(0xFF141B33) to Color(0xFF3A4766)
    Sky.CLOUDY -> if (day) Color(0xFF56677A) to Color(0xFF93A3B4) else Color(0xFF1C2229) to Color(0xFF3D4650)
    Sky.FOG -> if (day) Color(0xFF6E7A86) to Color(0xFFAEB6BE) else Color(0xFF262B31) to Color(0xFF4B525A)
    Sky.DRIZZLE, Sky.RAIN -> if (day) Color(0xFF3A5066) to Color(0xFF6A8299) else Color(0xFF111923) to Color(0xFF2C3B4C)
    Sky.SNOW -> if (day) Color(0xFF6F8DAB) to Color(0xFFBACDE0) else Color(0xFF1E2A3A) to Color(0xFF4A5D75)
    Sky.STORM -> if (day) Color(0xFF2C2F48) to Color(0xFF59587A) else Color(0xFF0F0F1C) to Color(0xFF2D2B45)
}

private val tempStops = listOf(
    -10.0 to Color(0xFF8C8CFF),
    0.0 to Color(0xFF6EC6FF),
    10.0 to Color(0xFF7FE0B5),
    20.0 to Color(0xFFFFD166),
    30.0 to Color(0xFFFF8A3D),
    40.0 to Color(0xFFFF4D4D),
)

fun tempColor(t: Double): Color {
    if (t.isNaN()) return W.Dim
    if (t <= tempStops.first().first) return tempStops.first().second
    if (t >= tempStops.last().first) return tempStops.last().second
    val i = tempStops.indexOfFirst { it.first > t }
    val (t0, c0) = tempStops[i - 1]
    val (t1, c1) = tempStops[i]
    return lerp(c0, c1, ((t - t0) / (t1 - t0)).toFloat())
}

fun deg(t: Double) = if (t.isNaN()) "—" else "${t.roundToInt()}°"
