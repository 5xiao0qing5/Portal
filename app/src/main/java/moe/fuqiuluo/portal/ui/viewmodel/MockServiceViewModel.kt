package moe.fuqiuluo.portal.ui.viewmodel

import android.app.Activity
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tencent.bugly.crashreport.CrashReport
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.fuqiuluo.portal.android.coro.CoroutineController
import moe.fuqiuluo.portal.android.coro.CoroutineRouteMock
import moe.fuqiuluo.portal.ext.accuracy
import moe.fuqiuluo.portal.ext.altitude
import moe.fuqiuluo.portal.ext.reportDuration
import moe.fuqiuluo.portal.ext.speed
import moe.fuqiuluo.portal.service.MockServiceHelper
import moe.fuqiuluo.portal.ui.mock.HistoricalLocationEditDraft
import moe.fuqiuluo.portal.ui.mock.HistoricalLocation
import moe.fuqiuluo.portal.ui.mock.HistoricalRoute
import moe.fuqiuluo.portal.ui.mock.Rocker
import moe.fuqiuluo.xposed.utils.FakeLoc
import net.sf.geographiclib.Geodesic

class MockServiceViewModel : ViewModel() {
    companion object {
        private const val TAG = "MockServiceViewModel"
        private const val MOVE_COMPENSATION = 1.0
    }

    lateinit var rocker: Rocker
    private lateinit var rockerJob: Job
    private lateinit var routeMockJob: Job
    var isRockerLocked = false
    var routeStage = 0
    var routeLoopCompletedCount = 0
    var routeMockSpeed = 3.0
    var routeMockLoopEnabled = false
    var routeMockLoopCount = 1
    var routeMockLoopIntervalSeconds = 0
    val rockerCoroutineController = CoroutineController()
    val routeMockCoroutine = CoroutineRouteMock()

    var isRouteStart = false

    var locationManager: LocationManager? = null
        set(value) {
            field = value
            if (value != null)
                MockServiceHelper.tryInitService(value)
        }

    var selectedLocation: HistoricalLocation? = null
    var selectedRoute: HistoricalRoute? = null
    var pendingHistoricalLocationEditDraft: HistoricalLocationEditDraft? = null
    var pendingHistoricalLocationMapPick = false
    var reopenHistoricalLocationEditDialog = false


    fun initRocker(activity: Activity): Rocker {
        if (!::rocker.isInitialized) {
            rocker = Rocker(activity)
        }

        if (!::rockerJob.isInitialized || rockerJob.isCancelled) {
            rockerCoroutineController.pause()
            val delayTime = activity.reportDuration.coerceAtLeast(1).toLong()
            val applicationContext = activity.applicationContext
            rockerJob = viewModelScope.launch {
                do {
                    rockerCoroutineController.controlledCoroutine()
                    delay(delayTime)
                    val stepDistance = calculateStepDistance(delayTime)
                    val manager = locationManager ?: continue
                    if (FakeLoc.enableDebugLog) {
                        Log.d(
                            TAG,
                            "rockerTick speed=${FakeLoc.speed} delayMs=$delayTime stepDistance=$stepDistance bearing=${FakeLoc.bearing} hasBearings=${FakeLoc.hasBearings}"
                        )
                    }
                    if (stepDistance <= 0.0) {
                        Log.w(TAG, "Skip rocker move because step distance is invalid: $stepDistance")
                        continue
                    }

                    CrashReport.setUserSceneTag(applicationContext, 261773)
                    if (!MockServiceHelper.move(manager, stepDistance, FakeLoc.bearing)) {
                        Log.e(TAG, "Failed to move")
                    }

//                    if (MockServiceHelper.broadcastLocation(locationManager!!)) {
//                        Log.d("MockServiceViewModel", "Broadcast location")
//                    } else {
//                        Log.e("MockServiceViewModel", "Failed to broadcast location")
//                    }
                } while (isActive)
            }
        }

        FakeLoc.speed = activity.speed
        FakeLoc.altitude = activity.altitude
        FakeLoc.accuracy = activity.accuracy

        if (!::routeMockJob.isInitialized || routeMockJob.isCancelled) {
            routeMockCoroutine.pause()
            val delayTime = activity.reportDuration.coerceAtLeast(1).toLong()
            routeMockJob = viewModelScope.launch {
                routeLoop@ do {
                    routeMockCoroutine.routeMockCoroutine()
                    delay(delayTime)
                    val manager = locationManager
                    if (manager == null) {
                        resetRouteMockState(disableAutoPlay = true)
                        continue@routeLoop
                    }
                    val route = selectedRoute?.route.orEmpty()
                    if (route.isEmpty()) {
                        Log.w(TAG, "Skip route mock because no route is selected")
                        resetRouteMockState(disableAutoPlay = true)
                        continue@routeLoop
                    }
                    val stepDistance = calculateStepDistance(delayTime, routeMockSpeed)
                    if (FakeLoc.enableDebugLog) {
                        Log.d(
                            TAG,
                            "routeTick routeSpeed=$routeMockSpeed delayMs=$delayTime stepDistance=$stepDistance routeStage=$routeStage hasBearings=${FakeLoc.hasBearings}"
                        )
                    }
                    if (stepDistance <= 0.0) {
                        Log.w(TAG, "Skip route mock because step distance is invalid: $stepDistance")
                        resetRouteMockState(disableAutoPlay = true)
                        continue@routeLoop
                    }

                    // 如果是第0阶段，定位到第一个点
                    if (routeStage == 0) {
                        MockServiceHelper.setLocation(
                            manager,
                            route[0].first,
                            route[0].second
                        )
                        routeStage++
                    }

                    // 处理所有已到达的阶段
                    var shouldSkipCurrentTick = false
                    while (routeStage < route.size) {
                        val target = route[routeStage]
                        val location = MockServiceHelper.getLocation(manager)
                        if (location == null) {
                            Log.e(TAG, "Failed to get current location during route mock")
                            resetRouteMockState(disableAutoPlay = true)
                            shouldSkipCurrentTick = true
                            break
                        }
                        val currentLat = location.first
                        val currentLon = location.second

                        val inverse = Geodesic.WGS84.Inverse(
                            currentLat,
                            currentLon,
                            target.first,
                            target.second
                        )
                        // 判断距离是否小于1米（可根据需要调整阈值）
                        if (inverse.s12 < 1.0) {
                            // 精确设置位置到目标点并进入下一阶段
                            MockServiceHelper.setLocation(
                                manager,
                                target.first,
                                target.second
                            )
                            routeStage++
                        } else if (inverse.s12 < stepDistance) {
                            // 如果距离小于速度，直接移动到目标点
                            MockServiceHelper.setLocation(
                                manager,
                                target.first,
                                target.second
                            )
                            routeStage++

                        } else {
                            break
                        }
                    }

                    if (shouldSkipCurrentTick) {
                        continue@routeLoop
                    }

                    // 检查是否已完成所有阶段
                    if (routeStage >= route.size) {
                        routeLoopCompletedCount += 1
                        if (routeMockLoopEnabled && routeLoopCompletedCount < routeMockLoopCount) {
                            val interval = routeMockLoopIntervalSeconds.coerceAtLeast(0)
                            if (interval > 0) {
                                delay(interval * 1000L)
                            }
                            routeStage = 0
                            continue@routeLoop
                        }
                        resetRouteMockState(disableAutoPlay = true)
                        continue@routeLoop
                    }

                    // 处理当前目标点的移动
                    val target = route[routeStage]
                    val location = MockServiceHelper.getLocation(manager)
                    if (location == null) {
                        Log.e(TAG, "Failed to get current location before route move")
                        resetRouteMockState(disableAutoPlay = true)
                        continue
                    }
                    val currentLat = location.first
                    val currentLon = location.second

                    val inverse = Geodesic.WGS84.Inverse(
                        currentLat,
                        currentLon,
                        target.first,
                        target.second
                    )
                    var azimuth = inverse.azi1
                    if (azimuth < 0) {
                        azimuth += 360
                    }

                    Log.d(TAG, "routeMove from=$currentLat,$currentLon to=${target.first},${target.second} distanceLeft=${inverse.s12} stepDistance=$stepDistance bearing=$azimuth")
                    if (!MockServiceHelper.move(
                            manager,
                            stepDistance,
                            azimuth
                        )
                    ) {
                        Log.e(TAG, "移动失败")
                    }
                } while (isActive)
            }
        }

        return rocker
    }

    fun isServiceStart(): Boolean {
        return locationManager != null && MockServiceHelper.isServiceInit() && MockServiceHelper.isMockStart(
            locationManager!!
        )
    }

    fun resetRouteMockState(disableAutoPlay: Boolean = false) {
        routeStage = 0
        routeLoopCompletedCount = 0
        isRouteStart = false
        routeMockCoroutine.pause()
        if (disableAutoPlay && ::rocker.isInitialized) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                rocker.autoStatus = false
            } else {
                Handler(Looper.getMainLooper()).post {
                    if (::rocker.isInitialized) {
                        rocker.autoStatus = false
                    }
                }
            }
        }
    }

    private fun calculateStepDistance(delayTime: Long, speed: Double = FakeLoc.speed): Double {
        if (delayTime <= 0L) {
            return 0.0
        }
        val stepDistance = speed * delayTime / 1000.0 / MOVE_COMPENSATION
        if (FakeLoc.enableDebugLog) {
            Log.d(TAG, "calcStep speed=$speed delayMs=$delayTime compensation=$MOVE_COMPENSATION stepDistance=$stepDistance")
        }
        return stepDistance
    }
}
