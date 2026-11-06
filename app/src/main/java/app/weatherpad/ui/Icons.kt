package app.weatherpad.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import app.weatherpad.Sky
import app.weatherpad.Wmo
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val SunC = Color(0xFFFFD34E)
private val MoonC = Color(0xFFF3ECCB)
private val CloudC = Color(0xFFF4F7FB)
private val CloudDark = Color(0xFFC3CEDB)
private val RainC = Color(0xFF7CC4FF)
private val SnowC = Color(0xFFFFFFFF)
private val BoltC = Color(0xFFFFD34E)

/** 纯 Canvas 画的天气图标，不依赖图标字体 */
@Composable
fun WeatherIcon(code: Int, isDay: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        translate((size.width - s) / 2, (size.height - s) / 2) { drawWeather(Wmo.sky(code), isDay, code, s) }
    }
}

internal fun DrawScope.drawWeather(sky: Sky, day: Boolean, code: Int, s: Float) {
    fun o(x: Float, y: Float) = Offset(x * s, y * s)
    when (sky) {
        Sky.CLEAR -> if (day) sun(o(.5f, .5f), s * .2f) else moon(o(.5f, .5f), s * .3f)
        Sky.PARTLY -> {
            if (day) sun(o(.36f, .36f), s * .16f) else moon(o(.38f, .36f), s * .22f)
            cloud(o(.55f, .64f), s * .8f, CloudC)
        }
        Sky.CLOUDY -> {
            cloud(o(.62f, .42f), s * .6f, CloudDark)
            cloud(o(.46f, .62f), s * .85f, CloudC)
        }
        Sky.FOG -> {
            cloud(o(.5f, .4f), s * .8f, CloudDark)
            listOf(.66f to .62f, .78f to .74f, .9f to .5f).forEach { (y, w) ->
                drawLine(CloudC, o(.5f - w / 2, y), o(.5f + w / 2, y), s * .055f, StrokeCap.Round)
            }
        }
        Sky.DRIZZLE -> {
            cloud(o(.5f, .4f), s * .85f, CloudC)
            listOf(.32f to .7f, .5f to .7f, .68f to .7f, .41f to .86f, .59f to .86f).forEach { (x, y) ->
                drawCircle(RainC, s * .04f, o(x, y))
            }
        }
        Sky.RAIN -> {
            cloud(o(.5f, .4f), s * .85f, CloudC)
            val xs = if (code in listOf(65, 82)) listOf(.26f, .42f, .58f, .74f) else listOf(.32f, .5f, .68f)
            xs.forEach { x -> drawLine(RainC, o(x, .64f), o(x - .07f, .88f), s * .055f, StrokeCap.Round) }
        }
        Sky.SNOW -> {
            cloud(o(.5f, .4f), s * .85f, CloudC)
            listOf(.3f to .72f, .5f to .86f, .7f to .72f).forEach { (x, y) -> flake(o(x, y), s * .075f) }
        }
        Sky.STORM -> {
            cloud(o(.5f, .38f), s * .85f, CloudDark)
            val bolt = Path().apply {
                moveTo(.56f * s, .52f * s); lineTo(.38f * s, .77f * s); lineTo(.5f * s, .77f * s)
                lineTo(.43f * s, .98f * s); lineTo(.66f * s, .68f * s); lineTo(.54f * s, .68f * s); lineTo(.62f * s, .52f * s); close()
            }
            drawPath(bolt, BoltC)
            drawLine(RainC, o(.28f, .62f), o(.23f, .78f), s * .045f, StrokeCap.Round)
            drawLine(RainC, o(.78f, .62f), o(.73f, .78f), s * .045f, StrokeCap.Round)
        }
    }
}

private fun DrawScope.sun(c: Offset, r: Float) {
    for (i in 0 until 8) {
        val a = i * PI / 4
        val d = Offset(cos(a).toFloat(), sin(a).toFloat())
        drawLine(SunC, c + d * (r * 1.32f), c + d * (r * 1.7f), r * .22f, StrokeCap.Round)
    }
    drawCircle(SunC, r, c)
}

private fun DrawScope.moon(c: Offset, r: Float) {
    val outer = Path().apply { addOval(Rect(c, r)) }
    val inner = Path().apply { addOval(Rect(Offset(c.x + r * .5f, c.y - r * .35f), r * .82f)) }
    drawPath(Path().apply { op(outer, inner, PathOperation.Difference) }, MoonC)
}

/** c 为云底部中线附近，w 为宽度 */
private fun DrawScope.cloud(c: Offset, w: Float, color: Color) {
    val h = w * .2f
    drawRoundRect(color, Offset(c.x - w / 2, c.y - h * .1f), androidx.compose.ui.geometry.Size(w, h),
        androidx.compose.ui.geometry.CornerRadius(h / 2))
    drawCircle(color, w * .2f, Offset(c.x - w * .18f, c.y - w * .02f))
    drawCircle(color, w * .27f, Offset(c.x + w * .1f, c.y - w * .1f))
}

private fun DrawScope.flake(c: Offset, r: Float) {
    for (i in 0 until 3) {
        val a = i * PI / 3 + PI / 2
        val d = Offset(cos(a).toFloat(), sin(a).toFloat()) * r
        drawLine(SnowC, c - d, c + d, r * .32f, StrokeCap.Round)
    }
}
