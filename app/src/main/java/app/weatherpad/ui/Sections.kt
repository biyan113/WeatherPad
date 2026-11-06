package app.weatherpad.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Masks
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.weatherpad.Forecast
import java.time.LocalDateTime
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun GlassCard(title: String?, icon: ImageVector?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(W.Glass).padding(16.dp)) {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, null, Modifier.size(15.dp), tint = W.Dim)
                    Spacer(Modifier.width(6.dp))
                }
                Text(title, fontSize = 13.sp, color = W.Dim, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(10.dp))
        }
        content()
    }
}

/* ---------------- 逐小时 ---------------- */

@Composable
fun HourlyCard(fc: Forecast) {
    val hours = fc.upcoming(25)
    if (hours.isEmpty()) return
    val itemW = 62.dp
    val nextRain = hours.drop(1).firstOrNull { it.pop >= 50 }
    val summary = when {
        hours.first().pop >= 50 -> "当前有降水可能，概率 ${hours.first().pop}%"
        nextRain != null -> "预计 ${nextRain.time.hour} 时左右可能降水（${nextRain.pop}%）"
        else -> "未来 24 小时降水可能性较低"
    }
    GlassCard(summary, Icons.Rounded.Schedule, Modifier.fillMaxWidth()) {
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            Row {
                hours.forEachIndexed { i, h ->
                    Column(Modifier.width(itemW), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (i == 0) "现在" else "${h.time.hour}时", fontSize = 13.sp, fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal)
                        Spacer(Modifier.height(6.dp))
                        WeatherIcon(h.code, h.isDay, Modifier.size(30.dp))
                        Text(if (h.pop >= 20) "${h.pop}%" else "", fontSize = 11.sp, color = W.Rain, modifier = Modifier.height(16.dp))
                    }
                }
            }
            TempCurve(hours.map { it.temp }, Modifier.width(itemW * hours.size).height(76.dp))
        }
    }
}

@Composable
private fun TempCurve(temps: List<Double>, modifier: Modifier) {
    val tm = rememberTextMeasurer()
    val style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    Canvas(modifier) {
        val valid = temps.filterNot { it.isNaN() }
        if (valid.isEmpty()) return@Canvas
        val lo = valid.min(); val hi = valid.max()
        val top = 26.dp.toPx(); val bottom = size.height - 8.dp.toPx()
        val w = size.width / temps.size
        val pts = temps.mapIndexed { i, t ->
            val f = if (hi - lo < 0.1) 0.5f else ((t - lo) / (hi - lo)).toFloat()
            Offset(w * (i + 0.5f), bottom - f * (bottom - top))
        }
        val line = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) {
                val mx = (pts[i - 1].x + pts[i].x) / 2
                cubicTo(mx, pts[i - 1].y, mx, pts[i].y, pts[i].x, pts[i].y)
            }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(pts.last().x, size.height); lineTo(pts.first().x, size.height); close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), top, size.height))
        drawPath(line, Brush.horizontalGradient(temps.map { tempColor(it) }), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        pts.forEachIndexed { i, p ->
            drawCircle(Color.White, 3.dp.toPx(), p)
            val r = tm.measure(deg(temps[i]), style)
            drawText(r, topLeft = Offset(p.x - r.size.width / 2f, p.y - r.size.height - 4.dp.toPx()))
        }
    }
}

/* ---------------- 逐日 ---------------- */

@Composable
fun DailyCard(fc: Forecast, modifier: Modifier) {
    val days = fc.upcomingDays()
    if (days.isEmpty()) return
    val lo = days.minOf { if (it.min.isNaN()) Double.MAX_VALUE else it.min }
    val hi = days.maxOf { if (it.max.isNaN()) -Double.MAX_VALUE else it.max }
    GlassCard("${days.size} 日预报", Icons.Rounded.CalendarMonth, modifier) {
        days.forEachIndexed { i, d ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
            Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically) {
                val label = when (i) {
                    0 -> "今天"
                    1 -> "明天"
                    else -> d.date.dayOfWeek.getDisplayName(DateTextStyle.SHORT, Locale.CHINA)
                }
                Column(Modifier.width(58.dp)) {
                    Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text("${d.date.monthValue}/${d.date.dayOfMonth}", fontSize = 11.sp, color = W.Faint)
                }
                Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    WeatherIcon(d.code, true, Modifier.size(26.dp))
                    if (d.pop >= 20) Text("${d.pop}%", fontSize = 10.sp, color = W.Rain)
                }
                Text(deg(d.min), Modifier.width(42.dp), color = W.Dim, fontSize = 16.sp, textAlign = TextAlign.End)
                TempBar(d.min, d.max, lo, hi, if (i == 0) fc.current.temp else Double.NaN, Modifier.weight(1f).height(6.dp).padding(horizontal = 0.dp))
                Text(deg(d.max), Modifier.width(42.dp), fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun TempBar(min: Double, max: Double, lo: Double, hi: Double, now: Double, modifier: Modifier) {
    Canvas(modifier.padding(horizontal = 10.dp)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(Color.Black.copy(alpha = 0.18f), cornerRadius = r)
        if (min.isNaN() || max.isNaN() || hi <= lo) return@Canvas
        fun x(t: Double) = ((t - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f) * size.width
        val a = x(min); val b = x(max).coerceAtLeast(a + size.height)
        drawRoundRect(
            Brush.horizontalGradient(listOf(tempColor(min), tempColor(max)), a, b),
            topLeft = Offset(a, 0f), size = Size(b - a, size.height), cornerRadius = r,
        )
        if (!now.isNaN()) {
            val c = Offset(x(now), size.height / 2)
            drawCircle(Color.Black.copy(alpha = 0.35f), size.height * 0.95f, c)
            drawCircle(Color.White, size.height * 0.7f, c)
        }
    }
}

/* ---------------- 详情卡片 ---------------- */

@Composable
fun DetailGrid(fc: Forecast, modifier: Modifier) {
    val tiles: List<@Composable (Modifier) -> Unit> = listOf(
        { m -> AqiTile(fc, m) },
        { m -> UvTile(fc, m) },
        { m -> WindTile(fc, m) },
        { m -> SunTile(fc, m) },
        { m -> FeelsTile(fc, m) },
        { m -> HumidityTile(fc, m) },
        { m -> PrecipTile(fc, m) },
        { m -> VisibilityTile(fc, m) },
        { m -> PressureTile(fc, m) },
        { m -> CloudTile(fc, m) },
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { tile -> tile(Modifier.weight(1f).fillMaxHeight().heightIn(min = 150.dp)) }
            }
        }
    }
}

@Composable
private fun Big(text: String, unit: String = "") {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(text, fontSize = 34.sp, fontWeight = FontWeight.Medium)
        if (unit.isNotEmpty()) Text(" $unit", fontSize = 15.sp, color = W.Dim, modifier = Modifier.padding(bottom = 6.dp))
    }
}

@Composable
private fun Note(text: String) = Text(text, fontSize = 13.sp, color = W.Dim, lineHeight = 18.sp)

/** 带位置圆点的彩色刻度条 */
@Composable
private fun ScaleBar(fraction: Float, colors: List<Color>) {
    Canvas(Modifier.fillMaxWidth().height(6.dp)) {
        drawRoundRect(Brush.horizontalGradient(colors), cornerRadius = CornerRadius(size.height / 2))
        val c = Offset(fraction.coerceIn(0f, 1f) * size.width, size.height / 2)
        drawCircle(Color.Black.copy(alpha = 0.35f), size.height * 1.0f, c)
        drawCircle(Color.White, size.height * 0.75f, c)
    }
}

private fun fmt(v: Double, digits: Int = 0) = if (v.isNaN()) "—" else if (digits == 0) v.roundToInt().toString() else "%.${digits}f".format(v)

@Composable
private fun AqiTile(fc: Forecast, m: Modifier) = GlassCard("空气质量", Icons.Rounded.Masks, m) {
    val a = fc.air
    if (a == null) { Note("暂无数据"); return@GlassCard }
    val (level, advice) = when {
        a.usAqi <= 50 -> "优" to "空气很好，适合户外活动"
        a.usAqi <= 100 -> "良" to "敏感人群减少长时间户外活动"
        a.usAqi <= 150 -> "轻度污染" to "敏感人群应减少户外活动"
        a.usAqi <= 200 -> "中度污染" to "建议减少户外活动"
        a.usAqi <= 300 -> "重度污染" to "避免户外活动"
        else -> "严重污染" to "留在室内"
    }
    Big(a.usAqi.toString(), level)
    Spacer(Modifier.height(8.dp))
    ScaleBar(a.usAqi / 300f, listOf(Color(0xFF4CD964), Color(0xFFFFE14D), Color(0xFFFF9F40), Color(0xFFFF4D4D), Color(0xFF9B59B6), Color(0xFF7E2E3E)))
    Spacer(Modifier.height(8.dp))
    Note("PM2.5 ${fmt(a.pm25)} · PM10 ${fmt(a.pm10)} μg/m³\n$advice")
}

@Composable
private fun UvTile(fc: Forecast, m: Modifier) = GlassCard("紫外线指数", Icons.Rounded.WbSunny, m) {
    val uv = fc.upcomingDays().firstOrNull()?.uv ?: Double.NaN
    val level = when {
        uv.isNaN() -> ""
        uv < 3 -> "低"
        uv < 6 -> "中等"
        uv < 8 -> "高"
        uv < 11 -> "很高"
        else -> "极高"
    }
    Big(fmt(uv), level)
    Spacer(Modifier.height(8.dp))
    ScaleBar((if (uv.isNaN()) 0.0 else uv / 11).toFloat(), listOf(Color(0xFF4CD964), Color(0xFFFFE14D), Color(0xFFFF9F40), Color(0xFFFF4D4D), Color(0xFFB36BFF)))
    Spacer(Modifier.height(8.dp))
    Note(if (uv >= 6) "今日最高值，外出注意防晒" else "今日最高值")
}

private val dirs = listOf("北", "东北", "东", "东南", "南", "西南", "西", "西北")
private fun beaufort(kmh: Double): Int = listOf(1, 6, 12, 20, 29, 39, 50, 62, 75, 89, 103, 118).count { kmh >= it }

@Composable
private fun WindTile(fc: Forecast, m: Modifier) = GlassCard("风", Icons.Rounded.Air, m) {
    val c = fc.current
    val dir = if (c.windDir.isNaN()) "" else dirs[(((c.windDir % 360) + 22.5) / 45).toInt() % 8] + "风"
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Big("${beaufort(c.wind)}", "级")
            Note("$dir\n${fmt(c.wind)} km/h\n阵风 ${fmt(c.gusts)} km/h")
        }
        Compass(c.windDir, Modifier.size(84.dp))
    }
}

@Composable
private fun Compass(dirDeg: Double, modifier: Modifier) {
    val tm = rememberTextMeasurer()
    val st = TextStyle(color = W.Dim, fontSize = 10.sp)
    Canvas(modifier) {
        val r = size.minDimension / 2
        val c = center
        drawCircle(Color.White.copy(alpha = 0.25f), r - 1.dp.toPx(), c, style = Stroke(1.dp.toPx()))
        for (i in 0 until 36) {
            val a = i * 10 * PI / 180
            val len = if (i % 9 == 0) 6.dp.toPx() else 3.dp.toPx()
            val d = Offset(sin(a).toFloat(), -cos(a).toFloat())
            drawLine(Color.White.copy(alpha = 0.35f), c + d * (r - 2.dp.toPx()), c + d * (r - 2.dp.toPx() - len), 1.dp.toPx())
        }
        listOf("北" to 0, "东" to 90, "南" to 180, "西" to 270).forEach { (t, a) ->
            val rad = a * PI / 180
            val p = c + Offset(sin(rad).toFloat(), -cos(rad).toFloat()) * (r - 16.dp.toPx())
            val res = tm.measure(t, st)
            drawText(res, topLeft = p - Offset(res.size.width / 2f, res.size.height / 2f))
        }
        if (!dirDeg.isNaN()) {
            // 风向是“风从哪来”，箭头指向风吹去的方向
            rotate((dirDeg + 180).toFloat(), c) {
                val tip = Offset(c.x, c.y - r * 0.62f)
                val tail = Offset(c.x, c.y + r * 0.62f)
                drawLine(Color.White, tail, tip, 2.5.dp.toPx(), StrokeCap.Round)
                val head = Path().apply {
                    moveTo(tip.x, tip.y - 2.dp.toPx())
                    lineTo(tip.x - 6.dp.toPx(), tip.y + 9.dp.toPx())
                    lineTo(tip.x + 6.dp.toPx(), tip.y + 9.dp.toPx())
                    close()
                }
                drawPath(head, Color.White)
                drawCircle(Color.White, 3.dp.toPx(), tail)
            }
        }
    }
}

@Composable
private fun SunTile(fc: Forecast, m: Modifier) = GlassCard("日出日落", Icons.Rounded.WbTwilight, m) {
    val d = fc.upcomingDays().firstOrNull()
    val rise = d?.sunrise; val set = d?.sunset
    if (rise == null || set == null) { Note("极昼或极夜"); return@GlassCard }
    val now = fc.now()
    val upcoming = if (now.isBefore(rise)) "日出" to rise else if (now.isBefore(set)) "日落" to set else "日出" to (fc.upcomingDays().getOrNull(1)?.sunrise ?: rise)
    Big(hm(upcoming.second), upcoming.first)
    SunArc(rise, set, now, Modifier.fillMaxWidth().height(46.dp).padding(vertical = 4.dp))
    Note("日出 ${hm(rise)} · 日落 ${hm(set)}")
}

private fun hm(t: LocalDateTime) = "%02d:%02d".format(t.hour, t.minute)

@Composable
private fun SunArc(rise: LocalDateTime, set: LocalDateTime, now: LocalDateTime, modifier: Modifier) {
    Canvas(modifier) {
        val total = java.time.Duration.between(rise, set).seconds.toFloat()
        val f = (java.time.Duration.between(rise, now).seconds / total).coerceIn(0f, 1f)
        val rect = Rect(0f, 0f, size.width, size.height * 2)
        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
        drawArc(Color.White.copy(alpha = 0.35f), 180f, 180f, false, rect.topLeft, rect.size, style = Stroke(1.5.dp.toPx(), pathEffect = dash))
        drawArc(Color(0xFFFFD34E), 180f, 180f * f, false, rect.topLeft, rect.size, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        drawLine(Color.White.copy(alpha = 0.3f), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        val a = PI * (1 - f)
        val p = Offset(size.width / 2 + (size.width / 2 * cos(a)).toFloat(), size.height - (size.height * sin(a)).toFloat())
        drawCircle(Color(0xFFFFD34E), 6.dp.toPx(), p)
    }
}

@Composable
private fun FeelsTile(fc: Forecast, m: Modifier) = GlassCard("体感温度", Icons.Rounded.Thermostat, m) {
    val c = fc.current
    Big(deg(c.feels))
    val diff = c.feels - c.temp
    Note(
        when {
            diff.isNaN() -> ""
            diff <= -2 -> if (c.wind > 15) "风使体感比实际更冷" else "体感比实际更冷"
            diff >= 2 -> if (c.humidity > 60) "湿度使体感比实际更热" else "体感比实际更热"
            else -> "与实际温度相近"
        },
    )
}

@Composable
private fun HumidityTile(fc: Forecast, m: Modifier) = GlassCard("湿度", Icons.Rounded.WaterDrop, m) {
    Big("${fc.current.humidity}", "%")
    Note("露点 ${deg(fc.current.dewPoint)}")
}

@Composable
private fun PrecipTile(fc: Forecast, m: Modifier) = GlassCard("降水", Icons.Rounded.Umbrella, m) {
    val today = fc.upcomingDays().firstOrNull()
    Big(fmt(today?.precipSum ?: 0.0, 1), "毫米")
    val next = fc.upcomingDays().drop(1).firstOrNull { it.precipSum >= 1 }
    Note(
        "今日降水概率 ${today?.pop ?: 0}%\n" +
            (next?.let { "下次降水：${it.date.monthValue}/${it.date.dayOfMonth} 约 ${fmt(it.precipSum, 1)} 毫米" } ?: "未来几天无明显降水"),
    )
}

@Composable
private fun VisibilityTile(fc: Forecast, m: Modifier) = GlassCard("能见度", Icons.Rounded.Visibility, m) {
    val km = fc.current.visibility / 1000
    Big(if (km.isNaN()) "—" else if (km >= 10) fmt(km) else fmt(km, 1), "公里")
    Note(
        when {
            km.isNaN() -> ""
            km >= 10 -> "视野非常清晰"
            km >= 4 -> "视野良好"
            km >= 1 -> "有轻雾或霾"
            else -> "能见度很低，注意出行安全"
        },
    )
}

@Composable
private fun PressureTile(fc: Forecast, m: Modifier) = GlassCard("气压", Icons.Rounded.Compress, m) {
    Big(fmt(fc.current.pressure), "hPa")
    val p = fc.current.pressure
    Note(if (p.isNaN()) "" else if (p < 1000) "低气压，天气可能转差" else if (p > 1025) "高气压，天气多稳定" else "气压正常")
}

@Composable
private fun CloudTile(fc: Forecast, m: Modifier) = GlassCard("云量", Icons.Rounded.Cloud, m) {
    Big("${fc.current.cloud}", "%")
    Spacer(Modifier.height(8.dp))
    ScaleBar(fc.current.cloud / 100f, listOf(Color(0xFF6EC6FF), Color(0xFFE0E6EE)))
}
