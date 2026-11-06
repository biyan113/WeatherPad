package app.weatherpad.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.weatherpad.Forecast
import app.weatherpad.Place
import app.weatherpad.Sky
import app.weatherpad.UiState
import app.weatherpad.WeatherViewModel
import app.weatherpad.Wmo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val HM = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun WeatherRoot(vm: WeatherViewModel, onRequestLocation: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val place = s.current
    val fc = place?.let { s.forecasts[it.key] }
    val (top, bottom) = skyGradient(fc?.let { Wmo.sky(it.current.code) } ?: Sky.PARTLY, fc?.current?.isDay ?: true)
    val t by animateColorAsState(top, tween(900), label = "top")
    val b by animateColorAsState(bottom, tween(900), label = "bottom")

    BoxWithConstraints(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(t, b)))) {
        if (maxWidth >= 840.dp) {
            Row(Modifier.fillMaxSize()) {
                CityPanel(
                    s, vm, onRequestLocation,
                    Modifier.width(340.dp).fillMaxHeight().background(Color.Black.copy(alpha = 0.16f))
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical)),
                )
                WeatherPane(s, place, fc, vm, onMenu = null, Modifier.weight(1f))
            }
        } else {
            val drawer = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            ModalNavigationDrawer(
                drawerState = drawer,
                drawerContent = {
                    ModalDrawerSheet(Modifier.width(360.dp), drawerContainerColor = W.Drawer) {
                        CityPanel(s, vm, onRequestLocation, Modifier.fillMaxSize(), onPicked = { scope.launch { drawer.close() } })
                    }
                },
            ) {
                WeatherPane(s, place, fc, vm, onMenu = { scope.launch { drawer.open() } }, Modifier.fillMaxSize())
            }
        }
    }
}

/* ---------------- 城市列表 ---------------- */

@Composable
private fun CityPanel(s: UiState, vm: WeatherViewModel, onRequestLocation: () -> Unit, modifier: Modifier, onPicked: () -> Unit = {}) {
    Column(modifier.padding(horizontal = 16.dp)) {
        Text("天气", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 20.dp, bottom = 14.dp, start = 4.dp))
        SearchField(s.query, vm::search)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.weight(1f)) {
            if (s.query.isNotBlank()) {
                SearchResults(s, onPick = { vm.add(it); onPicked() })
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                    if (s.location == null) item { LocateCard(s.locating, onRequestLocation) }
                    items(s.places, key = { it.key }) { p ->
                        CityCard(
                            p, s.forecasts[p.key], selected = p.key == s.current?.key, loading = p.key in s.loading,
                            onClick = { vm.select(p.key); onPicked() },
                            onDelete = if (p.isLocation) null else ({ vm.remove(p.key) }),
                        )
                    }
                }
            }
        }
        Text(
            "数据：Open-Meteo（CC BY 4.0）· OpenStreetMap",
            fontSize = 11.sp, color = W.Faint, modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
        )
    }
}

@Composable
private fun SearchField(q: String, onChange: (String) -> Unit) {
    BasicTextField(
        value = q, onValueChange = onChange, singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
        cursorBrush = SolidColor(Color.White),
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(W.Glass).padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = W.Dim, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (q.isEmpty()) Text("搜索城市（中文 / 拼音 / 英文）", color = W.Faint, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    inner()
                }
                if (q.isNotEmpty()) Icon(Icons.Rounded.Close, "清除", tint = W.Dim, modifier = Modifier.size(20.dp).clickable { onChange("") })
            }
        },
    )
}

@Composable
private fun SearchResults(s: UiState, onPick: (Place) -> Unit) {
    when {
        s.searching && s.results.isEmpty() -> Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
            CircularProgressIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 2.5.dp)
        }
        s.searchError != null -> Text("搜索失败：${s.searchError}", color = W.Dim, modifier = Modifier.padding(8.dp))
        s.results.isEmpty() -> Text("没有找到匹配的城市", color = W.Dim, modifier = Modifier.padding(8.dp))
        else -> LazyColumn(Modifier.clip(RoundedCornerShape(16.dp)).background(W.Glass)) {
            items(s.results, key = { it.key }) { p ->
                val added = s.saved.any { it.key == p.key }
                Column(Modifier.fillMaxWidth().clickable { onPick(p) }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(p.name + if (added) "（已添加）" else "", style = MaterialTheme.typography.titleMedium)
                    if (p.region.isNotBlank()) Text(p.region, color = W.Dim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun LocateCard(locating: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.dp, W.GlassHi, RoundedCornerShape(18.dp))
            .clickable(enabled = !locating, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.MyLocation, null, Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(if (locating) "正在定位…" else "使用我的位置", style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CityCard(p: Place, fc: Forecast?, selected: Boolean, loading: Boolean, onClick: () -> Unit, onDelete: (() -> Unit)?) {
    var menu by remember { mutableStateOf(false) }
    val (c1, c2) = fc?.let { skyGradient(Wmo.sky(it.current.code), it.current.isDay) } ?: (W.Glass to W.Glass)
    val shape = RoundedCornerShape(18.dp)
    Box {
        Row(
            Modifier.fillMaxWidth().clip(shape).background(Brush.horizontalGradient(listOf(c1, c2)))
                .then(if (selected) Modifier.border(2.dp, Color.White.copy(alpha = 0.85f), shape) else Modifier.border(1.dp, W.Glass, shape))
                .combinedClickable(onClick = onClick, onLongClick = { if (onDelete != null) menu = true })
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (p.isLocation) {
                        Icon(Icons.Rounded.Navigation, null, Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(p.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val sub = if (p.isLocation) "我的位置" else fc?.let { LocalTime.now(it.zone).format(HM) } ?: p.region
                Text(sub, fontSize = 13.sp, color = W.Dim, maxLines = 1)
                Spacer(Modifier.height(10.dp))
                Text(fc?.let { Wmo.text(it.current.code) } ?: if (loading) "加载中…" else "—", fontSize = 13.sp, color = W.Dim)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(fc?.let { deg(it.current.temp) } ?: "", fontSize = 40.sp, fontWeight = FontWeight.Light)
                fc?.upcomingDays()?.firstOrNull()?.let { d ->
                    Text("最高 ${deg(d.max)}  最低 ${deg(d.min)}", fontSize = 12.sp, color = W.Dim)
                }
            }
        }
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("删除 ${p.name}") },
                leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                onClick = { menu = false; onDelete?.invoke() },
            )
        }
    }
}

/* ---------------- 主区域 ---------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeatherPane(s: UiState, place: Place?, fc: Forecast?, vm: WeatherViewModel, onMenu: (() -> Unit)?, modifier: Modifier) {
    val loading = place != null && place.key in s.loading
    val error = place?.let { s.errors[it.key] }
    PullToRefreshBox(
        isRefreshing = loading && fc != null,
        onRefresh = { place?.let(vm::refresh) },
        modifier = modifier,
    ) {
        val insets = WindowInsets.safeDrawing.only(
            if (onMenu == null) WindowInsetsSides.End + WindowInsetsSides.Vertical else WindowInsetsSides.Horizontal + WindowInsetsSides.Vertical,
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).windowInsetsPadding(insets)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (onMenu != null) {
                IconButton(onClick = onMenu, Modifier.padding(start = 0.dp)) { Icon(Icons.Rounded.Menu, "城市列表") }
            }
            when {
                place == null -> Message("还没有城市", "在左侧搜索并添加一个城市吧")
                fc == null && loading -> Box(Modifier.fillMaxWidth().height(400.dp), Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
                fc == null -> Message("加载失败", error ?: "未知错误", action = "重试") { vm.refresh(place) }
                else -> WeatherContent(place, fc, error)
            }
        }
    }
}

@Composable
private fun Message(title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(top = 160.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(body, color = W.Dim)
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            Button(onAction, colors = ButtonDefaults.buttonColors(containerColor = W.GlassHi, contentColor = Color.White)) { Text(action) }
        }
    }
}

@Composable
private fun WeatherContent(place: Place, fc: Forecast, error: String?) {
    Header(place, fc)
    if (error != null) {
        val at = Instant.ofEpochMilli(fc.fetchedAt).atZone(ZoneId.systemDefault()).format(HM)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x33FF6B6B)).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.WarningAmber, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("更新失败（$error），显示的是 $at 的数据", fontSize = 13.sp)
        }
    }
    HourlyCard(fc)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 640.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DailyCard(fc, Modifier.weight(1f))
                DetailGrid(fc, Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DailyCard(fc, Modifier.fillMaxWidth())
                DetailGrid(fc, Modifier.fillMaxWidth())
            }
        }
    }
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); tick++ } }
    val ago = remember(fc.fetchedAt, tick) { ((System.currentTimeMillis() - fc.fetchedAt) / 60_000).toInt() }
    Text(
        (if (ago < 1) "刚刚更新" else "$ago 分钟前更新") + " · 下拉刷新 · 天气数据来自 Open-Meteo.com",
        fontSize = 12.sp, color = W.Faint, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
private fun Header(place: Place, fc: Forecast) {
    val today = fc.upcomingDays().firstOrNull()
    Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (place.isLocation) {
                Icon(Icons.Rounded.Navigation, null, Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(place.name, fontSize = 32.sp, fontWeight = FontWeight.Medium)
        }
        if (place.region.isNotBlank() && !place.isLocation) Text(place.region, fontSize = 13.sp, color = W.Dim)
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherIcon(fc.current.code, fc.current.isDay, Modifier.size(96.dp))
            Spacer(Modifier.width(12.dp))
            Text(deg(fc.current.temp), fontSize = 104.sp, fontWeight = FontWeight.Thin)
        }
        Text(Wmo.text(fc.current.code), fontSize = 22.sp, color = W.Dim)
        if (today != null) {
            Spacer(Modifier.height(4.dp))
            Text("最高 ${deg(today.max)}   最低 ${deg(today.min)}   体感 ${deg(fc.current.feels)}", fontSize = 17.sp)
        }
    }
}
