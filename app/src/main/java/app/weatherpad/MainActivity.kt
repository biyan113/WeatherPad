package app.weatherpad

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import app.weatherpad.ui.WeatherRoot
import app.weatherpad.ui.WeatherTheme
import app.weatherpad.widget.Widgets

class MainActivity : ComponentActivity() {
    private val vm: WeatherViewModel by viewModels()

    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) vm.locate()
    }

    private fun requestLocation() {
        askLocation.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 只在首次启动时主动请求一次，之后由“使用我的位置”按钮触发
        val sp = getSharedPreferences("weather", MODE_PRIVATE)
        if (!sp.getBoolean("asked_location", false) && !DeviceLocation.granted(this)) {
            sp.edit().putBoolean("asked_location", true).apply()
            requestLocation()
        }
        Widgets.schedule(this)
        handleIntent(intent)
        setContent { WeatherTheme { WeatherRoot(vm, onRequestLocation = ::requestLocation) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** 从“城市列表”小组件点进来时切到对应城市 */
    private fun handleIntent(intent: Intent?) {
        val key = intent?.getStringExtra(EXTRA_CITY) ?: return
        if (vm.state.value.places.any { it.key == key }) vm.select(key)
        intent.removeExtra(EXTRA_CITY)
    }

    override fun onResume() {
        super.onResume()
        vm.refreshAll(force = false)
        if (DeviceLocation.granted(this)) vm.locate()
    }

    companion object {
        const val EXTRA_CITY = "city"
    }
}
