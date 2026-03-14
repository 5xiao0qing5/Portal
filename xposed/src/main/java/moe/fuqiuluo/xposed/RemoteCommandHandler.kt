package moe.fuqiuluo.xposed

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import moe.fuqiuluo.dobby.Dobby
import moe.fuqiuluo.xposed.hooks.LocationServiceHook
import moe.fuqiuluo.xposed.utils.CellMockConfig
import moe.fuqiuluo.xposed.utils.FakeLoc
import moe.fuqiuluo.xposed.utils.BinderUtils
import moe.fuqiuluo.xposed.utils.Logger
import java.util.Collections
import kotlin.random.Random

object RemoteCommandHandler {
    private val proxyBinders by lazy { Collections.synchronizedList(arrayListOf<IBinder>()) }
    private val needProxyCmd = arrayOf(
        "start",
        "stop",
        "clear_motion",
        "set_speed_amp",
        "set_altitude",
        "set_speed",
        "update_location",
        "set_bearing",
        "move",
        "put_config",
        "set_cell_config"
    )
    internal val randomKey by lazy { "portal_" + Random.nextDouble() }
    private var isLoadedLibrary = false

    private fun updateNativeSensorHookState() {
        if (isLoadedLibrary) {
            if (FakeLoc.enableDebugLog) {
                Logger.debug(
                    "stepNativeHook enabled=${FakeLoc.enable && FakeLoc.enableSensorMock} mock=${FakeLoc.enable} sensorMock=${FakeLoc.enableSensorMock} moving=${FakeLoc.sensorMotionActive}"
                )
            }
            Dobby.setStatus(FakeLoc.enable && FakeLoc.enableSensorMock)
        }
    }

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    fun handleInstruction(command: String, rely: Bundle): Boolean {
        // Exchange key -> returns a random key -> is used to verify that it is the PortalManager
        if (command == "exchange_key") {
            val userId = BinderUtils.getCallerUid()
            if (BinderUtils.isLocationProviderEnabled(userId)) {
                rely.putString("key", randomKey)
                return true
            }
            // Go back and see if the instruction has been processed to prevent it from being detected by others
        } else if (command != randomKey) {
            return false
        }
        val commandId = rely.getString("command_id") ?: return false

        kotlin.runCatching {
            if (proxyBinders.isNotEmpty() && needProxyCmd.any { it == commandId }) {
                proxyBinders.removeIf {
                    if (it.isBinderAlive && it.pingBinder()) {
                        val data = Parcel.obtain()
                        data.writeBundle(rely)
                        it.transact(1, data, null, 0)
                        data.recycle()
                        false
                    } else true
                }
            }
        }.onFailure {
            Logger.error("Failed to transact with proxyBinder", it)
        }

        if (FakeLoc.enableDebugLog) {
            Logger.debug("commandId=$commandId, rely=$rely")
        }

        when (commandId) {
            "set_proxy" -> {
                Logger.info("SubProxyBinder: ${rely.getBinder("proxy")} from ${BinderUtils.getUidPackageNames()}!")
                rely.getBinder("proxy")?.let {
                    proxyBinders.add(it)
                }
                return true
            }
            "start" -> {
                val speed = rely.getDouble("speed", FakeLoc.speed)
                val altitude = rely.getDouble("altitude", FakeLoc.altitude)
                val accuracy = rely.getFloat("accuracy", FakeLoc.accuracy)

                FakeLoc.enable = true
                FakeLoc.sensorMotionActive = false
                FakeLoc.simulatedDistanceMeters = 0.0
                FakeLoc.simulatedStepCount = 0.0
                FakeLoc.lastEstimatedStepLengthMeters = FakeLoc.estimateStepLengthMeters(speed)
                updateNativeSensorHookState()

                FakeLoc.speed = speed
                FakeLoc.altitude = altitude
                FakeLoc.accuracy = accuracy

                return true
            }
            "stop" -> {
                FakeLoc.enable = false
                FakeLoc.hasBearings = false
                FakeLoc.sensorMotionActive = false
                updateNativeSensorHookState()
                return true
            }
            "clear_motion" -> {
                FakeLoc.hasBearings = false
                FakeLoc.sensorMotionActive = false
                if (FakeLoc.enableDebugLog) {
                    Logger.debug("clear_motion -> hasBearings=false sensorMotionActive=false")
                }
                return true
            }
            "is_start" -> {
                rely.putBoolean("is_start", FakeLoc.enable)
                return true
            }
            "start_gnss_mock" -> {
                FakeLoc.enableMockGnss = true
                return true
            }
            "stop_gnss_mock" -> {
                FakeLoc.enableMockGnss = false
                return true
            }
            "is_gnss_start" -> {
                rely.putBoolean("is_gnss_start", FakeLoc.enableMockGnss)
                return true
            }
            "is_wifi_mock_start" -> {
                rely.putBoolean("is_wifi_mock_start", FakeLoc.enableMockWifi)
                return true
            }
            "start_wifi_mock" -> {
                FakeLoc.enableMockWifi = true
                return true
            }
            "stop_wifi_mock" -> {
                FakeLoc.enableMockWifi = false
                return true
            }
            "get_location" -> {
                rely.putDouble("lat", FakeLoc.latitude)
                rely.putDouble("lon", FakeLoc.longitude)
                return true
            }
            "get_listener_size" -> {
                rely.putInt("size", LocationServiceHook.locationListeners.size)
                return true
            }
            "get_speed" -> {
                rely.putDouble("speed", FakeLoc.speed)
                return true
            }
            "get_bearing" -> {
                rely.putDouble("bearing", FakeLoc.bearing)
                return true
            }
            "get_altitude" -> {
                rely.putDouble("altitude", FakeLoc.altitude)
                return true
            }
            "set_speed_amp" -> {
                val speedAmplitude = rely.getDouble("speed_amplitude", 1.0)
                FakeLoc.speedAmplitude = speedAmplitude
                return true
            }
            "set_altitude" -> {
                val altitude = rely.getDouble("altitude", 0.0)
                FakeLoc.altitude = altitude
                return true
            }
            "set_speed" -> {
                val speed = rely.getDouble("speed", 0.0)
                FakeLoc.speed = speed
                if (FakeLoc.enableDebugLog) {
                    Logger.debug("set_speed -> speed=$speed")
                }
                return true
            }
            "set_bearing" -> {
                val bearing = rely.getDouble("bearing", 0.0)
                FakeLoc.bearing = bearing
                FakeLoc.hasBearings = true
                FakeLoc.sensorMotionActive = true
                if (FakeLoc.enableDebugLog) {
                    Logger.debug("stepMotion set_bearing bearing=$bearing moving=true")
                }
                return true
            }
            "move" -> {
                val distance = rely.getDouble("n", 0.0)
                if (distance == 0.0) return true
                val bearing = rely.getDouble("bearing", 0.0)
                FakeLoc.sensorMotionActive = true
                val stepLengthMeters = FakeLoc.estimateStepLengthMeters(FakeLoc.speed)
                FakeLoc.lastEstimatedStepLengthMeters = stepLengthMeters
                FakeLoc.simulatedDistanceMeters += distance
                FakeLoc.simulatedStepCount += distance / stepLengthMeters
                if (FakeLoc.enableDebugLog) {
                    Logger.debug("move command distance=$distance bearing=$bearing current=${FakeLoc.latitude},${FakeLoc.longitude}")
                    Logger.debug("stepMotion move distance=$distance bearing=$bearing moving=true")
                    Logger.debug("stepStats distance=${FakeLoc.simulatedDistanceMeters} steps=${FakeLoc.simulatedStepCount} stepLength=$stepLengthMeters")
                }
                val newLoc = FakeLoc.moveLocation(
                    n = distance,
                    angle = bearing
                )
                if (FakeLoc.enableDebugLog) {
                    Logger.debug("move: distance=$distance, bearing=$bearing, newLoc=$newLoc")
                }
                FakeLoc.bearing = bearing
                FakeLoc.hasBearings = true
                return updateCoordinate(newLoc.first, newLoc.second, resetMotion = false).also {
                    if (FakeLoc.isSystemServerProcess) LocationServiceHook.callOnLocationChanged()
                }
            }
            "update_location" -> {
                val mode = rely.getString("mode")
                var newLat = rely.getDouble("lat", 0.0)
                var newLon = rely.getDouble("lon", 0.0)
                when(mode) {
                    "+" -> {
                        newLat += FakeLoc.latitude
                        newLon += FakeLoc.longitude
                        return updateCoordinate(newLat, newLon)
                    }
                    "-" -> {
                        newLat = FakeLoc.latitude - newLat
                        newLon = FakeLoc.longitude - newLon
                        return updateCoordinate(newLat, newLon)
                    }
                    "*" -> {
                        newLat *= FakeLoc.latitude
                        newLon *= FakeLoc.longitude
                        return updateCoordinate(newLat, newLon)
                    }
                    "/" -> {
                        if (newLat == 0.0 || newLon == 0.0) {
                            return false
                        }
                        newLat /= FakeLoc.latitude
                        newLon /= FakeLoc.longitude
                        return updateCoordinate(newLat, newLon)
                    }
                    "=" -> {
                        return updateCoordinate(newLat, newLon)
                    }
                    "random" -> {
                        return updateCoordinate(Random.nextDouble(-90.0, 90.0), Random.nextDouble(-180.0, 180.0))
                    }
                }
                return true
            }
            "put_config" -> {
                val enable = rely.getBoolean("enable", FakeLoc.enable)
                val speed = rely.getDouble("speed", FakeLoc.speed)
                val altitude = rely.getDouble("altitude", FakeLoc.altitude)
                val accuracy = rely.getFloat("accuracy", FakeLoc.accuracy)
                val enableDebugLog = rely.getBoolean("enable_debug_log", FakeLoc.enableDebugLog)
                val disableGetCurrentLocation = rely.getBoolean("disable_get_current_location", FakeLoc.disableGetCurrentLocation)
                val disableRegisterLocationListener = rely.getBoolean("disable_register_location_listener", FakeLoc.disableRegisterLocationListener)
                val disableFusedLocation = rely.getBoolean("disable_fused_location", FakeLoc.disableFusedLocation)
                val needDowngradeToCdma = rely.getBoolean("need_downgrade_to_2g", FakeLoc.needDowngradeToCdma)
                var minSatellites = rely.getInt("min_satellites", 12)
                if (minSatellites < 0) {
                    minSatellites = 12
                }

                val enableAGPS = rely.getBoolean("enable_agps", FakeLoc.enableAGPS)
                val enableNMEA = rely.getBoolean("enable_nmea", FakeLoc.enableNMEA)
                val enableSensorMock = rely.getBoolean("hook_sensor", FakeLoc.enableSensorMock)
                val stableStaticLocation = rely.getBoolean("stable_static_location", FakeLoc.stableStaticLocation)
                val disableRequestGeofence = rely.getBoolean("disable_request_geofence", FakeLoc.disableRequestGeofence)
                val disableGetFromLocation = rely.getBoolean("disable_get_from_location", FakeLoc.disableGetFromLocation)

                FakeLoc.enable = enable
                FakeLoc.speed = speed
                FakeLoc.altitude = altitude
                FakeLoc.accuracy = accuracy
                FakeLoc.enableDebugLog = enableDebugLog
                FakeLoc.disableGetCurrentLocation = disableGetCurrentLocation
                FakeLoc.disableRegisterLocationListener = disableRegisterLocationListener
                FakeLoc.disableFusedLocation = disableFusedLocation
                FakeLoc.needDowngradeToCdma = needDowngradeToCdma
                FakeLoc.minSatellites = minSatellites
                FakeLoc.enableAGPS = enableAGPS
                FakeLoc.enableNMEA = enableNMEA
                FakeLoc.enableSensorMock = enableSensorMock
                FakeLoc.stableStaticLocation = stableStaticLocation
                FakeLoc.disableRequestGeofence = disableRequestGeofence
                FakeLoc.disableGetFromLocation = disableGetFromLocation
                FakeLoc.cellConfig = CellMockConfig.from(rely, FakeLoc.cellConfig)
                updateNativeSensorHookState()
                if (FakeLoc.enableDebugLog) {
                    Logger.debug("put_config enable=$enable speed=$speed altitude=$altitude accuracy=$accuracy stableStatic=$stableStaticLocation hookSensor=$enableSensorMock speedAmp=${FakeLoc.speedAmplitude}")
                }
                if (FakeLoc.enableDebugLog) {
                    Logger.debug(
                        "put_config cell: enabled=${FakeLoc.cellConfig.enabled}, mcc=${FakeLoc.cellConfig.mcc}, mnc=${FakeLoc.cellConfig.mnc}, " +
                            "lteTac=${FakeLoc.cellConfig.lteTac}, lteEci=${FakeLoc.cellConfig.lteEci}, nrNci=${FakeLoc.cellConfig.nrNci}"
                    )
                }
                return true
            }
            "set_cell_config" -> {
                FakeLoc.cellConfig = CellMockConfig.from(rely, FakeLoc.cellConfig).apply {
                    enabled = true
                }
                Logger.info(
                    "set_cell_config: enabled=${FakeLoc.cellConfig.enabled}, mcc=${FakeLoc.cellConfig.mcc}, mnc=${FakeLoc.cellConfig.mnc}, " +
                        "lteTac=${FakeLoc.cellConfig.lteTac}, lteEci=${FakeLoc.cellConfig.lteEci}, nrNci=${FakeLoc.cellConfig.nrNci}"
                )
                return true
            }
            "sync_config" -> {
                rely.putBoolean("enable", FakeLoc.enable)
                rely.putDouble("latitude", FakeLoc.latitude)
                rely.putDouble("longitude", FakeLoc.longitude)
                rely.putDouble("altitude", FakeLoc.altitude)
                rely.putDouble("speed", FakeLoc.speed)
                rely.putDouble("speed_amplitude", FakeLoc.speedAmplitude)
                rely.putBoolean("has_bearings", FakeLoc.hasBearings)
                rely.putDouble("bearing", FakeLoc.bearing)
                rely.putParcelable("last_location", FakeLoc.lastLocation)
                rely.putBoolean("enable_log", FakeLoc.enableLog)
                rely.putBoolean("enable_debug_log", FakeLoc.enableDebugLog)
                rely.putBoolean("disable_get_current_location", FakeLoc.disableGetCurrentLocation)
                rely.putBoolean("disable_register_location_listener", FakeLoc.disableRegisterLocationListener)
                rely.putBoolean("disable_fused_location", FakeLoc.disableFusedLocation)
                rely.putBoolean("enable_agps", FakeLoc.enableAGPS)
                rely.putBoolean("enable_nmea", FakeLoc.enableNMEA)
                rely.putBoolean("hook_sensor", FakeLoc.enableSensorMock)
                rely.putBoolean("sensor_motion_active", FakeLoc.sensorMotionActive)
                rely.putDouble("simulated_distance_meters", FakeLoc.simulatedDistanceMeters)
                rely.putDouble("simulated_step_count", FakeLoc.simulatedStepCount)
                rely.putDouble("estimated_step_length_meters", FakeLoc.lastEstimatedStepLengthMeters)
                rely.putBoolean("stable_static_location", FakeLoc.stableStaticLocation)
                rely.putBoolean("hide_mock", FakeLoc.hideMock)
                rely.putBoolean("hook_wifi", FakeLoc.hookWifi)
                rely.putBoolean("need_downgrade_to_2g", FakeLoc.needDowngradeToCdma)
                FakeLoc.cellConfig.writeTo(rely)
                return true
            }
            "get_step_stats" -> {
                rely.putDouble("simulated_distance_meters", FakeLoc.simulatedDistanceMeters)
                rely.putDouble("simulated_step_count", FakeLoc.simulatedStepCount)
                rely.putDouble("estimated_step_length_meters", FakeLoc.lastEstimatedStepLengthMeters)
                rely.putBoolean("sensor_motion_active", FakeLoc.sensorMotionActive)
                rely.putBoolean("enable_sensor_mock", FakeLoc.enableSensorMock)
                rely.putBoolean("enable_mock", FakeLoc.enable)
                return true
            }
            "broadcast_location" -> {
                LocationServiceHook.callOnLocationChanged()
                return true
            }
            "load_library" -> {
                val path = rely.getString("path") ?: return false

                if (isLoadedLibrary && path.endsWith("libportal.so")) {
                    rely.putString("result", "success")
                    return true
                }
                runCatching {
                    System.load(path)
                }.onSuccess {
                    rely.putString("result", "success")
                    isLoadedLibrary = true
                }.onFailure {
                    rely.putString("result", it.stackTraceToString())
                }

                updateNativeSensorHookState()

                return true
            }
            else -> return false
        }
    }

//    private var hasHookSensor = false
//
//    private fun tryHookSensor(classLoader: ClassLoader = FakeLoc::class.java.classLoader!!) {
//        if (hasHookSensor || proxyBinders.isNullOrEmpty()) return
//
//
//
//        hasHookSensor = true
//    }

//    private fun generateLocation(): Location {
//        val (location, realLocation) = if (FakeLocationConfig.lastLocation != null) {
//            (FakeLocationConfig.lastLocation!! to true)
//        } else {
//            (Location(LocationManager.GPS_PROVIDER) to false)
//        }
//
//        return LocationServiceProxyHook.injectLocation(location, realLocation)
//    }

    private fun updateCoordinate(newLat: Double, newLon: Double, resetMotion: Boolean = true): Boolean {
        if (newLat in -90.0..90.0 && newLon in -180.0..180.0) {
            FakeLoc.latitude = newLat
            FakeLoc.longitude = newLon
            if (resetMotion) {
                FakeLoc.hasBearings = false
                FakeLoc.sensorMotionActive = false
            }
            return true
        } else {
            Logger.error("Invalid latitude or longitude: $newLat, $newLon")
            return false
        }
    }
}
