package moe.fuqiuluo.portal.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import moe.fuqiuluo.portal.Portal
import moe.fuqiuluo.portal.android.root.ShellUtils
import moe.fuqiuluo.portal.cell.OpenCellIdClient
import moe.fuqiuluo.portal.ext.accuracy
import moe.fuqiuluo.portal.ext.altitude
import moe.fuqiuluo.portal.ext.debug
import moe.fuqiuluo.portal.ext.disableFusedProvider
import moe.fuqiuluo.portal.ext.disableGetCurrentLocation
import moe.fuqiuluo.portal.ext.disableRegisterLocationListener
import moe.fuqiuluo.portal.ext.enableCellMock
import moe.fuqiuluo.portal.ext.enableAGPS
import moe.fuqiuluo.portal.ext.enableGetFromLocation
import moe.fuqiuluo.portal.ext.enableNMEA
import moe.fuqiuluo.portal.ext.enableRequestGeofence
import moe.fuqiuluo.portal.ext.hookSensor
import moe.fuqiuluo.portal.ext.minSatelliteCount
import moe.fuqiuluo.portal.ext.needDowngradeToCdma
import moe.fuqiuluo.portal.ext.openCellIdToken
import moe.fuqiuluo.portal.ext.PortalPrefs
import moe.fuqiuluo.portal.ext.preferNrCell
import moe.fuqiuluo.portal.ext.speed
import moe.fuqiuluo.portal.ext.reportDuration
import moe.fuqiuluo.portal.ext.loopBroadcastlocation
import moe.fuqiuluo.portal.ext.stableStaticLocation
import moe.fuqiuluo.xposed.utils.CellMockConfig
import moe.fuqiuluo.xposed.utils.FakeLoc
import java.io.File
import java.util.Locale

object MockServiceHelper {
    const val PROVIDER_NAME = "portal"
    private const val TAG = "MockServiceHelper"
    private const val CELL_CACHE_MAX_SIZE = 128
    private const val CELL_CACHE_DECIMALS = 5
    private lateinit var randomKey: String

    private var loopThread :Thread ?= null
    @Volatile private var isRunning = false

    data class CellRefreshResult(
        val success: Boolean,
        val message: String,
        val fromCache: Boolean = false,
        val forced: Boolean = false,
        val cacheKey: String? = null,
    )

    private data class CachedCellConfigEntry(
        val cacheKey: String,
        val lat: Double,
        val lon: Double,
        val preferNr: Boolean,
        val updatedAtMillis: Long,
        val config: CellMockConfig,
    )

    data class StepDebugInfo(
        val simulatedStepCount: Double,
        val simulatedDistanceMeters: Double,
        val estimatedStepLengthMeters: Double,
        val sensorMotionActive: Boolean,
        val enableSensorMock: Boolean,
        val enableMock: Boolean,
    )

    fun tryInitService(locationManager: LocationManager) {
        val rely = Bundle()
        Log.d("MockServiceHelper", "Try to init service")
        if(locationManager.sendExtraCommand(PROVIDER_NAME, "exchange_key", rely)) {
            rely.getString("key")?.let {
                randomKey = it
                Log.d("MockServiceHelper", "Service init success, key: $randomKey")
            }
        } else {
            Log.e("MockServiceHelper", "Failed to init service")
        }
    }

    fun isMockStart(locationManager: LocationManager): Boolean {
        if (!::randomKey.isInitialized) {
            return false
        }
        val rely = Bundle()
        rely.putString("command_id", "is_start")
        if(locationManager.sendExtraCommand(PROVIDER_NAME, randomKey, rely)) {
            return rely.getBoolean("is_start")
        }
        return false
    }

    fun isGnssMockStart(locationManager: LocationManager): Boolean {
        if (!::randomKey.isInitialized) {
            return false
        }
        val rely = Bundle()
        rely.putString("command_id", "is_gnss_start")
        if(locationManager.sendExtraCommand(PROVIDER_NAME, randomKey, rely)) {
            return rely.getBoolean("is_gnss_start")
        }
        return false
    }

    fun startGnssMock(locationManager: LocationManager): Boolean {
        if (!::randomKey.isInitialized) {
            return false
        }
        val rely = Bundle()
        rely.putString("command_id", "start_gnss_mock")
        return locationManager.sendExtraCommand(PROVIDER_NAME, randomKey, rely)
    }

    fun stopGnssMock(locationManager: LocationManager): Boolean {
        if (!::randomKey.isInitialized) {
            return false
        }
        val rely = Bundle()
        rely.putString("command_id", "stop_gnss_mock")
        return locationManager.sendExtraCommand(PROVIDER_NAME, randomKey, rely)
    }

    fun isWifiMockStart(locationManager: LocationManager): Boolean {
        if (!::randomKey.isInitialized) {
            return false
        }
        val rely = Bundle()
        rely.putString("command_id", "is_wifi_mock_start")
        if(locationManager.sendExtraCommand(PROVIDER_NAME, randomKey, rely)) {
            return rely.getBoolean("is_wifi_mock_start")
        }
        return false
    }

    fun startWifiMock(locationManager: LocationManager): Boolean {
        if (!::randomKey.isInitialized) {
            return false
        }
        val rely = Bundle()
        rely.putString("command_id", "start_wifi_mock")
        return locationManager.sendExtraCommand(PROVIDER_NAME, randomKey, rely)
    }

    fun stopWifiMock(locationManager: LocationManager): Boolean {
        if (!::randomKey.isInitialized) {
            return false
        }
        val rely = Bundle()
        rely.putString("command_id", "stop_wifi_mock")
        return locationManager.sendExtraCommand(PROVIDER_NAME, randomKey, rely)
    }

    fun tryOpenMock(
        locationManager: LocationManager,
        speed: Double,
        altitude: Double,
        accuracy: Float,
    ): Boolean {
        if (!::randomKey.isInitialized) {
            return false
        }
        val rely = Bundle()
        rely.putString("command_id", "start")
        rely.putDouble("speed", speed)
        rely.putDouble("altitude", altitude)
        rely.putFloat("accuracy", accuracy)
        startLoopBroadcastLocation(locationManager)
        return if(locationManager.sendExtraCommand(PROVIDER_NAME, randomKey, rely)) {
            isMockStart(locationManager).also {
                FakeLoc.enable = it
            }
        } else {
            false
        }
    }

    fun tryCloseMock(locationManager: LocationManager): Boolean {
        if (!::randomKey.isInitialized) {
            return false
        }
        val rely = Bundle()
        rely.putString("command_id", "stop")
        stopLoopBroadcastLocation()
        if (locationManager.sendExtraCommand(PROVIDER_NAME, randomKey, rely)) {
            return !isMockStart(locationManager).also {
                FakeLoc.enable = it
            }
        }
        return false
    }

    fun getLocation(locationManager: LocationManager): Pair<Double, Double>? {
        if (!::randomKey.isInitialized) {
            return null
        }
        val rely = Bundle()
        rely.putString("command_id", "get_location")
        if(locationManager.sendExtraCommand(PROVIDER_NAME, randomKey, rely)) {
            return Pair(rely.getDouble("lat"), rely.getDouble("lon"))
        }
        return null
    }

    fun getLocationListenerSize(locationManager: LocationManager): Int? {
        if (!::randomKey.isInitialized) {
            return null
        }
        val rely = Bundle()
        rely.putString("command_id", "get_listener_size")
        if(locationManager.sendExtraCommand(PROVIDER_NAME, randomKey, rely)) {
            return rely.getInt("size")
        }
        return null
    }

    fun broadcastLocation(locationManager: LocationManager): Boolean {
        if (!::randomKey.isInitialized) {
            return false
        }
        val rely = Bundle()
        rely.putString("command_id", "broadcast_location")
        return locationManager.sendExtraCommand(PROVIDER_NAME, randomKey, rely)
    }

    fun setBearing(locationManager: LocationManager, bearing: Double): Boolean {
        if (!::randomKey.isInitialized) {
            return false
        }
        val rely = Bundle()
        rely.putString("command_id", "set_bearing")
        rely.putDouble("bearing", bearing)
        return locationManager.sendExtraCommand(PROVIDER_NAME, randomKey, rely)
    }

    fun clearMotion(locationManager: LocationManager): Boolean {
        if (!::randomKey.isInitialized) {
            return false
        }
        val rely = Bundle()
        rely.putString("command_id", "clear_motion")
        FakeLoc.hasBearings = false
        if (FakeLoc.enableDebugLog) {
            Log.d(TAG, "clearMotion")
        }
        return locationManager.sendExtraCommand(PROVIDER_NAME, randomKey, rely)
    }

    fun setSpeed(locationManager: LocationManager, speed: Float): Boolean {
        if (!::randomKey.isInitialized) {
            return false
        }
        val rely = Bundle()
        rely.putString("command_id", "set_speed")
        rely.putDouble("speed", speed.toDouble())
        if (FakeLoc.enableDebugLog) {
            Log.d(TAG, "setSpeed speed=$speed")
        }
        return locationManager.sendExtraCommand(PROVIDER_NAME, randomKey, rely)
    }

    fun setAltitude(locationManager: LocationManager, altitude: Double): Boolean {
        if (!::randomKey.isInitialized) {
            return false
        }
        val rely = Bundle()
        rely.putString("command_id", "set_altitude")
        rely.putDouble("altitude", altitude)
        return locationManager.sendExtraCommand(PROVIDER_NAME, randomKey, rely)
    }

    fun setSpeedAmplitude(locationManager: LocationManager, speedAmplitude: Double): Boolean {
        if (!::randomKey.isInitialized) {
            return false
        }
        val rely = Bundle()
        rely.putString("command_id", "set_speed_amp")
        rely.putDouble("speed_amplitude", speedAmplitude)
        return locationManager.sendExtraCommand(PROVIDER_NAME, randomKey, rely)
    }

    fun getSpeed(locationManager: LocationManager): Float? {
        if (!::randomKey.isInitialized) {
            return null
        }
        val rely = Bundle()
        rely.putString("command_id", "get_speed")
        if(locationManager.sendExtraCommand(PROVIDER_NAME, randomKey, rely)) {
            return rely.getFloat("speed")
        }
        return null
    }

    fun getBearing(locationManager: LocationManager): Float? {
        if (!::randomKey.isInitialized) {
            return null
        }
        val rely = Bundle()
        rely.putString("command_id", "get_bearing")
        if(locationManager.sendExtraCommand(PROVIDER_NAME, randomKey, rely)) {
            return rely.getFloat("bearing")
        }
        return null
    }

    fun getAltitude(locationManager: LocationManager): Double? {
        if (!::randomKey.isInitialized) {
            return null
        }
        val rely = Bundle()
        rely.putString("command_id", "get_altitude")
        if(locationManager.sendExtraCommand(PROVIDER_NAME, randomKey, rely)) {
            return rely.getDouble("altitude")
        }
        return null
    }

    fun move(locationManager: LocationManager, distance: Double, bearing: Double): Boolean {
        if (!::randomKey.isInitialized) {
            return false
        }
        val rely = Bundle()
        rely.putString("command_id", "move")
        rely.putDouble("n", distance)
        rely.putDouble("bearing", bearing)

        if (FakeLoc.enableDebugLog) {
            Log.d(TAG, "move distance=$distance bearing=$bearing")
        }

        return locationManager.sendExtraCommand(PROVIDER_NAME, randomKey, rely)
    }

    fun setLocation(locationManager: LocationManager, lat: Double, lon: Double): Boolean {
        return updateLocation(locationManager, lat, lon, "=")
    }

    fun updateLocation(locationManager: LocationManager, lat: Double, lon: Double, mode: String): Boolean {
        if (!::randomKey.isInitialized) {
            return false
        }
        val rely = Bundle()
        rely.putString("command_id", "update_location")
        rely.putDouble("lat", lat)
        rely.putDouble("lon", lon)
        rely.putString("mode", mode)
        return locationManager.sendExtraCommand(PROVIDER_NAME, randomKey, rely)
    }

    fun loadLibrary(locationManager: LocationManager, path: String): String? {
        if (!::randomKey.isInitialized) {
            return null
        }
        val rely = Bundle()
        rely.putString("command_id", "load_library")
        rely.putString("path", path)
        if(locationManager.sendExtraCommand(PROVIDER_NAME, randomKey, rely)) {
            return rely.getString("result")
        }
        return null
    }

    fun getStepDebugInfo(locationManager: LocationManager): StepDebugInfo? {
        if (!::randomKey.isInitialized) {
            return null
        }
        val rely = Bundle()
        rely.putString("command_id", "get_step_stats")
        if (locationManager.sendExtraCommand(PROVIDER_NAME, randomKey, rely)) {
            return StepDebugInfo(
                simulatedStepCount = rely.getDouble("simulated_step_count", 0.0),
                simulatedDistanceMeters = rely.getDouble("simulated_distance_meters", 0.0),
                estimatedStepLengthMeters = rely.getDouble("estimated_step_length_meters", 0.0),
                sensorMotionActive = rely.getBoolean("sensor_motion_active", false),
                enableSensorMock = rely.getBoolean("enable_sensor_mock", false),
                enableMock = rely.getBoolean("enable_mock", false),
            )
        }
        return null
    }

    fun setCellConfig(locationManager: LocationManager, config: CellMockConfig): Boolean {
        if (!::randomKey.isInitialized) {
            return false
        }
        val rely = Bundle()
        rely.putString("command_id", "set_cell_config")
        config.writeTo(rely)
        Log.d(TAG, "setCellConfig: enabled=${config.enabled} mcc=${config.mcc} mnc=${config.mnc} lteTac=${config.lteTac} lteEci=${config.lteEci} nrNci=${config.nrNci}")
        return locationManager.sendExtraCommand(PROVIDER_NAME, randomKey, rely)
    }

    fun refreshCellConfigByOpenCellId(
        locationManager: LocationManager,
        context: Context,
        lat: Double,
        lon: Double,
        forceRefresh: Boolean = false,
    ): CellRefreshResult {
        if (!context.enableCellMock) {
            return CellRefreshResult(true, "cell mock disabled")
        }
        val preferNr = context.preferNrCell
        val cacheKey = buildCellCacheKey(lat, lon, preferNr)
        Log.d(
            TAG,
            "refreshCellConfigByOpenCellId: lat=$lat lon=$lon preferNr=$preferNr forceRefresh=$forceRefresh cacheKey=$cacheKey",
        )
        val token = context.openCellIdToken
        if (token.isBlank()) {
            Log.e(TAG, "refreshCellConfigByOpenCellId: token is empty")
            return CellRefreshResult(false, "OpenCellID token is empty", cacheKey = cacheKey)
        }
        if (!forceRefresh) {
            val cachedEntry = findCachedCellConfig(context, cacheKey)
            if (cachedEntry != null) {
                Log.d(
                    TAG,
                    "refreshCellConfigByOpenCellId cache hit: key=$cacheKey updatedAt=${cachedEntry.updatedAtMillis}",
                )
                val pushed = setCellConfig(
                    locationManager,
                    cachedEntry.config.copy(enabled = true, preferNr = preferNr),
                )
                return if (pushed) {
                    CellRefreshResult(
                        success = true,
                        message = "cache hit",
                        fromCache = true,
                        forced = false,
                        cacheKey = cacheKey,
                    )
                } else {
                    Log.e(TAG, "refreshCellConfigByOpenCellId cache apply failed: sendExtraCommand(set_cell_config) returned false")
                    CellRefreshResult(
                        success = false,
                        message = "sendExtraCommand(set_cell_config) failed",
                        fromCache = true,
                        forced = false,
                        cacheKey = cacheKey,
                    )
                }
            }
            Log.d(TAG, "refreshCellConfigByOpenCellId cache miss: key=$cacheKey")
        } else {
            Log.d(TAG, "refreshCellConfigByOpenCellId force refresh: bypass cache key=$cacheKey")
        }
        val queryResult = OpenCellIdClient.queryBestConfig(lat, lon, token, preferNr)
        val config = queryResult.config ?: run {
            Log.e(TAG, "refreshCellConfigByOpenCellId failed: ${queryResult.message}")
            return CellRefreshResult(false, queryResult.message, forced = forceRefresh, cacheKey = cacheKey)
        }
        val pushed = setCellConfig(locationManager, config)
        return if (pushed) {
            cacheCellConfig(
                context,
                CachedCellConfigEntry(
                    cacheKey = cacheKey,
                    lat = lat,
                    lon = lon,
                    preferNr = preferNr,
                    updatedAtMillis = System.currentTimeMillis(),
                    config = config.copy(enabled = true, preferNr = preferNr),
                ),
            )
            Log.d(TAG, "refreshCellConfigByOpenCellId cache store: key=$cacheKey")
            CellRefreshResult(
                success = true,
                message = if (forceRefresh) "refreshed" else "fetched",
                fromCache = false,
                forced = forceRefresh,
                cacheKey = cacheKey,
            )
        } else {
            Log.e(TAG, "refreshCellConfigByOpenCellId failed: sendExtraCommand(set_cell_config) returned false")
            CellRefreshResult(
                success = false,
                message = "sendExtraCommand(set_cell_config) failed",
                fromCache = false,
                forced = forceRefresh,
                cacheKey = cacheKey,
            )
        }
    }

    fun putConfig(
        locationManager: LocationManager,
        context: Context,
        hookSensorOverride: Boolean? = null,
    ): Boolean {
        if (!::randomKey.isInitialized) {
            return false
        }

        val isMockEnabled = isMockStart(locationManager)
        val accuracy = context.accuracy
        val hookSensorEnabled = hookSensorOverride ?: context.hookSensor

        FakeLoc.enable = isMockEnabled
        FakeLoc.altitude = context.altitude
        FakeLoc.speed = context.speed
        FakeLoc.accuracy = accuracy
        FakeLoc.enableDebugLog = context.debug
        FakeLoc.disableGetCurrentLocation = context.disableGetCurrentLocation
        FakeLoc.disableRegisterLocationListener = context.disableRegisterLocationListener
        FakeLoc.disableFusedLocation = context.disableFusedProvider
        FakeLoc.needDowngradeToCdma = context.needDowngradeToCdma
        FakeLoc.minSatellites = context.minSatelliteCount
        FakeLoc.enableAGPS = context.enableAGPS
        FakeLoc.enableNMEA = context.enableNMEA
        FakeLoc.enableSensorMock = hookSensorEnabled
        FakeLoc.stableStaticLocation = context.stableStaticLocation
        FakeLoc.disableRequestGeofence = !context.enableRequestGeofence
        FakeLoc.disableGetFromLocation = !context.enableGetFromLocation
        FakeLoc.cellConfig.enabled = context.enableCellMock
        FakeLoc.cellConfig.preferNr = context.preferNrCell

        val rely = Bundle()
        rely.putString("command_id", "put_config")
        rely.putBoolean("enable", isMockEnabled)
        rely.putDouble("altitude", FakeLoc.altitude)
        rely.putDouble("speed", FakeLoc.speed)
        rely.putFloat("accuracy", FakeLoc.accuracy)
        rely.putBoolean("enable_debug_log", FakeLoc.enableDebugLog)
        rely.putBoolean("disable_get_current_location", FakeLoc.disableGetCurrentLocation)
        rely.putBoolean("disable_register_location_listener", FakeLoc.disableRegisterLocationListener)
        rely.putBoolean("disable_fused_location", FakeLoc.disableFusedLocation)
        rely.putBoolean("need_downgrade_to_2g", FakeLoc.needDowngradeToCdma)
        rely.putInt("min_satellites", FakeLoc.minSatellites)
        rely.putBoolean("enable_agps", FakeLoc.enableAGPS)
        rely.putBoolean("enable_nmea", FakeLoc.enableNMEA)
        rely.putBoolean("hook_sensor", hookSensorEnabled)
        rely.putBoolean("stable_static_location", FakeLoc.stableStaticLocation)
        rely.putBoolean("disable_request_geofence", FakeLoc.disableRequestGeofence)
        rely.putBoolean("disable_get_from_location", FakeLoc.disableGetFromLocation)
        FakeLoc.cellConfig.writeTo(rely)

        return locationManager.sendExtraCommand(PROVIDER_NAME, randomKey, rely)
    }

    fun isServiceInit(): Boolean {
        return ::randomKey.isInitialized
    }

    private fun buildCellCacheKey(lat: Double, lon: Double, preferNr: Boolean): String {
        return String.format(
            Locale.US,
            "%.${CELL_CACHE_DECIMALS}f,%.${CELL_CACHE_DECIMALS}f,%s",
            lat,
            lon,
            if (preferNr) "nr" else "lte",
        )
    }

    private fun findCachedCellConfig(context: Context, cacheKey: String): CachedCellConfigEntry? {
        return readCellCache(context).firstOrNull { it.cacheKey == cacheKey }
    }

    private fun readCellCache(context: Context): MutableList<CachedCellConfigEntry> {
        val json = PortalPrefs.getCellMockCacheJson(context)
        if (json.isBlank()) {
            return mutableListOf()
        }
        return runCatching {
            val array = JSON.parseArray(json)
            array.mapNotNull { item ->
                val obj = item as? JSONObject ?: return@mapNotNull null
                val cacheKey = obj.getString("cacheKey") ?: return@mapNotNull null
                CachedCellConfigEntry(
                    cacheKey = cacheKey,
                    lat = obj.getDoubleValue("lat"),
                    lon = obj.getDoubleValue("lon"),
                    preferNr = obj.getBooleanValue("preferNr"),
                    updatedAtMillis = obj.getLongValue("updatedAtMillis"),
                    config = CellMockConfig(
                        enabled = if (obj.containsKey("enabled")) obj.getBooleanValue("enabled") else true,
                        mcc = obj.getIntValue("mcc"),
                        mnc = obj.getIntValue("mnc"),
                        lteTac = obj.getIntValue("lteTac"),
                        lteEci = obj.getIntValue("lteEci"),
                        ltePci = obj.getIntValue("ltePci"),
                        lteEarfcn = obj.getIntValue("lteEarfcn"),
                        nrNci = obj.getLongValue("nrNci"),
                        nrPci = obj.getIntValue("nrPci"),
                        nrArfcn = obj.getIntValue("nrArfcn"),
                        preferNr = if (obj.containsKey("configPreferNr")) {
                            obj.getBooleanValue("configPreferNr")
                        } else {
                            obj.getBooleanValue("preferNr")
                        },
                    ),
                )
            }.toMutableList()
        }.getOrElse {
            Log.e(TAG, "readCellCache failed, clear broken cache", it)
            PortalPrefs.setCellMockCacheJson(context, "")
            mutableListOf()
        }
    }

    private fun cacheCellConfig(context: Context, entry: CachedCellConfigEntry) {
        val cache = readCellCache(context)
            .filterNot { it.cacheKey == entry.cacheKey }
            .toMutableList()
        cache.add(0, entry)
        val normalized = cache
            .sortedByDescending { it.updatedAtMillis }
            .take(CELL_CACHE_MAX_SIZE)
        val jsonArray = JSONArray()
        normalized.forEach { cached ->
            jsonArray.add(
                JSONObject().apply {
                    put("cacheKey", cached.cacheKey)
                    put("lat", cached.lat)
                    put("lon", cached.lon)
                    put("preferNr", cached.preferNr)
                    put("updatedAtMillis", cached.updatedAtMillis)
                    put("enabled", cached.config.enabled)
                    put("mcc", cached.config.mcc)
                    put("mnc", cached.config.mnc)
                    put("lteTac", cached.config.lteTac)
                    put("lteEci", cached.config.lteEci)
                    put("ltePci", cached.config.ltePci)
                    put("lteEarfcn", cached.config.lteEarfcn)
                    put("nrNci", cached.config.nrNci)
                    put("nrPci", cached.config.nrPci)
                    put("nrArfcn", cached.config.nrArfcn)
                    put("configPreferNr", cached.config.preferNr)
                },
            )
        }
        PortalPrefs.setCellMockCacheJson(context, jsonArray.toJSONString())
    }


    private fun startLoopBroadcastLocation(locationManager: LocationManager){
        val appContext = Portal.appContext
        val delayTime=appContext.reportDuration.toLong()

        if(isRunning) return
        if(!appContext.loopBroadcastlocation) return

        isRunning=true
        loopThread=Thread{
            Log.d("MockServiceHelper","loopBoardcast: Start")
            while(isRunning){
                try {
                    broadcastLocation(locationManager)
                    Thread.sleep(delayTime)
                }catch (e:InterruptedException){
                    if (FakeLoc.enableDebugLog) {
                        Log.d("MockServiceHelper","loopBoardcast: Stop")
                    }
                    break
                }
            }
        }
        loopThread!!.start()
    }

    private fun stopLoopBroadcastLocation(){
        isRunning =false
        loopThread?.interrupt()
        loopThread = null
    }


    @SuppressLint("DiscouragedPrivateApi")
    fun loadPortalLibrary(context: Context): Boolean {
        if (!ShellUtils.hasRoot()) return false

        val isX86: Boolean = runCatching {
            if (Build.SUPPORTED_ABIS.any { it.contains("x86") }) {
                return@runCatching true
            }
            val clazz = Class.forName("dalvik.system.VMRuntime")
            val method = clazz.getDeclaredMethod("getRuntime")
            val runtime = method.invoke(null)
            val field = clazz.getDeclaredField("vmInstructionSet")
            field.isAccessible = true
            val instructionSet = field.get(runtime) as String
            if (instructionSet.contains("x86") ) {
                true
            } else false
        }.getOrElse { false }
        // todo: support x86

        val soDir = File("/data/local/portal-lib")
        if (!soDir.exists()) {
            ShellUtils.executeCommand("mkdir ${soDir.absolutePath}")
        }
        val soFile = File(soDir, "libportal.so")
        runCatching {
            val tmpSoFile = File(soDir, "libportal.so.tmp").also { file ->
                var nativeDir = context.applicationInfo.nativeLibraryDir
                val apkSoFile = File(nativeDir, "libportal.so")
                if (apkSoFile.exists()) {
                    ShellUtils.executeCommand("cp ${apkSoFile.absolutePath} ${file.absolutePath}")
                } else {
                    Log.e("MockServiceHelper", "Failed to copy portal library: ${apkSoFile.absolutePath}")
                    return@runCatching
                }
            }
            if (soFile.exists()) {
                val originalHash = ShellUtils.executeCommandToBytes("head -c 4096 ${soFile.absolutePath}")
                val newHash = ShellUtils.executeCommandToBytes("head -c 4096 ${tmpSoFile.absolutePath}")
                if (!originalHash.contentEquals(newHash)) {
                    ShellUtils.executeCommand("rm ${soFile.absolutePath}")
                    ShellUtils.executeCommand("mv ${tmpSoFile.absolutePath} ${soFile.absolutePath}")
                } else {
                    ShellUtils.executeCommand("rm ${tmpSoFile.absolutePath}")
                }
            } else if (tmpSoFile.exists()) {
                ShellUtils.executeCommand("mv ${tmpSoFile.absolutePath} ${soFile.absolutePath}")
            }
        }.onFailure {
            Log.w("MockServiceHelper", "Failed to copy portal library", it)
        }

        ShellUtils.executeCommand("chmod 777 ${soFile.absolutePath}")

        val result = loadLibrary(context.getSystemService(Context.LOCATION_SERVICE) as LocationManager, soFile.absolutePath)

        Log.d("MockServiceHelper", "load portal library result: $result")

        return result == "success"
    }
}
