package moe.fuqiuluo.portal.ext

import android.content.Context
import moe.fuqiuluo.portal.ui.mock.HistoricalLocation
import moe.fuqiuluo.portal.ui.mock.HistoricalRoute
import moe.fuqiuluo.portal.ui.theme.ThemePreset

val Context.sharedPrefs
    get() = PortalPrefs.prefs(this)

var Context.selectLocation: HistoricalLocation?
    get() = PortalPrefs.getSelectedLocation(this)
    set(value) {
        PortalPrefs.setSelectedLocation(this, value)
    }

var Context.selectRoute: HistoricalRoute?
    get() = PortalPrefs.getSelectedRoute(this)
    set(value) {
        PortalPrefs.setSelectedRoute(this, value)
    }

val Context.historicalLocations: List<HistoricalLocation>
    get() = PortalPrefs.getHistoricalLocations(this)

var Context.rawHistoricalLocations: Set<String>
    get() = PortalPrefs.getRawHistoricalLocations(this)
    set(value) {
        PortalPrefs.setRawHistoricalLocations(this, value)
    }

var Context.jsonHistoricalRoutes: String
    get() = PortalPrefs.getJsonHistoricalRoutes(this)
    set(value) {
        PortalPrefs.setJsonHistoricalRoutes(this, value)
    }

var Context.reportDuration: Int
    get() = PortalPrefs.readConfig(this).reportDuration
    set(value) {
        PortalPrefs.updateConfig(this) { it.copy(reportDuration = value) }
    }

var Context.minSatelliteCount: Int
    get() = PortalPrefs.readConfig(this).minSatelliteCount
    set(value) {
        PortalPrefs.updateConfig(this) { it.copy(minSatelliteCount = value) }
    }

var Context.mapType: Int
    get() = PortalPrefs.getMapType(this)
    set(value) {
        PortalPrefs.setMapType(this, value)
    }

var Context.rockerCoords: Pair<Int, Int>
    get() = PortalPrefs.getRockerCoords(this)
    set(value) {
        PortalPrefs.setRockerCoords(this, value)
    }

var Context.speed: Double
    get() = PortalPrefs.readConfig(this).speed
    set(value) {
        PortalPrefs.updateConfig(this) { it.copy(speed = value) }
    }

var Context.altitude: Double
    get() = PortalPrefs.readConfig(this).altitude
    set(value) {
        PortalPrefs.updateConfig(this) { it.copy(altitude = value) }
    }

var Context.accuracy: Float
    get() = PortalPrefs.readConfig(this).accuracy
    set(value) {
        PortalPrefs.updateConfig(this) { it.copy(accuracy = value) }
    }

var Context.needOpenSELinux: Boolean
    get() = PortalPrefs.readConfig(this).needOpenSELinux
    set(value) {
        PortalPrefs.updateConfig(this) { it.copy(needOpenSELinux = value) }
    }

var Context.needDowngradeToCdma: Boolean
    get() = PortalPrefs.readConfig(this).needDowngradeToCdma
    set(value) {
        PortalPrefs.updateConfig(this) { it.copy(needDowngradeToCdma = value) }
    }

var Context.hookSensor: Boolean
    get() = PortalPrefs.readConfig(this).hookSensor
    set(value) {
        PortalPrefs.updateConfig(this) { it.copy(hookSensor = value) }
    }

var Context.debug: Boolean
    get() = PortalPrefs.readConfig(this).debug
    set(value) {
        PortalPrefs.updateConfig(this) { it.copy(debug = value) }
    }

var Context.disableGetCurrentLocation: Boolean
    get() = PortalPrefs.readConfig(this).disableGetCurrentLocation
    set(value) {
        PortalPrefs.updateConfig(this) { it.copy(disableGetCurrentLocation = value) }
    }

var Context.disableRegisterLocationListener: Boolean
    get() = PortalPrefs.readConfig(this).disableRegisterLocationListener
    set(value) {
        PortalPrefs.updateConfig(this) { it.copy(disableRegisterLocationListener = value) }
    }

var Context.disableFusedProvider: Boolean
    get() = PortalPrefs.readConfig(this).disableFusedProvider
    set(value) {
        PortalPrefs.updateConfig(this) { it.copy(disableFusedProvider = value) }
    }

var Context.enableRequestGeofence: Boolean
    get() = PortalPrefs.readConfig(this).enableRequestGeofence
    set(value) {
        PortalPrefs.updateConfig(this) { it.copy(enableRequestGeofence = value) }
    }

var Context.enableGetFromLocation: Boolean
    get() = PortalPrefs.readConfig(this).enableGetFromLocation
    set(value) {
        PortalPrefs.updateConfig(this) { it.copy(enableGetFromLocation = value) }
    }

var Context.enableAGPS: Boolean
    get() = PortalPrefs.readConfig(this).enableAGPS
    set(value) {
        PortalPrefs.updateConfig(this) { it.copy(enableAGPS = value) }
    }

var Context.enableNMEA: Boolean
    get() = PortalPrefs.readConfig(this).enableNMEA
    set(value) {
        PortalPrefs.updateConfig(this) { it.copy(enableNMEA = value) }
    }

var Context.disableWifiScan: Boolean
    get() = PortalPrefs.readConfig(this).disableWifiScan
    set(value) {
        PortalPrefs.updateConfig(this) { it.copy(disableWifiScan = value) }
    }

var Context.stableStaticLocation: Boolean
    get() = PortalPrefs.readConfig(this).stableStaticLocation
    set(value) {
        PortalPrefs.updateConfig(this) { it.copy(stableStaticLocation = value) }
    }

var Context.loopBroadcastlocation: Boolean
    get() = PortalPrefs.readConfig(this).loopBroadcastLocation
    set(value) {
        PortalPrefs.updateConfig(this) { it.copy(loopBroadcastLocation = value) }
    }

var Context.enableCellMock: Boolean
    get() = PortalPrefs.readConfig(this).enableCellMock
    set(value) {
        PortalPrefs.updateConfig(this) { it.copy(enableCellMock = value) }
    }

var Context.preferNrCell: Boolean
    get() = PortalPrefs.readConfig(this).preferNrCell
    set(value) {
        PortalPrefs.updateConfig(this) { it.copy(preferNrCell = value) }
    }

var Context.openCellIdToken: String
    get() = PortalPrefs.readConfig(this).openCellIdToken
    set(value) {
        PortalPrefs.updateConfig(this) { it.copy(openCellIdToken = value) }
    }

var Context.routeMockSpeed: Float
    get() = PortalPrefs.readConfig(this).routeMockSpeed
    set(value) {
        PortalPrefs.updateConfig(this) { it.copy(routeMockSpeed = value) }
    }

var Context.routeMockSpeedFluctuationEnabled: Boolean
    get() = PortalPrefs.readConfig(this).routeMockSpeedFluctuationEnabled
    set(value) {
        PortalPrefs.updateConfig(this) { it.copy(routeMockSpeedFluctuationEnabled = value) }
    }

var Context.routeMockStepFrequencyEnabled: Boolean
    get() = PortalPrefs.readConfig(this).routeMockStepFrequencyEnabled
    set(value) {
        PortalPrefs.updateConfig(this) { it.copy(routeMockStepFrequencyEnabled = value) }
    }

var Context.routeMockLoopEnabled: Boolean
    get() = PortalPrefs.readConfig(this).routeMockLoopEnabled
    set(value) {
        PortalPrefs.updateConfig(this) { it.copy(routeMockLoopEnabled = value) }
    }

var Context.routeMockLoopCount: Int
    get() = PortalPrefs.readConfig(this).routeMockLoopCount
    set(value) {
        PortalPrefs.updateConfig(this) { it.copy(routeMockLoopCount = value) }
    }

var Context.routeMockLoopIntervalSeconds: Int
    get() = PortalPrefs.readConfig(this).routeMockLoopIntervalSeconds
    set(value) {
        PortalPrefs.updateConfig(this) { it.copy(routeMockLoopIntervalSeconds = value) }
    }

var Context.themePresetKey: String
    get() = PortalPrefs.readConfig(this).themePresetKey
    set(value) {
        PortalPrefs.updateConfig(this) { it.copy(themePresetKey = value) }
    }

val Context.themePreset: ThemePreset
    get() = ThemePreset.fromKey(themePresetKey)
