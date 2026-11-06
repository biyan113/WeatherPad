package app.weatherpad.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.weatherpad.Forecast
import app.weatherpad.OpenMeteo
import app.weatherpad.Place
import app.weatherpad.Sky
import app.weatherpad.Store
import app.weatherpad.Wmo
import app.weatherpad.ui.drawWeather
import app.weatherpad.ui.skyGradient
import app.weatherpad.ui.tempColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** 小组件读的数据：跟随 App 当前选中的城市 + 全部城市列表（都来自本地缓存） */
data class WidgetData(val place: Place?, val fc: Forecast?, val all: List<Pair<Place, Forecast?>>) {
    companion object {
        fun load(ctx: Context): WidgetData {
            val store = Store(ctx)
            val places = listOfNotNull(store.location) + (store.places() ?: emptyList())
            val sel = places.firstOrNull { it.key == store.selected } ?: places.firstOrNull()
            val all = places.map { it to store.loadForecast(it.key) }
            return WidgetData(sel, all.firstOrNull { it.first.key == sel?.key }?.second, all)
        }
    }
}

object Widgets {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _version = MutableStateFlow(0)
    /** 数据版本号；小组件在组合里订阅它，变了就重新读缓存 */
    val version = _version.asStateFlow()

    fun notify(ctx: Context) {
        _version.value++
        val app = ctx.applicationContext
        scope.launch {
            listOf(SmallWidget(), StripWidget(), MediumWidget(), LargeWidget(), CitiesWidget()).forEach {
                runCatching { it.updateAll(app) }
            }
        }
    }

    fun schedule(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        wm.enqueueUniquePeriodicWork(
            "weather-refresh", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RefreshWorker>(30, TimeUnit.MINUTES).setConstraints(net).build(),
        )
    }

    fun refreshNow(ctx: Context) {
        val net = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            "weather-refresh-now", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<RefreshWorker>().setConstraints(net).build(),
        )
    }
}

/** 后台每 30 分钟拉一次所有城市（“我的位置”用上次定位的坐标） */
class RefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val store = Store(applicationContext)
        val places = listOfNotNull(store.location) + (store.places() ?: emptyList())
        var failed = false
        for (p in places) {
            runCatching {
                val (f, a) = OpenMeteo.fetch(p)
                store.saveForecast(p.key, f, a, System.currentTimeMillis())
            }.onFailure { failed = true }
        }
        Widgets.notify(applicationContext)
        return if (failed && runAttemptCount < 3) Result.retry() else Result.success()
    }
}

/* ---------------- 位图（Glance 里不能直接用 Canvas） ---------------- */

object WidgetArt {
    private val icons = HashMap<String, Bitmap>()

    /** 复用 App 里的 Compose 绘制代码画天气图标 */
    fun icon(code: Int, day: Boolean, px: Int = 96): Bitmap = synchronized(icons) {
        icons.getOrPut("$code-$day-$px") {
            val img = ImageBitmap(px, px)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(img), Size(px.toFloat(), px.toFloat())) {
                drawWeather(Wmo.sky(code), day, code, px.toFloat())
            }
            img.asAndroidBitmap()
        }
    }

    fun background(fc: Forecast?): Bitmap {
        val (top, bottom) = skyGradient(fc?.let { Wmo.sky(it.current.code) } ?: Sky.PARTLY, fc?.current?.isDay ?: true)
        val bmp = Bitmap.createBitmap(4, 160, Bitmap.Config.ARGB_8888)
        val p = Paint().apply { shader = LinearGradient(0f, 0f, 0f, 160f, top.toArgb(), bottom.toArgb(), Shader.TileMode.CLAMP) }
        android.graphics.Canvas(bmp).drawRect(0f, 0f, 4f, 160f, p)
        return bmp
    }

    /** 逐日温度区间条 */
    fun tempBar(min: Double, max: Double, lo: Double, hi: Double, w: Int = 240, h: Int = 14): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val r = h / 2f
        c.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 })
        if (min.isNaN() || max.isNaN() || hi <= lo) return bmp
        fun x(t: Double) = ((t - lo) / (hi - lo)).toFloat().coerceIn(0f, 1f) * w
        val a = x(min); val b = x(max).coerceAtLeast(a + h)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(a, 0f, b, 0f, tempColor(min).toArgb(), tempColor(max).toArgb(), Shader.TileMode.CLAMP)
        }
        c.drawRoundRect(RectF(a, 0f, b, h.toFloat()), r, r, p)
        return bmp
    }
}

fun aqiLevel(v: Int) = when {
    v <= 50 -> "优"
    v <= 100 -> "良"
    v <= 150 -> "轻度污染"
    v <= 200 -> "中度污染"
    v <= 300 -> "重度污染"
    else -> "严重污染"
}
