package app.weatherpad

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/* ---------------- 模型 ---------------- */

data class Place(val key: String, val name: String, val region: String, val lat: Double, val lon: Double) {
    val isLocation get() = key == LOCATION_KEY

    fun toJson(): JSONObject = JSONObject().put("key", key).put("name", name).put("region", region).put("lat", lat).put("lon", lon)

    companion object {
        const val LOCATION_KEY = "loc"
        fun fromJson(o: JSONObject) = Place(o.getString("key"), o.getString("name"), o.optString("region"), o.getDouble("lat"), o.getDouble("lon"))
    }
}

data class Current(
    val time: LocalDateTime,
    val temp: Double,
    val feels: Double,
    val humidity: Int,
    val dewPoint: Double,
    val isDay: Boolean,
    val precip: Double,
    val code: Int,
    val cloud: Int,
    val pressure: Double,
    val wind: Double,
    val windDir: Double,
    val gusts: Double,
    val visibility: Double,
)

data class Hour(val time: LocalDateTime, val temp: Double, val pop: Int, val code: Int, val isDay: Boolean)

data class Day(
    val date: LocalDate,
    val code: Int,
    val max: Double,
    val min: Double,
    val sunrise: LocalDateTime?,
    val sunset: LocalDateTime?,
    val uv: Double,
    val pop: Int,
    val precipSum: Double,
    val windMax: Double,
)

data class Air(val usAqi: Int, val pm25: Double, val pm10: Double)

data class Forecast(
    val zone: ZoneId,
    val current: Current,
    val hours: List<Hour>,
    val days: List<Day>,
    val air: Air?,
    val fetchedAt: Long,
) {
    fun now(): LocalDateTime = LocalDateTime.now(zone)

    /** 从当前整点起的 n 小时；第一格用实时数据覆盖 */
    fun upcoming(n: Int): List<Hour> {
        val start = now().truncatedTo(ChronoUnit.HOURS)
        val list = hours.filter { !it.time.isBefore(start) }.take(n)
        if (list.isEmpty()) return list
        return listOf(list[0].copy(temp = current.temp, code = current.code, isDay = current.isDay)) + list.drop(1)
    }

    /** 今天及以后的逐日预报 */
    fun upcomingDays(): List<Day> {
        val today = now().toLocalDate()
        return days.filter { !it.date.isBefore(today) }
    }

    companion object {
        fun parse(f: JSONObject, a: JSONObject?, fetchedAt: Long): Forecast {
            val zone = runCatching { ZoneId.of(f.getString("timezone")) }.getOrDefault(ZoneId.systemDefault())
            val c = f.getJSONObject("current")
            val current = Current(
                time = LocalDateTime.parse(c.getString("time")),
                temp = c.optDouble("temperature_2m"),
                feels = c.optDouble("apparent_temperature"),
                humidity = c.optInt("relative_humidity_2m"),
                dewPoint = c.optDouble("dew_point_2m"),
                isDay = c.optInt("is_day", 1) == 1,
                precip = c.optDouble("precipitation", 0.0),
                code = c.optInt("weather_code"),
                cloud = c.optInt("cloud_cover"),
                pressure = c.optDouble("pressure_msl"),
                wind = c.optDouble("wind_speed_10m"),
                windDir = c.optDouble("wind_direction_10m"),
                gusts = c.optDouble("wind_gusts_10m"),
                visibility = c.optDouble("visibility"),
            )

            val h = f.getJSONObject("hourly")
            val ht = h.getJSONArray("time")
            val hTemp = h.getJSONArray("temperature_2m")
            val hPop = h.optJSONArray("precipitation_probability") ?: JSONArray()
            val hCode = h.getJSONArray("weather_code")
            val hDay = h.getJSONArray("is_day")
            val hours = (0 until ht.length()).map { i ->
                Hour(LocalDateTime.parse(ht.getString(i)), hTemp.optDouble(i), hPop.optInt(i), hCode.optInt(i), hDay.optInt(i, 1) == 1)
            }

            val d = f.getJSONObject("daily")
            val dt = d.getJSONArray("time")
            fun arr(name: String) = d.optJSONArray(name) ?: JSONArray()
            val dCode = arr("weather_code"); val dMax = arr("temperature_2m_max"); val dMin = arr("temperature_2m_min")
            val dRise = arr("sunrise"); val dSet = arr("sunset"); val dUv = arr("uv_index_max")
            val dPop = arr("precipitation_probability_max"); val dSum = arr("precipitation_sum"); val dWind = arr("wind_speed_10m_max")
            fun time(a: JSONArray, i: Int) = a.optString(i, "").takeIf { it.length >= 16 }?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
            val days = (0 until dt.length()).map { i ->
                Day(
                    date = LocalDate.parse(dt.getString(i)),
                    code = dCode.optInt(i), max = dMax.optDouble(i), min = dMin.optDouble(i),
                    sunrise = time(dRise, i), sunset = time(dSet, i),
                    uv = dUv.optDouble(i), pop = dPop.optInt(i), precipSum = dSum.optDouble(i, 0.0), windMax = dWind.optDouble(i),
                )
            }

            val air = a?.optJSONObject("current")?.let { ac ->
                if (ac.isNull("us_aqi")) null else Air(ac.optInt("us_aqi"), ac.optDouble("pm2_5"), ac.optDouble("pm10"))
            }
            return Forecast(zone, current, hours, days, air, fetchedAt)
        }
    }
}

/* ---------------- WMO 天气代码 ---------------- */

enum class Sky { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM }

object Wmo {
    fun sky(code: Int): Sky = when (code) {
        0, 1 -> Sky.CLEAR
        2 -> Sky.PARTLY
        3 -> Sky.CLOUDY
        45, 48 -> Sky.FOG
        51, 53, 55, 56, 57 -> Sky.DRIZZLE
        61, 63, 65, 66, 67, 80, 81, 82 -> Sky.RAIN
        71, 73, 75, 77, 85, 86 -> Sky.SNOW
        95, 96, 99 -> Sky.STORM
        else -> Sky.CLOUDY
    }

    fun text(code: Int): String = when (code) {
        0 -> "晴"
        1 -> "晴间少云"
        2 -> "多云"
        3 -> "阴"
        45 -> "雾"
        48 -> "雾凇"
        51 -> "小毛毛雨"
        53 -> "毛毛雨"
        55 -> "密毛毛雨"
        56, 57 -> "冻毛毛雨"
        61 -> "小雨"
        63 -> "中雨"
        65 -> "大雨"
        66, 67 -> "冻雨"
        71 -> "小雪"
        73 -> "中雪"
        75 -> "大雪"
        77 -> "雪粒"
        80 -> "阵雨"
        81 -> "强阵雨"
        82 -> "暴雨"
        85 -> "阵雪"
        86 -> "强阵雪"
        95 -> "雷阵雨"
        96, 99 -> "雷阵雨伴冰雹"
        else -> "未知"
    }
}

/* ---------------- Open-Meteo / Nominatim ---------------- */

object OpenMeteo {
    private const val UA = "WeatherPad/0.1 (Android; open-source weather client)"

    private val CURRENT = listOf(
        "temperature_2m", "relative_humidity_2m", "apparent_temperature", "dew_point_2m", "is_day", "precipitation",
        "weather_code", "cloud_cover", "pressure_msl", "wind_speed_10m", "wind_direction_10m", "wind_gusts_10m", "visibility",
    ).joinToString(",")
    private const val HOURLY = "temperature_2m,precipitation_probability,weather_code,is_day"
    private const val DAILY = "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max," +
        "precipitation_probability_max,precipitation_sum,wind_speed_10m_max"

    private fun get(url: String): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 12000
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept", "application/json")
        try {
            val code = c.responseCode
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(c.errorStream.bufferedReader().readText()).optString("reason") }.getOrNull()
                throw IOException(msg?.takeIf { it.isNotBlank() } ?: "HTTP $code")
            }
            return JSONObject(c.inputStream.bufferedReader().readText())
        } finally {
            c.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    suspend fun search(q: String): List<Place> = withContext(Dispatchers.IO) {
        val arr = get("https://geocoding-api.open-meteo.com/v1/search?name=${enc(q)}&count=15&language=zh&format=json")
            .optJSONArray("results") ?: return@withContext emptyList()
        (0 until arr.length()).map { arr.getJSONObject(it) }.map { o ->
            val name = o.getString("name")
            val region = listOf("admin2", "admin1", "country").map { o.optString(it) }
                .filter { it.isNotBlank() && it != name }.distinct().joinToString(" · ")
            Place(o.getLong("id").toString(), name, region, o.getDouble("latitude"), o.getDouble("longitude"))
        }
    }

    /** 返回 (天气预报, 空气质量)；空气质量失败不影响主数据 */
    suspend fun fetch(p: Place): Pair<JSONObject, JSONObject?> = coroutineScope {
        val ll = "latitude=${p.lat}&longitude=${p.lon}"
        val f = async(Dispatchers.IO) {
            get("https://api.open-meteo.com/v1/forecast?$ll&current=$CURRENT&hourly=$HOURLY&daily=$DAILY&timezone=auto&forecast_days=10")
        }
        val a = async(Dispatchers.IO) {
            runCatching { get("https://air-quality-api.open-meteo.com/v1/air-quality?$ll&current=us_aqi,pm2_5,pm10&timezone=auto") }.getOrNull()
        }
        f.await() to a.await()
    }

    /** OpenStreetMap Nominatim 反向地理编码，拿当前位置的城市名 */
    suspend fun reverse(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        runCatching {
            val o = get("https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=$lat&lon=$lon&zoom=12&accept-language=zh-CN")
            val a = o.optJSONObject("address") ?: return@runCatching null
            listOf("city_district", "district", "county", "city", "town", "state").map { a.optString(it) }.firstOrNull { it.isNotBlank() }
        }.getOrNull()
    }
}
