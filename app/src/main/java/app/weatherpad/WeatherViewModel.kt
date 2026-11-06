package app.weatherpad

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.weatherpad.widget.Widgets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.SocketTimeoutException
import java.net.UnknownHostException

data class UiState(
    val location: Place? = null,
    val locating: Boolean = false,
    val saved: List<Place> = emptyList(),
    val selected: String? = null,
    val forecasts: Map<String, Forecast> = emptyMap(),
    val loading: Set<String> = emptySet(),
    val errors: Map<String, String> = emptyMap(),
    val query: String = "",
    val results: List<Place> = emptyList(),
    val searching: Boolean = false,
    val searchError: String? = null,
) {
    val places: List<Place> get() = listOfNotNull(location) + saved
    val current: Place? get() = places.firstOrNull { it.key == selected } ?: places.firstOrNull()
}

class WeatherViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()
    private var searchJob: Job? = null

    init {
        val saved = store.places() ?: DEFAULTS.also { store.savePlaces(it) }
        val loc = store.location
        val all = listOfNotNull(loc) + saved
        _state.value = UiState(
            location = loc,
            saved = saved,
            selected = store.selected?.takeIf { k -> all.any { it.key == k } } ?: all.firstOrNull()?.key,
            forecasts = all.mapNotNull { p -> store.loadForecast(p.key)?.let { p.key to it } }.toMap(),
        )
        refreshAll(force = false)
    }

    fun refreshAll(force: Boolean) {
        val s = _state.value
        s.places.forEach { p ->
            val fc = s.forecasts[p.key]
            if (force || fc == null || System.currentTimeMillis() - fc.fetchedAt > STALE_MS) refresh(p)
        }
    }

    fun refresh(p: Place) {
        if (p.key in _state.value.loading) return
        _state.update { it.copy(loading = it.loading + p.key) }
        viewModelScope.launch {
            try {
                val (f, a) = OpenMeteo.fetch(p)
                val t = System.currentTimeMillis()
                val fc = Forecast.parse(f, a, t)
                store.saveForecast(p.key, f, a, t)
                _state.update { it.copy(forecasts = it.forecasts + (p.key to fc), errors = it.errors - p.key) }
                Widgets.notify(getApplication())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(errors = it.errors + (p.key to friendly(e))) }
            } finally {
                _state.update { it.copy(loading = it.loading - p.key) }
            }
        }
    }

    /** 已授权定位后调用：更新“我的位置” */
    fun locate() {
        if (_state.value.locating) return
        _state.update { it.copy(locating = true) }
        viewModelScope.launch {
            try {
                val l = DeviceLocation.get(getApplication()) ?: return@launch
                val prev = _state.value.location
                val moved = prev == null || distanceKm(prev.lat, prev.lon, l.latitude, l.longitude) > 2.0
                if (!moved) {
                    refreshAll(force = false)
                    return@launch
                }
                val name = OpenMeteo.reverse(l.latitude, l.longitude) ?: "我的位置"
                val p = Place(Place.LOCATION_KEY, name, "当前位置", l.latitude, l.longitude)
                store.location = p
                _state.update { it.copy(location = p, selected = if (prev == null) p.key else it.selected) }
                if (prev == null) store.selected = p.key
                refresh(p)
            } finally {
                _state.update { it.copy(locating = false) }
            }
        }
    }

    fun select(key: String) {
        store.selected = key
        _state.update { it.copy(selected = key) }
        Widgets.notify(getApplication())
        val s = _state.value
        val p = s.places.firstOrNull { it.key == key } ?: return
        val fc = s.forecasts[key]
        if (fc == null || System.currentTimeMillis() - fc.fetchedAt > STALE_MS) refresh(p)
    }

    fun search(q: String) {
        _state.update { it.copy(query = q, searchError = null) }
        searchJob?.cancel()
        if (q.isBlank()) {
            _state.update { it.copy(results = emptyList(), searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            _state.update { it.copy(searching = true) }
            try {
                val r = OpenMeteo.search(q.trim())
                _state.update { it.copy(results = r, searching = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(results = emptyList(), searching = false, searchError = friendly(e)) }
            }
        }
    }

    fun add(p: Place) {
        val s = _state.value
        if (s.saved.none { it.key == p.key }) {
            val saved = s.saved + p
            store.savePlaces(saved)
            _state.update { it.copy(saved = saved) }
        }
        searchJob?.cancel()
        _state.update { it.copy(query = "", results = emptyList(), searching = false) }
        select(p.key)
    }

    fun remove(key: String) {
        val saved = _state.value.saved.filterNot { it.key == key }
        store.savePlaces(saved)
        store.dropForecast(key)
        _state.update { it.copy(saved = saved, forecasts = it.forecasts - key, selected = if (it.selected == key) null else it.selected) }
        if (store.selected == key) store.selected = null
        Widgets.notify(getApplication())
    }

    private fun friendly(e: Exception): String = when (e) {
        is UnknownHostException -> "网络不可用"
        is SocketTimeoutException -> "请求超时"
        else -> e.message ?: e.javaClass.simpleName
    }

    private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, r)
        return r[0] / 1000.0
    }

    companion object {
        private const val STALE_MS = 15 * 60_000L
        private val DEFAULTS = listOf(
            Place("1816670", "北京", "北京 · 中国", 39.9075, 116.39723),
            Place("1796236", "上海", "上海 · 中国", 31.22222, 121.45806),
        )
    }
}
