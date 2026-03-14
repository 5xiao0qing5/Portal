@file:Suppress("UNCHECKED_CAST")
package moe.fuqiuluo.xposed.hooks.sensor

import android.content.pm.FeatureInfo
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.os.SystemClock
import android.util.ArrayMap
import de.robv.android.xposed.XposedHelpers
import moe.fuqiuluo.xposed.utils.FakeLoc
import moe.fuqiuluo.xposed.utils.Logger
import moe.fuqiuluo.xposed.utils.afterHook
import moe.fuqiuluo.xposed.utils.beforeHook
import moe.fuqiuluo.xposed.utils.hookAllMethods
import moe.fuqiuluo.xposed.utils.hookMethodAfter
import moe.fuqiuluo.xposed.utils.onceHook
import moe.fuqiuluo.xposed.utils.onceHookAllMethod
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.floor

object SystemSensorManagerHook {
    private data class ListenerKey(
        val listener: SensorEventListener,
        val sensorType: Int,
    )

    private data class SensorRegistration(
        val listener: SensorEventListener,
        val sensor: Sensor,
        var lastCounterDispatched: Long = 0,
        var lastDetectorDispatched: Long = 0,
        val stepCounterOffset: Float = 1000f,
    )

    private val registrations = ConcurrentHashMap<ListenerKey, SensorRegistration>()
    private val syntheticDispatch = ThreadLocal.withInitial { false }
    private var schedulerStarted = false
    private var totalSimulatedSteps = 0.0
    private var lastStepUpdateNanos = 0L

    private val sensorEventConstructor: Constructor<*>? by lazy {
        kotlin.runCatching {
            SensorEvent::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType).apply {
                isAccessible = true
            }
        }.getOrNull()
    }

    private val sensorField: Field? by lazy {
        kotlin.runCatching {
            SensorEvent::class.java.getDeclaredField("sensor").apply { isAccessible = true }
        }.getOrNull()
    }

    private val accuracyField: Field? by lazy {
        kotlin.runCatching {
            SensorEvent::class.java.getDeclaredField("accuracy").apply { isAccessible = true }
        }.getOrNull()
    }

    private val timestampField: Field? by lazy {
        kotlin.runCatching {
            SensorEvent::class.java.getDeclaredField("timestamp").apply { isAccessible = true }
        }.getOrNull()
    }

    private val scheduler: ScheduledExecutorService by lazy {
        Executors.newSingleThreadScheduledExecutor {
            Thread(it, "Portal-StepMock").apply { isDaemon = true }
        }
    }

    operator fun invoke(classLoader: ClassLoader) {
        unlockGeoSensor(classLoader)
        hookSystemSensorManager(classLoader)
        startStepSchedulerIfNeeded()
    }

    private fun startStepSchedulerIfNeeded() {
        if (schedulerStarted) {
            return
        }
        schedulerStarted = true
        scheduler.scheduleAtFixedRate({
            kotlin.runCatching { dispatchSyntheticSteps() }
                .onFailure { Logger.error("Failed to dispatch synthetic steps", it) }
        }, 400, 400, TimeUnit.MILLISECONDS)
    }

    private fun dispatchSyntheticSteps() {
        if (registrations.isEmpty()) {
            lastStepUpdateNanos = 0L
            return
        }

        val now = SystemClock.elapsedRealtimeNanos()
        updateStepCounter(now)
        val currentWholeSteps = floor(totalSimulatedSteps).toLong()
        if (currentWholeSteps <= 0L) {
            return
        }

        registrations.values.forEach { registration ->
            when (registration.sensor.type) {
                Sensor.TYPE_STEP_COUNTER -> {
                    if (currentWholeSteps > registration.lastCounterDispatched) {
                        registration.lastCounterDispatched = currentWholeSteps
                        if (FakeLoc.enableDebugLog) {
                            Logger.debug(
                                "stepDispatch type=STEP_COUNTER listener=${registration.listener.javaClass.name} value=${registration.stepCounterOffset + currentWholeSteps.toFloat()} totalSteps=$currentWholeSteps"
                            )
                        }
                        emitSensorChanged(
                            registration.listener,
                            registration.sensor,
                            registration.stepCounterOffset + currentWholeSteps.toFloat(),
                            now
                        )
                    }
                }

                Sensor.TYPE_STEP_DETECTOR -> {
                    val stepsDue = currentWholeSteps - registration.lastDetectorDispatched
                    if (stepsDue <= 0) {
                        return@forEach
                    }
                    val dispatchCount = stepsDue.coerceAtMost(8)
                    if (FakeLoc.enableDebugLog) {
                        Logger.debug(
                            "stepDispatch type=STEP_DETECTOR listener=${registration.listener.javaClass.name} stepsDue=$stepsDue dispatchCount=$dispatchCount totalSteps=$currentWholeSteps"
                        )
                    }
                    repeat(dispatchCount.toInt()) {
                        emitSensorChanged(registration.listener, registration.sensor, 1.0f, now)
                    }
                    registration.lastDetectorDispatched = currentWholeSteps
                }
            }
        }
    }

    private fun updateStepCounter(now: Long) {
        if (lastStepUpdateNanos == 0L) {
            lastStepUpdateNanos = now
            return
        }

        val deltaSeconds = ((now - lastStepUpdateNanos).coerceAtLeast(0L)) / 1_000_000_000.0
        lastStepUpdateNanos = now

        if (!FakeLoc.enable || !FakeLoc.enableSensorMock || !FakeLoc.sensorMotionActive) {
            return
        }

        val speed = FakeLoc.speed.coerceAtLeast(0.0)
        if (speed < 0.3) {
            return
        }

        val estimatedStepLengthMeters = when {
            speed < 1.5 -> 0.65
            speed < 2.5 -> 0.78
            else -> 0.90
        }
        val stepsPerSecond = (speed / estimatedStepLengthMeters).coerceIn(0.5, 4.0)
        totalSimulatedSteps += deltaSeconds * stepsPerSecond

        if (FakeLoc.enableDebugLog) {
            Logger.debug(
                "stepTick speed=$speed moving=${FakeLoc.sensorMotionActive} stepLength=$estimatedStepLengthMeters totalSteps=$totalSimulatedSteps"
            )
        }
    }

    private fun emitSensorChanged(
        listener: SensorEventListener,
        sensor: Sensor,
        value: Float,
        timestampNanos: Long,
    ) {
        val constructor = sensorEventConstructor ?: return
        val sensorField = sensorField ?: return
        val accuracyField = accuracyField ?: return
        val timestampField = timestampField ?: return

        kotlin.runCatching {
            val event = constructor.newInstance(1) as SensorEvent
            event.values[0] = value
            sensorField.set(event, sensor)
            accuracyField.setInt(event, 3)
            timestampField.setLong(event, timestampNanos)
            syntheticDispatch.set(true)
            listener.onSensorChanged(event)
        }.onFailure {
            Logger.error("Failed to emit synthetic sensor event", it)
        }.also {
            syntheticDispatch.set(false)
        }
    }

    private fun hookSystemSensorManager(classLoader: ClassLoader) {
        val cSystemSensorManager = XposedHelpers.findClassIfExists("android.hardware.SystemSensorManager", classLoader)
        if (cSystemSensorManager == null) {
            if (FakeLoc.enableDebugLog) {
                Logger.debug("Failed to find SystemSensorManager")
            }
            return
        }

        val hookRegisterListenerImpl = beforeHook {
            val listener = args[0] as? SensorEventListener ?: return@beforeHook
            val sensor = args[1] as? Sensor ?: return@beforeHook
            if (FakeLoc.enableDebugLog) {
                Logger.debug("RegisterListenerImpl: $listener, sensor=$sensor type=${sensor.type}")
            }

            if (sensor.type != Sensor.TYPE_STEP_COUNTER && sensor.type != Sensor.TYPE_STEP_DETECTOR) {
                return@beforeHook
            }

            val key = ListenerKey(listener, sensor.type)
            registrations[key] = SensorRegistration(listener = listener, sensor = sensor)
            if (FakeLoc.enableDebugLog) {
                Logger.debug(
                    "stepRegister listener=${listener.javaClass.name} type=${sensor.type} registrations=${registrations.size} hookEnabled=${FakeLoc.enableSensorMock}"
                )
            }

            listener.javaClass.onceHookAllMethod("onSensorChanged", beforeHook {
                if (syntheticDispatch.get() == true) {
                    return@beforeHook
                }
                val event = args.firstOrNull() as? SensorEvent ?: return@beforeHook
                val eventSensor = event.sensor ?: return@beforeHook
                if (!FakeLoc.enableSensorMock) {
                    return@beforeHook
                }
                if (eventSensor.type == Sensor.TYPE_STEP_COUNTER || eventSensor.type == Sensor.TYPE_STEP_DETECTOR) {
                    if (FakeLoc.enableDebugLog) {
                        Logger.debug(
                            "stepBlockRealEvent listener=${listener.javaClass.name} type=${eventSensor.type} value=${event.values.firstOrNull()}"
                        )
                    }
                    result = null
                }
            })
        }
        cSystemSensorManager.declaredMethods.filter {
            it.name == "registerListenerImpl" &&
                it.parameterTypes.isNotEmpty() &&
                it.parameterTypes[0] == SensorEventListener::class.java &&
                it.parameterTypes[1] == Sensor::class.java
        }.forEach {
            it.onceHook(hookRegisterListenerImpl)
        }

        val hookUnregisterListenerImpl = beforeHook {
            val listener = args[0] as? SensorEventListener ?: return@beforeHook
            val sensor = args.getOrNull(1) as? Sensor
            if (FakeLoc.enableDebugLog) {
                Logger.debug("UnregisterListenerImpl: $listener sensor=${sensor?.type}")
            }
            if (sensor != null) {
                registrations.remove(ListenerKey(listener, sensor.type))
            } else {
                registrations.keys.removeIf { it.listener == listener }
            }
            if (FakeLoc.enableDebugLog) {
                Logger.debug(
                    "stepUnregister listener=${listener.javaClass.name} sensor=${sensor?.type} registrations=${registrations.size}"
                )
            }
        }
        cSystemSensorManager.declaredMethods.filter {
            it.name == "unregisterListenerImpl" &&
                it.parameterTypes.isNotEmpty() &&
                it.parameterTypes[0] == SensorEventListener::class.java
        }.forEach {
            it.onceHook(hookUnregisterListenerImpl)
        }

        cSystemSensorManager.hookAllMethods("getSensorList", afterHook {
            if (FakeLoc.enableDebugLog) {
                Logger.debug("getSensorList: type=${args[0]} -> $result")
            }
        })
        cSystemSensorManager.hookAllMethods("getFullSensorsList", afterHook {
            if (FakeLoc.enableDebugLog) {
                Logger.debug("getFullSensorsList -> $result")
            }
        })
    }

    private fun unlockGeoSensor(classLoader: ClassLoader) {
        val cSystemConfig = XposedHelpers.findClassIfExists("com.android.server.SystemConfig", classLoader)
            ?: return

        val openGLVersion = run {
            val cSystemProperties = XposedHelpers.findClassIfExists("android.os.SystemProperties", classLoader)
                ?: return@run 0
            XposedHelpers.callStaticMethod(
                cSystemProperties,
                "getInt",
                "ro.opengles.version",
                FeatureInfo.GL_ES_VERSION_UNDEFINED
            ) as Int
        }

        cSystemConfig.hookMethodAfter("getAvailableFeatures") {
            val features = result as ArrayMap<String, FeatureInfo>
            if (FakeLoc.enableDebugLog) {
                Logger.debug("getAvailableFeatures: ${features.keys}")
            }

            if (!features.contains(PackageManager.FEATURE_SENSOR_STEP_COUNTER)) {
                val feature = FeatureInfo()
                feature.name = PackageManager.FEATURE_SENSOR_STEP_COUNTER
                feature.reqGlEsVersion = openGLVersion
                features[PackageManager.FEATURE_SENSOR_STEP_COUNTER] = feature
            }
            if (!features.contains(PackageManager.FEATURE_SENSOR_STEP_DETECTOR)) {
                val feature = FeatureInfo()
                feature.name = PackageManager.FEATURE_SENSOR_STEP_DETECTOR
                feature.reqGlEsVersion = openGLVersion
                features[PackageManager.FEATURE_SENSOR_STEP_DETECTOR] = feature
            }
            result = features
        }
    }
}
