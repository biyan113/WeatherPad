package app.weatherpad

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

/** 城市列表和每个城市最近一次的原始响应都存在 SharedPreferences，离线也能打开 */
class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("weather", Context.MODE_PRIVATE)

    fun places(): List<Place>? = sp.getString("places", null)?.let { s ->
        runCatching { JSONArray(s).let { a -> (0 until a.length()).map { Place.fromJson(a.getJSONObject(it)) } } }.getOrNull()
    }

    fun savePlaces(list: List<Place>) {
        sp.edit().putString("places", JSONArray(list.map { it.toJson() }).toString()).apply()
    }

    var location: Place?
        get() = sp.getString("location", null)?.let { runCatching { Place.fromJson(JSONObject(it)) }.getOrNull() }
        set(v) { sp.edit().putString("location", v?.toJson()?.toString()).apply() }

    var selected: String?
        get() = sp.getString("selected", null)
        set(v) { sp.edit().putString("selected", v).apply() }

    fun saveForecast(key: String, f: JSONObject, a: JSONObject?, t: Long) {
        val o = JSONObject().put("f", f).put("a", a ?: JSONObject.NULL).put("t", t)
        sp.edit().putString("fc_$key", o.toString()).apply()
    }

    fun loadForecast(key: String): Forecast? = runCatching {
        val o = JSONObject(sp.getString("fc_$key", null) ?: return null)
        Forecast.parse(o.getJSONObject("f"), o.optJSONObject("a"), o.getLong("t"))
    }.getOrNull()

    fun dropForecast(key: String) { sp.edit().remove("fc_$key").apply() }
}

/** 只用系统 LocationManager，不依赖 Google Play 服务（国行平板也能用） */
object DeviceLocation {
    fun granted(ctx: Context) = listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        .any { ctx.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    @SuppressLint("MissingPermission")
    suspend fun get(ctx: Context): Location? {
        if (!granted(ctx)) return null
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        val providers = runCatching { lm.getProviders(true) }.getOrDefault(emptyList())
        val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 30 * 60_000) return last

        val provider = listOf("fused", LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).firstOrNull { it in providers }
            ?: return last
        val fresh = withTimeoutOrNull(12_000) {
            suspendCancellableCoroutine<Location?> { cont ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    lm.getCurrentLocation(provider, signal, ctx.mainExecutor) { if (cont.isActive) cont.resume(it) }
                } else {
                    val l = object : LocationListener {
                        override fun onLocationChanged(loc: Location) {
                            lm.removeUpdates(this)
                            if (cont.isActive) cont.resume(loc)
                        }
                    }
                    @Suppress("DEPRECATION")
                    lm.requestLocationUpdates(provider, 0L, 0f, l, Looper.getMainLooper())
                    cont.invokeOnCancellation { lm.removeUpdates(l) }
                }
            }
        }
        return fresh ?: last
    }
}
