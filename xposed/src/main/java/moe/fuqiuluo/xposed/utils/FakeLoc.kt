package moe.fuqiuluo.xposed.utils

import android.location.Location
import moe.fuqiuluo.xposed.utils.Logger
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

object FakeLoc {
    /**
     * 是否允许打印日志
     */
    var enableLog = true

    /**
     * 是否允许打印调试日志
     */
    var enableDebugLog = true

    /**
     * 模拟定位服务开关
     */
    @Volatile
    var enable = false

    /**
     * 模拟Gnss卫星数据开关
     */
    @Volatile
    var enableMockGnss = false

    /**
     * 模拟WLAN数据
     */
    @Volatile
    var enableMockWifi = false

    /**
     * 是否禁用GetCurrentLocation方法（在部分系统不禁用可能导致hook失效）
     */
    var disableGetCurrentLocation = true

    /**
     * 是否禁用RegisterLocationListener方法
     */
    var disableRegisterLocationListener = false

    /**
     * 如果TelephonyHook失效，可能需要打开此开关
     */
    var disableFusedLocation = true
    var disableNetworkLocation = true

    var disableRequestGeofence = false
    var disableGetFromLocation = false

    /**
     * 是否允许AGPS模块（当前没什么鸟用）
     */
    var enableAGPS = false

    /**
     * 是否允许NMEA模块
     */
    var enableNMEA = false

    /**
     * 是否隐藏模拟位置
     */
    var hideMock = true

    /**
     * may cause system to crash
     */
    var hookWifi = true

    /**
     * 是否启用传感器/计步模拟
     */
    @Volatile
    var enableSensorMock = false

    /**
     * 将网络定位降级为Cdma
     */
    var needDowngradeToCdma = true
    var cellConfig = CellMockConfig()
    var isSystemServerProcess = false

    /**
     * 模拟最小卫星数量
     */
    var minSatellites = 12

    /**
     * 反定位复原加强（启用后将导致部分应用在关闭Portal后需要重新启动才能重新获取定位）
     */
    var loopBroadcastLocation = false

    /**
     * 上一次的位置
     */
    @Volatile var lastLocation: Location? = null
    @Volatile var latitude = 0.0
    @Volatile var longitude = 0.0
    @Volatile var altitude = 80.0

    @Volatile var speed = 3.05

    var speedAmplitude = 1.0

    @Volatile var simulatedDistanceMeters = 0.0

    @Volatile var simulatedStepCount = 0.0

    @Volatile var lastEstimatedStepLengthMeters = 0.75

    @Volatile var hasBearings = false

    /**
     * 独立于定位朝向的运动状态，供传感器步数模拟使用
     */
    @Volatile var sensorMotionActive = false

    @Volatile var stableStaticLocation = true

    var bearing = 0.0
        get() {
            if (!hasBearings && !stableStaticLocation) {
                if (field >= 360.0) {
                    field -= 360.0
                }
                field += 0.5
                return field
            }
            return ((field % 360.0) + 360.0) % 360.0
        }

    var accuracy = 25.0f
        set(value) {
            field = if (value < 0) {
                -value
            } else {
                value
            }
        }

    fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val radius = 6371000.0
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaPhi = Math.toRadians(lat2 - lat1)
        val deltaLambda = Math.toRadians(lon2 - lon1)
        val a = sin(deltaPhi / 2).pow(2) + cos(phi1) * cos(phi2) * sin(deltaLambda / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return radius * c
    }

    fun jitterLocation(lat: Double = latitude, lon: Double = longitude, n: Double = Random.nextDouble(0.0, accuracy.toDouble()), angle: Double = bearing): Pair<Double, Double> {
        if (accuracy <= 0f) {
            return lat to lon
        }
        if (hasBearings) {
            return lat to lon
        }
        if (stableStaticLocation && !hasBearings) {
            return lat to lon
        }
        val earthRadius = 6371000.0
        val radiusInDegrees = n / 15 / earthRadius * (180 / PI)

        val jitterAngle = if (Random.nextBoolean()) angle + 45 else angle - 45

        val newLat = lat + radiusInDegrees * cos(Math.toRadians(jitterAngle))
        val newLon = lon + radiusInDegrees * sin(Math.toRadians(jitterAngle)) / cos(Math.toRadians(lat))

        return Pair(newLat, newLon)
    }

    fun injectedSpeed(originSpeed: Float): Float {
        if (stableStaticLocation && !hasBearings) {
            if (enableDebugLog) {
                Logger.debug("injectedSpeed static mode -> 0, originSpeed=$originSpeed hasBearings=$hasBearings")
            }
            return 0f
        }
        val baseSpeed = if (hasBearings) speed else originSpeed.toDouble()
        val speedAmp = if (speedAmplitude > 0.0) {
            Random.nextDouble(-speedAmplitude, speedAmplitude)
        } else {
            0.0
        }
        val injected = max(0.0, baseSpeed + speedAmp).toFloat()
        if (enableDebugLog) {
            Logger.debug("injectedSpeed origin=$originSpeed base=$baseSpeed amp=$speedAmp injected=$injected hasBearings=$hasBearings stableStatic=$stableStaticLocation")
        }
        return injected
    }

    fun moveLocation(lat: Double = latitude, lon: Double = longitude, n: Double, angle: Double = bearing): Pair<Double, Double> {
        val earthRadius = 6371000.0
        val distance = n.coerceAtLeast(0.0)
        val radiusInDegrees = distance / earthRadius * (180 / PI)
        val newLat = lat + radiusInDegrees * cos(Math.toRadians(angle))
        val newLon = lon + radiusInDegrees * sin(Math.toRadians(angle)) / cos(Math.toRadians(lat))
        if (enableDebugLog) {
            Logger.debug("moveLocation from=$lat,$lon distance=$distance angle=$angle to=$newLat,$newLon")
        }
        return Pair(newLat, newLon)
    }

    fun estimateStepLengthMeters(currentSpeed: Double = speed): Double {
        return when {
            currentSpeed < 1.5 -> 0.65
            currentSpeed < 2.5 -> 0.78
            else -> 0.90
        }
    }



    fun calculateBearing(latA: Double, lonA: Double, latB: Double, lonB: Double): Double {
        val lat1 = Math.toRadians(latA)
        val lon1 = Math.toRadians(lonA)
        val lat2 = Math.toRadians(latB)
        val lon2 = Math.toRadians(lonB)

        val deltaLon = lon2 - lon1

        val y = sin(deltaLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)

        var bearing = Math.toDegrees(atan2(y, x))
        bearing = (bearing + 360) % 360  // 标准化到0-360度

        return bearing
    }
}
