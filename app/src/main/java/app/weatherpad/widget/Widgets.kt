package app.weatherpad.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.weatherpad.Forecast
import app.weatherpad.MainActivity
import app.weatherpad.Place
import app.weatherpad.Wmo
import app.weatherpad.ui.deg
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

/* ---------------- 共用 ---------------- */

private val White = ColorProvider(Color.White)
private val Dim = ColorProvider(Color.White.copy(alpha = 0.75f))
private val RainBlue = ColorProvider(Color(0xFF9AD4FF))

private fun txt(size: TextUnit, color: ColorProvider = White, weight: FontWeight = FontWeight.Normal, align: TextAlign = TextAlign.Start) =
    TextStyle(color = color, fontSize = size, fontWeight = weight, textAlign = align)

private fun openApp(ctx: Context, cityKey: String? = null) = actionStartActivity(
    Intent(ctx, MainActivity::class.java)
        .setAction(Intent.ACTION_VIEW)
        .setData(Uri.parse("weatherpad://city/${cityKey ?: "current"}"))
        .apply { if (cityKey != null) putExtra(MainActivity.EXTRA_CITY, cityKey) }
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
)

@Composable
private fun rememberData(): WidgetData {
    val ctx = LocalContext.current
    val v by Widgets.version.collectAsState()
    return remember(v) { WidgetData.load(ctx) }
}

/** 渐变背景 + 圆角 + 点击打开 App 的外壳 */
@Composable
private fun Shell(fc: Forecast?, cityKey: String?, padding: Dp = 14.dp, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    Box(
        GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(24.dp)
            .background(ImageProvider(WidgetArt.background(fc)), ContentScale.FillBounds)
            .clickable(openApp(ctx, cityKey))
            .padding(padding),
    ) { content() }
}

@Composable
private fun Icon(code: Int, day: Boolean, size: Dp) {
    Image(ImageProvider(WidgetArt.icon(code, day)), Wmo.text(code), GlanceModifier.size(size))
}

@Composable
private fun Empty(text: String = "打开天气添加城市") {
    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = txt(13.sp, Dim, align = TextAlign.Center))
    }
}

private fun Place.title() = if (isLocation) "➤ $name" else name
private fun Forecast.today() = upcomingDays().firstOrNull()
private fun Forecast.hiLo() = today()?.let { "最高 ${deg(it.max)}  最低 ${deg(it.min)}" } ?: ""

/** 逐小时一行，列数按可用宽度决定 */
@Composable
private fun HourlyRow(fc: Forecast, count: Int) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        fc.upcoming(count).forEachIndexed { i, h ->
            Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (i == 0) "现在" else "${h.time.hour}时", style = txt(12.sp, Dim, align = TextAlign.Center))
                Spacer(GlanceModifier.height(4.dp))
                Icon(h.code, h.isDay, 24.dp)
                Spacer(GlanceModifier.height(2.dp))
                Text(deg(h.temp), style = txt(14.sp, weight = FontWeight.Medium, align = TextAlign.Center))
            }
        }
    }
}

/* ---------------- 1. 小号 2×2 ---------------- */

class SmallWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent { Content() }

    @Composable
    private fun Content() {
        val d = rememberData()
        val p = d.place; val fc = d.fc
        Shell(fc, p?.key) {
            if (p == null || fc == null) return@Shell Empty()
            Column(GlanceModifier.fillMaxSize()) {
                Text(p.title(), style = txt(15.sp, weight = FontWeight.Bold), maxLines = 1)
                Text(deg(fc.current.temp), style = txt(46.sp))
                Spacer(GlanceModifier.defaultWeight())
                Icon(fc.current.code, fc.current.isDay, 30.dp)
                Text(Wmo.text(fc.current.code), style = txt(13.sp, weight = FontWeight.Medium), maxLines = 1)
                Text(fc.hiLo(), style = txt(12.sp, Dim), maxLines = 1)
            }
        }
    }
}

/* ---------------- 2. 条形 4×1 ---------------- */

class StripWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent { Content() }

    @Composable
    private fun Content() {
        val d = rememberData()
        val p = d.place; val fc = d.fc
        Shell(fc, p?.key, padding = 10.dp) {
            if (p == null || fc == null) return@Shell Empty()
            Row(GlanceModifier.fillMaxSize().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(fc.current.code, fc.current.isDay, 38.dp)
                Spacer(GlanceModifier.width(8.dp))
                Text(deg(fc.current.temp), style = txt(30.sp))
                Spacer(GlanceModifier.width(12.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text(p.title(), style = txt(14.sp, weight = FontWeight.Bold), maxLines = 1)
                    Text("${Wmo.text(fc.current.code)} · ${fc.hiLo()}", style = txt(12.sp, Dim), maxLines = 1)
                }
                fc.air?.let { a ->
                    if (LocalSize.current.width >= 300.dp) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("AQI ${a.usAqi}", style = txt(14.sp, weight = FontWeight.Medium))
                            Text(aqiLevel(a.usAqi), style = txt(12.sp, Dim))
                        }
                    }
                }
            }
        }
    }
}

/* ---------------- 3. 中号 4×2 ---------------- */

class MediumWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent { Content() }

    @Composable
    private fun Content() {
        val d = rememberData()
        val p = d.place; val fc = d.fc
        val size = LocalSize.current
        Shell(fc, p?.key) {
            if (p == null || fc == null) return@Shell Empty()
            Column(GlanceModifier.fillMaxSize()) {
                Header(p, fc, bigTemp = 40.sp)
                Spacer(GlanceModifier.defaultWeight())
                if (size.height >= 130.dp) HourlyRow(fc, ((size.width - 28.dp) / 52.dp).toInt().coerceIn(3, 8))
            }
        }
    }
}

@Composable
private fun Header(p: Place, fc: Forecast, bigTemp: TextUnit) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(GlanceModifier.defaultWeight()) {
            Text(p.title(), style = txt(15.sp, weight = FontWeight.Bold), maxLines = 1)
            Text(deg(fc.current.temp), style = txt(bigTemp))
        }
        Column(horizontalAlignment = Alignment.End) {
            Icon(fc.current.code, fc.current.isDay, 32.dp)
            Text(Wmo.text(fc.current.code), style = txt(13.sp, weight = FontWeight.Medium, align = TextAlign.End), maxLines = 1)
            Text(fc.hiLo(), style = txt(12.sp, Dim, align = TextAlign.End), maxLines = 1)
            fc.air?.let { Text("空气 ${aqiLevel(it.usAqi)} ${it.usAqi}", style = txt(12.sp, Dim, align = TextAlign.End), maxLines = 1) }
        }
    }
}

/* ---------------- 4. 大号 4×4 ---------------- */

class LargeWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent { Content() }

    @Composable
    private fun Content() {
        val d = rememberData()
        val p = d.place; val fc = d.fc
        val size = LocalSize.current
        Shell(fc, p?.key, padding = 16.dp) {
            if (p == null || fc == null) return@Shell Empty()
            Column(GlanceModifier.fillMaxSize()) {
                Header(p, fc, bigTemp = 48.sp)
                Spacer(GlanceModifier.height(10.dp))
                HourlyRow(fc, ((size.width - 32.dp) / 52.dp).toInt().coerceIn(4, 8))
                Spacer(GlanceModifier.height(10.dp))
                val days = fc.upcomingDays()
                // 头部 + 逐小时约占 190dp，剩下的按每行 30dp 放逐日
                // Glance 每个容器最多 10 个子元素，逐日行单独放一个 Column
                val n = ((size.height - 200.dp) / 30.dp).toInt().coerceIn(0, minOf(days.size, 10))
                val shown = days.take(n)
                if (shown.isNotEmpty()) Column(GlanceModifier.fillMaxWidth()) {
                    val lo = shown.minOf { if (it.min.isNaN()) Double.MAX_VALUE else it.min }
                    val hi = shown.maxOf { if (it.max.isNaN()) -Double.MAX_VALUE else it.max }
                    shown.forEachIndexed { i, day ->
                        Row(GlanceModifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
                            val label = when (i) {
                                0 -> "今天"
                                1 -> "明天"
                                else -> day.date.dayOfWeek.getDisplayName(DateTextStyle.SHORT, Locale.CHINA)
                            }
                            Text(label, style = txt(14.sp, weight = FontWeight.Medium), modifier = GlanceModifier.width(44.dp))
                            Icon(day.code, true, 22.dp)
                            Text(if (day.pop >= 20) "${day.pop}%" else "", style = txt(11.sp, RainBlue), modifier = GlanceModifier.width(36.dp).padding(start = 4.dp))
                            Text(deg(day.min), style = txt(14.sp, Dim, align = TextAlign.End), modifier = GlanceModifier.width(36.dp))
                            Image(
                                ImageProvider(WidgetArt.tempBar(day.min, day.max, lo, hi)), null,
                                GlanceModifier.defaultWeight().height(6.dp).padding(horizontal = 8.dp), contentScale = ContentScale.FillBounds,
                            )
                            Text(deg(day.max), style = txt(14.sp, weight = FontWeight.Medium), modifier = GlanceModifier.width(36.dp))
                        }
                    }
                }
            }
        }
    }
}

/* ---------------- 5. 城市列表 ---------------- */

class CitiesWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent { Content() }

    @Composable
    private fun Content() {
        val d = rememberData()
        val ctx = LocalContext.current
        Shell(d.fc, null, padding = 12.dp) {
            if (d.all.isEmpty()) return@Shell Empty()
            Column(GlanceModifier.fillMaxSize()) {
                Text("天气", style = txt(16.sp, weight = FontWeight.Bold), modifier = GlanceModifier.padding(start = 4.dp, bottom = 6.dp))
                LazyColumn(GlanceModifier.fillMaxSize()) {
                    items(d.all, itemId = { it.first.key.hashCode().toLong() }) { (p, fc) ->
                        Column(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Row(
                                GlanceModifier.fillMaxWidth().cornerRadius(14.dp).background(Color.White.copy(alpha = 0.14f))
                                    .clickable(openApp(ctx, p.key)).padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(GlanceModifier.defaultWeight()) {
                                    Text(p.title(), style = txt(15.sp, weight = FontWeight.Bold), maxLines = 1)
                                    Text(fc?.let { "${Wmo.text(it.current.code)} · ${it.hiLo()}" } ?: "暂无数据", style = txt(12.sp, Dim), maxLines = 1)
                                }
                                if (fc != null) {
                                    Icon(fc.current.code, fc.current.isDay, 28.dp)
                                    Spacer(GlanceModifier.width(8.dp))
                                    Text(deg(fc.current.temp), style = txt(24.sp, align = TextAlign.End), modifier = GlanceModifier.width(52.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ---------------- Receivers ---------------- */

abstract class BaseReceiver : GlanceAppWidgetReceiver() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Widgets.schedule(context)
        Widgets.refreshNow(context)
    }
}

class SmallWidgetReceiver : BaseReceiver() { override val glanceAppWidget: GlanceAppWidget = SmallWidget() }
class StripWidgetReceiver : BaseReceiver() { override val glanceAppWidget: GlanceAppWidget = StripWidget() }
class MediumWidgetReceiver : BaseReceiver() { override val glanceAppWidget: GlanceAppWidget = MediumWidget() }
class LargeWidgetReceiver : BaseReceiver() { override val glanceAppWidget: GlanceAppWidget = LargeWidget() }
class CitiesWidgetReceiver : BaseReceiver() { override val glanceAppWidget: GlanceAppWidget = CitiesWidget() }
