package moe.fuqiuluo.portal.ext

import android.content.Context
import androidx.core.content.edit
import com.alibaba.fastjson2.JSON
import com.baidu.mapapi.map.BaiduMap
import moe.fuqiuluo.portal.service.MockServiceHelper
import moe.fuqiuluo.portal.ui.mock.HistoricalLocation
import moe.fuqiuluo.portal.ui.mock.HistoricalRoute
import moe.fuqiuluo.portal.ui.theme.ThemePreset
import moe.fuqiuluo.xposed.utils.FakeLoc

data class PortalConfig(
    val reportDuration: Int = 100,
    val minSatelliteCount: Int = 12,
    val speed: Double = FakeLoc.speed,
    val altitude: Double = FakeLoc.altitude,
    val accuracy: Float = FakeLoc.accuracy,
    val needOpenSELinux: Boolean = false,
    val needDowngradeToCdma: Boolean = FakeLoc.needDowngradeToCdma,
    val hookSensor: Boolean = false,
    val debug: Boolean = FakeLoc.enableDebugLog,
    val disableGetCurrentLocation: Boolean = FakeLoc.disableGetCurrentLocation,
    val disableRegisterLocationListener: Boolean = FakeLoc.disableRegisterLocationListener,
    val disableFusedProvider: Boolean = FakeLoc.disableFusedLocation,
    val enableRequestGeofence: Boolean = !FakeLoc.disableRequestGeofence,
    val enableGetFromLocation: Boolean = !FakeLoc.disableGetFromLocation,
    val enableAGPS: Boolean = FakeLoc.enableAGPS,
    val enableNMEA: Boolean = FakeLoc.enableNMEA,
    val disableWifiScan: Boolean = false,
    val stableStaticLocation: Boolean = true,
    val loopBroadcastLocation: Boolean = FakeLoc.loopBroadcastLocation,
    val enableCellMock: Boolean = true,
    val preferNrCell: Boolean = true,
    val openCellIdToken: String = "pk.43c7b71717439aeed2e00c7ff0a4d27f",
    val routeMockSpeed: Float = FakeLoc.speed.toFloat(),
    val routeMockSpeedFluctuationEnabled: Boolean = false,
    val routeMockStepFrequencyEnabled: Boolean = false,
    val routeMockLoopEnabled: Boolean = false,
    val routeMockLoopCount: Int = 1,
    val routeMockLoopIntervalSeconds: Int = 0,
    val themePresetKey: String = ThemePreset.default.key,
)

object PortalPrefs {
    private const val PREFS_NAME = MockServiceHelper.PROVIDER_NAME

    private const val KEY_SELECTED_LOCATION = "selectedLocation"
    private const val KEY_SELECTED_ROUTE = "selectedRoute"
    private const val KEY_LOCATIONS = "locations"
    private const val KEY_ROUTES = "routes"
    private const val KEY_MAP_TYPE = "mapType"
    private const val KEY_ROCKER_X = "rocker_x"
    private const val KEY_ROCKER_Y = "rocker_y"

    private const val KEY_REPORT_DURATION = "reportDuration"
    private const val KEY_MIN_SATELLITE_COUNT = "minSatelliteCount"
    private const val KEY_SPEED = "speed"
    private const val KEY_ALTITUDE = "altitude"
    private const val KEY_ACCURACY = "accuracy"
    private const val KEY_NEED_OPEN_SELINUX = "needOpenSELinux"
    private const val KEY_NEED_DOWNGRADE_TO_CDMA = "needDowngradeToCdma"
    private const val KEY_HOOK_SENSOR = "hookSensor"
    private const val KEY_DEBUG = "debug"
    private const val KEY_DISABLE_GET_CURRENT_LOCATION = "disableGetCurrentLocation"
    private const val KEY_DISABLE_REGISTER_LOCATION_LISTENER = "disableRegitserLocationListener"
    private const val KEY_DISABLE_FUSED_PROVIDER = "disableFusedProvider"
    private const val KEY_ENABLE_REQUEST_GEOFENCE = "enableRequestGeofence"
    private const val KEY_ENABLE_GET_FROM_LOCATION = "enableGetFromLocation"
    private const val KEY_ENABLE_AGPS = "enableAGPS"
    private const val KEY_ENABLE_NMEA = "enableNMEA"
    private const val KEY_DISABLE_WIFI_SCAN = "disableWifiScan"
    private const val KEY_STABLE_STATIC_LOCATION = "stableStaticLocation"
    private const val KEY_LOOP_BROADCAST_LOCATION = "loopBroadcastLocation"
    private const val KEY_ENABLE_CELL_MOCK = "enableCellMock"
    private const val KEY_PREFER_NR_CELL = "preferNrCell"
    private const val KEY_OPEN_CELL_ID_TOKEN = "openCellIdToken"
    private const val KEY_ROUTE_MOCK_SPEED = "routeMockSpeed"
    private const val KEY_ROUTE_MOCK_SPEED_FLUCTUATION = "routeMockSpeedFluctuationEnabled"
    private const val KEY_ROUTE_MOCK_STEP_FREQUENCY = "routeMockStepFrequencyEnabled"
    private const val KEY_ROUTE_MOCK_LOOP_ENABLED = "routeMockLoopEnabled"
    private const val KEY_ROUTE_MOCK_LOOP_COUNT = "routeMockLoopCount"
    private const val KEY_ROUTE_MOCK_LOOP_INTERVAL = "routeMockLoopIntervalSeconds"
    private const val KEY_THEME_PRESET = "themePresetKey"
    private const val KEY_CELL_CACHE_JSON = "cellMockCacheJson"

    fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)!!

    fun readConfig(context: Context): PortalConfig {
        val prefs = prefs(context)
        val speed = prefs.getFloat(KEY_SPEED, FakeLoc.speed.toFloat()).toDouble()
        val hookSensor = prefs.getBoolean(KEY_HOOK_SENSOR, false)
        return PortalConfig(
            reportDuration = prefs.getInt(KEY_REPORT_DURATION, 100),
            minSatelliteCount = prefs.getInt(KEY_MIN_SATELLITE_COUNT, 12),
            speed = speed,
            altitude = prefs.getFloat(KEY_ALTITUDE, FakeLoc.altitude.toFloat()).toDouble(),
            accuracy = prefs.getFloat(KEY_ACCURACY, FakeLoc.accuracy),
            needOpenSELinux = prefs.getBoolean(KEY_NEED_OPEN_SELINUX, false),
            needDowngradeToCdma = prefs.getBoolean(KEY_NEED_DOWNGRADE_TO_CDMA, FakeLoc.needDowngradeToCdma),
            hookSensor = hookSensor,
            debug = prefs.getBoolean(KEY_DEBUG, FakeLoc.enableDebugLog),
            disableGetCurrentLocation = prefs.getBoolean(KEY_DISABLE_GET_CURRENT_LOCATION, FakeLoc.disableGetCurrentLocation),
            disableRegisterLocationListener = prefs.getBoolean(KEY_DISABLE_REGISTER_LOCATION_LISTENER, FakeLoc.disableRegisterLocationListener),
            disableFusedProvider = prefs.getBoolean(KEY_DISABLE_FUSED_PROVIDER, FakeLoc.disableFusedLocation),
            enableRequestGeofence = prefs.getBoolean(KEY_ENABLE_REQUEST_GEOFENCE, !FakeLoc.disableRequestGeofence),
            enableGetFromLocation = prefs.getBoolean(KEY_ENABLE_GET_FROM_LOCATION, !FakeLoc.disableGetFromLocation),
            enableAGPS = prefs.getBoolean(KEY_ENABLE_AGPS, FakeLoc.enableAGPS),
            enableNMEA = prefs.getBoolean(KEY_ENABLE_NMEA, FakeLoc.enableNMEA),
            disableWifiScan = prefs.getBoolean(KEY_DISABLE_WIFI_SCAN, false),
            stableStaticLocation = prefs.getBoolean(KEY_STABLE_STATIC_LOCATION, true),
            loopBroadcastLocation = prefs.getBoolean(KEY_LOOP_BROADCAST_LOCATION, FakeLoc.loopBroadcastLocation),
            enableCellMock = prefs.getBoolean(KEY_ENABLE_CELL_MOCK, true),
            preferNrCell = prefs.getBoolean(KEY_PREFER_NR_CELL, true),
            openCellIdToken = prefs.getString(KEY_OPEN_CELL_ID_TOKEN, "pk.43c7b71717439aeed2e00c7ff0a4d27f") ?: "",
            routeMockSpeed = prefs.getFloat(KEY_ROUTE_MOCK_SPEED, speed.toFloat()),
            routeMockSpeedFluctuationEnabled = prefs.getBoolean(KEY_ROUTE_MOCK_SPEED_FLUCTUATION, false),
            routeMockStepFrequencyEnabled = prefs.getBoolean(KEY_ROUTE_MOCK_STEP_FREQUENCY, hookSensor),
            routeMockLoopEnabled = prefs.getBoolean(KEY_ROUTE_MOCK_LOOP_ENABLED, false),
            routeMockLoopCount = prefs.getInt(KEY_ROUTE_MOCK_LOOP_COUNT, 1).coerceAtLeast(1),
            routeMockLoopIntervalSeconds = prefs.getInt(KEY_ROUTE_MOCK_LOOP_INTERVAL, 0).coerceAtLeast(0),
            themePresetKey = prefs.getString(KEY_THEME_PRESET, ThemePreset.default.key) ?: ThemePreset.default.key,
        )
    }

    fun writeConfig(context: Context, config: PortalConfig) {
        prefs(context).edit {
            putInt(KEY_REPORT_DURATION, config.reportDuration)
            putInt(KEY_MIN_SATELLITE_COUNT, config.minSatelliteCount)
            putFloat(KEY_SPEED, config.speed.toFloat())
            putFloat(KEY_ALTITUDE, config.altitude.toFloat())
            putFloat(KEY_ACCURACY, config.accuracy)
            putBoolean(KEY_NEED_OPEN_SELINUX, config.needOpenSELinux)
            putBoolean(KEY_NEED_DOWNGRADE_TO_CDMA, config.needDowngradeToCdma)
            putBoolean(KEY_HOOK_SENSOR, config.hookSensor)
            putBoolean(KEY_DEBUG, config.debug)
            putBoolean(KEY_DISABLE_GET_CURRENT_LOCATION, config.disableGetCurrentLocation)
            putBoolean(KEY_DISABLE_REGISTER_LOCATION_LISTENER, config.disableRegisterLocationListener)
            putBoolean(KEY_DISABLE_FUSED_PROVIDER, config.disableFusedProvider)
            putBoolean(KEY_ENABLE_REQUEST_GEOFENCE, config.enableRequestGeofence)
            putBoolean(KEY_ENABLE_GET_FROM_LOCATION, config.enableGetFromLocation)
            putBoolean(KEY_ENABLE_AGPS, config.enableAGPS)
            putBoolean(KEY_ENABLE_NMEA, config.enableNMEA)
            putBoolean(KEY_DISABLE_WIFI_SCAN, config.disableWifiScan)
            putBoolean(KEY_STABLE_STATIC_LOCATION, config.stableStaticLocation)
            putBoolean(KEY_LOOP_BROADCAST_LOCATION, config.loopBroadcastLocation)
            putBoolean(KEY_ENABLE_CELL_MOCK, config.enableCellMock)
            putBoolean(KEY_PREFER_NR_CELL, config.preferNrCell)
            putString(KEY_OPEN_CELL_ID_TOKEN, config.openCellIdToken)
            putFloat(KEY_ROUTE_MOCK_SPEED, config.routeMockSpeed)
            putBoolean(KEY_ROUTE_MOCK_SPEED_FLUCTUATION, config.routeMockSpeedFluctuationEnabled)
            putBoolean(KEY_ROUTE_MOCK_STEP_FREQUENCY, config.routeMockStepFrequencyEnabled)
            putBoolean(KEY_ROUTE_MOCK_LOOP_ENABLED, config.routeMockLoopEnabled)
            putInt(KEY_ROUTE_MOCK_LOOP_COUNT, config.routeMockLoopCount.coerceAtLeast(1))
            putInt(KEY_ROUTE_MOCK_LOOP_INTERVAL, config.routeMockLoopIntervalSeconds.coerceAtLeast(0))
            putString(KEY_THEME_PRESET, config.themePresetKey)
        }
        syncFakeLoc(config)
    }

    fun updateConfig(context: Context, transform: (PortalConfig) -> PortalConfig): PortalConfig {
        val updated = transform(readConfig(context))
        writeConfig(context, updated)
        return updated
    }

    private fun syncFakeLoc(config: PortalConfig) {
        FakeLoc.altitude = config.altitude
        FakeLoc.speed = config.speed
        FakeLoc.accuracy = config.accuracy
        FakeLoc.enableDebugLog = config.debug
        FakeLoc.disableGetCurrentLocation = config.disableGetCurrentLocation
        FakeLoc.disableRegisterLocationListener = config.disableRegisterLocationListener
        FakeLoc.disableFusedLocation = config.disableFusedProvider
        FakeLoc.needDowngradeToCdma = config.needDowngradeToCdma
        FakeLoc.minSatellites = config.minSatelliteCount
        FakeLoc.enableAGPS = config.enableAGPS
        FakeLoc.enableNMEA = config.enableNMEA
        FakeLoc.enableSensorMock = config.hookSensor
        FakeLoc.stableStaticLocation = config.stableStaticLocation
        FakeLoc.disableRequestGeofence = !config.enableRequestGeofence
        FakeLoc.disableGetFromLocation = !config.enableGetFromLocation
        FakeLoc.enableMockWifi = config.disableWifiScan
        FakeLoc.loopBroadcastLocation = config.loopBroadcastLocation
        FakeLoc.cellConfig.enabled = config.enableCellMock
        FakeLoc.cellConfig.preferNr = config.preferNrCell
    }

    fun getSelectedLocation(context: Context): HistoricalLocation? {
        return prefs(context).getString(KEY_SELECTED_LOCATION, null)?.let(HistoricalLocation::fromString)
    }

    fun setSelectedLocation(context: Context, value: HistoricalLocation?) {
        prefs(context).edit { putString(KEY_SELECTED_LOCATION, value?.toString()) }
    }

    fun getSelectedRoute(context: Context): HistoricalRoute? {
        return prefs(context).getString(KEY_SELECTED_ROUTE, null)?.let {
            try {
                JSON.parseObject(it, HistoricalRoute::class.java)
            } catch (_: Exception) {
                setSelectedRoute(context, null)
                null
            }
        }
    }

    fun setSelectedRoute(context: Context, value: HistoricalRoute?) {
        prefs(context).edit { putString(KEY_SELECTED_ROUTE, JSON.toJSONString(value)) }
    }

    fun getRawHistoricalLocations(context: Context): Set<String> {
        return prefs(context).getStringSet(KEY_LOCATIONS, emptySet()) ?: emptySet()
    }

    fun setRawHistoricalLocations(context: Context, value: Set<String>) {
        prefs(context).edit { putStringSet(KEY_LOCATIONS, value) }
    }

    fun getHistoricalLocations(context: Context): List<HistoricalLocation> {
        return getRawHistoricalLocations(context).map(HistoricalLocation::fromString)
    }

    fun getJsonHistoricalRoutes(context: Context): String {
        return prefs(context).getString(KEY_ROUTES, "") ?: ""
    }

    fun setJsonHistoricalRoutes(context: Context, value: String) {
        prefs(context).edit { putString(KEY_ROUTES, value) }
    }

    fun getMapType(context: Context): Int {
        return prefs(context).getInt(KEY_MAP_TYPE, BaiduMap.MAP_TYPE_NORMAL)
    }

    fun setMapType(context: Context, value: Int) {
        prefs(context).edit { putInt(KEY_MAP_TYPE, value) }
    }

    fun getRockerCoords(context: Context): Pair<Int, Int> {
        val prefs = prefs(context)
        return prefs.getInt(KEY_ROCKER_X, 0) to prefs.getInt(KEY_ROCKER_Y, 0)
    }

    fun setRockerCoords(context: Context, value: Pair<Int, Int>) {
        prefs(context).edit {
            putInt(KEY_ROCKER_X, value.first)
            putInt(KEY_ROCKER_Y, value.second)
        }
    }

    fun getCellMockCacheJson(context: Context): String {
        return prefs(context).getString(KEY_CELL_CACHE_JSON, "") ?: ""
    }

    fun setCellMockCacheJson(context: Context, value: String) {
        prefs(context).edit { putString(KEY_CELL_CACHE_JSON, value) }
    }
}
