package moe.fuqiuluo.xposed.hooks.telephony

import android.os.Build
import android.os.Bundle
import android.os.Parcel
import android.telephony.CellIdentity
import android.telephony.CellIdentityCdma
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoLte
import android.telephony.CellSignalStrengthCdma
import android.telephony.CellSignalStrengthLte
import android.telephony.CellIdentityLte
import android.telephony.NeighboringCellInfo
import android.telephony.SignalStrength
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import moe.fuqiuluo.xposed.utils.BinderUtils
import moe.fuqiuluo.xposed.utils.FakeLoc
import moe.fuqiuluo.xposed.utils.Logger
import moe.fuqiuluo.xposed.utils.afterHook
import moe.fuqiuluo.xposed.utils.beforeHook
import moe.fuqiuluo.xposed.utils.hookAllMethods
import moe.fuqiuluo.xposed.utils.hookBefore
import moe.fuqiuluo.xposed.utils.hookMethodBefore
import moe.fuqiuluo.xposed.utils.onceHookDoNothingMethod
import moe.fuqiuluo.xposed.utils.onceHookMethodBefore
import java.lang.reflect.Modifier


object TelephonyHook: BaseTelephonyHook() {
    operator fun invoke(classLoader: ClassLoader) {
        if(!initDivineService("TelephonyHook")) {
            Logger.error("Failed to init mock service in TelephonyHook")
            return
        }

//        kotlin.runCatching {
//            val cCellIdentityCdma =
//                XposedHelpers.findClass("android.telephony.CellIdentityCdma", classLoader)
//            val hookCdma = object: XC_MethodHook() {
//                override fun beforeHookedMethod(param: MethodHookParam?) {
//                    if (param == null) return
//
//                    if (!FakeLocationConfig.enable) return
//
//                    param.args[3] = Int.MAX_VALUE
//                    param.args[4] = Int.MAX_VALUE
//                }
//            }
//            //                                                             nid             sid                 bid            lon            lat                alphal              alphas
//            XposedHelpers.findAndHookConstructor(
//                cCellIdentityCdma,
//                Int::class.java,
//                Int::class.java,
//                Int::class.java,
//                Int::class.java,
//                Int::class.java,
//                String::class.java,
//                String::class.java,
//                hookCdma
//            )
//        }.onFailure {
//            XposedBridge.log("[Portal] Hook CellIdentityCdma failed")
//        }

//        kotlin.runCatching {
//            val cCellIdentityGsm = XposedHelpers.findClass("android.telephony.CellIdentityGsm", classLoader)
//
//        }.onFailure {
//            XposedBridge.log("[Portal] Hook CellIdentityGsm failed")
//        }

//        XposedHelpers.findClassIfExists("android.telephony.TelephonyManager", classLoader)?.let {
//            XposedBridge.hookAllMethods(it, "getNeighboringCellInfo", hookGetNeighboringCellInfoList)
//            XposedBridge.hookAllMethods(it, "getCellLocation", hookGetCellLocation)
//        }

//        kotlin.runCatching {
//            XposedHelpers.findClass("com.android.internal.telephony.ITelephony\$Stub", classLoader)
//        }.onSuccess {
//            it.declaredMethods.forEach {
//                if (it.name == "onTransact") {
//                    hookOnTransactForServiceInstance(it)
//                    return@forEach
//                }
//            }
//        }.onFailure {
//            XposedBridge.log("[Portal] ITelephony.Stub not found: ${it.stackTraceToString()}")
//        }

        Logger.info(
            "TelephonyHook installed: needDowngradeToCdma=${FakeLoc.needDowngradeToCdma}, " +
                "cellMockEnabled=${FakeLoc.cellConfig.enabled}"
        )

        val cPhoneInterfaceManager = XposedHelpers.findClassIfExists("com.android.phone.PhoneInterfaceManager", classLoader)
            ?: return

        val hookGetPhoneTyp = beforeHook {
            if (FakeLoc.enable && shouldInjectCellInfo() && !BinderUtils.isSystemAppsCall()) {
                if (FakeLoc.enableDebugLog) {
                    Logger.debug("getActivePhoneType: injected!")
                }

                result = if (FakeLoc.cellConfig.enabled && !FakeLoc.needDowngradeToCdma) 1 else 2
            }
        }

        if(cPhoneInterfaceManager.hookAllMethods("getActivePhoneType", hookGetPhoneTyp).isEmpty()) {
            Logger.error("Hook PhoneInterfaceManager.getActivePhoneType failed")
        }
        if(cPhoneInterfaceManager.hookAllMethods("getActivePhoneTypeForSlot", hookGetPhoneTyp).isEmpty()) {
            Logger.warn("Hook PhoneInterfaceManager.getActivePhoneTypeForSlot failed")
        }

        cPhoneInterfaceManager.declaredMethods.find { it.name == "getAllCellInfo" }?.let {
            val hookGetAllCellInfo = afterHook {
                if (FakeLoc.enableDebugLog) {
                    Logger.debug("getAllCellInfo: injected! caller = ${BinderUtils.getCallerUid()}")
                }

                if (FakeLoc.enable && shouldInjectCellInfo() && !BinderUtils.isSystemAppsCall()) {
                    result = buildMockCellInfoList()
                }
            }
            if (XposedBridge.hookMethod(it, hookGetAllCellInfo) == null) {
                Logger.error("Hook PhoneInterfaceManager.getAllCellInfo failed")
            }
        }

        if(XposedBridge.hookAllMethods(cPhoneInterfaceManager, "getCellLocation", afterHook {
                if (!FakeLoc.enable || !shouldInjectCellInfo() || BinderUtils.isSystemAppsCall()) {
                    return@afterHook
                }
                if (FakeLoc.enableDebugLog) {
                    Logger.debug("${method.name}: injected!")
                }

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || result.javaClass.name == "android.os.Bundle") {
                    val cell = FakeLoc.cellConfig
                    val tac = if (cell.lteTac > 0) cell.lteTac else Int.MAX_VALUE
                    val eci = if (cell.lteEci > 0) cell.lteEci else Int.MAX_VALUE
                    result = Bundle().apply {
                        putInt("cid", eci)
                        putInt("lac", tac)
                        putInt("psc", Int.MAX_VALUE)
                        putInt("baseStationLatitude", (FakeLoc.latitude * 14400.0).toInt())
                        putInt("baseStationLongitude", (FakeLoc.longitude * 14400.0).toInt())
                        putBoolean("empty", false)
                        putBoolean("emptyParcel", false)
                        putInt("mFlags", 1536)
                        putBoolean("parcelled", false)
                        putInt("baseStationId", Int.MAX_VALUE)
                        putInt("systemId", Int.MAX_VALUE)
                        putInt("networkId", Int.MAX_VALUE)
                        putInt("size", 0)
                    }
                } else {
                    result = buildMockCellIdentityForCallback()
                }
            }).isEmpty()) {
            Logger.error("Hook PhoneInterfaceManager.getCellLocation failed")
        }

        beforeHook {
            if (FakeLoc.enable && shouldInjectCellInfo() && !BinderUtils.isSystemAppsCall()) {
                if (FakeLoc.enableDebugLog) {
                    Logger.debug("getDataNetworkType: injected!")
                }
                result = if (FakeLoc.cellConfig.enabled) {
                    if (FakeLoc.cellConfig.preferNr && FakeLoc.cellConfig.nrNci > 0L) 20 else 13
                } else {
                    4
                }
            }
        }.let {
            cPhoneInterfaceManager.hookAllMethods("getDataNetworkType", it)
            cPhoneInterfaceManager.hookAllMethods("getNetworkType", it)
            cPhoneInterfaceManager.hookAllMethods("getDataNetworkTypeForSubscriber", it)
            cPhoneInterfaceManager.hookAllMethods("getNetworkTypeForSubscriber", it)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (cPhoneInterfaceManager.hookAllMethods("getNeighboringCellInfo", beforeHook {
                if (!FakeLoc.enable || !shouldInjectCellInfo() || BinderUtils.isSystemAppsCall()) {
                    return@beforeHook
                }

                result = kotlin.runCatching {
                    val nCellInfo = NeighboringCellInfo::class.java.getConstructor().newInstance()
                    XposedHelpers.setIntField(nCellInfo, "mRssi", -46)
                    XposedHelpers.setIntField(nCellInfo, "mCid", -1)
                    XposedHelpers.setIntField(nCellInfo, "mLac", -1)
                    XposedHelpers.setIntField(nCellInfo, "mPsc", -1)
                    XposedHelpers.setIntField(nCellInfo, "mNetworkType", 3)
                    listOf(nCellInfo)
                }.getOrElse {
                    arrayListOf()
                }
            }).isEmpty()) {
                Logger.error("Hook PhoneInterfaceManager.getNeighboringCellInfo failed")
            }
        }

        val cTelephonyRegistry = XposedHelpers.findClassIfExists("com.android.server.TelephonyRegistry", classLoader)
        if (cTelephonyRegistry == null) {
            Logger.error("TelephonyRegistry not found")
        } else {
            hookTelephonyRegistry(cTelephonyRegistry)
        }

    }

    fun hookTelephonyRegistry(cTelephonyRegistry: Class<*>) {
        cTelephonyRegistry.declaredMethods.filter { (it.name == "listen" || it.name == "listenWithEventList") && !Modifier.isAbstract(it.modifiers) }
            .map {
                it to it.parameterTypes.indexOfFirst { typ -> typ.simpleName == "IPhoneStateListener" }
            }.forEach {
                val (m, idx) = it
                if (idx == -1) {
                    Logger.error("IPhoneStateListener not found")
                    return@forEach
                }
                m.hookBefore {
                    if (!FakeLoc.enable || !shouldInjectCellInfo() || BinderUtils.isSystemAppsCall()) {
                        return@hookBefore
                    }

                    val listener = args[idx] as Any
                    val hasHookOnCellLocationChanged = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        listener.javaClass.onceHookMethodBefore("onCellLocationChanged", CellIdentity::class.java) {
                            if (FakeLoc.enable) {
                                result = buildMockCellIdentityForCallback()
                            }
                        }
                    } else {
                        listener.javaClass.onceHookMethodBefore("onCellLocationChanged", Bundle::class.java) {
                            if (FakeLoc.enable) {
                                val cell = FakeLoc.cellConfig
                                val tac = if (cell.lteTac > 0) cell.lteTac else Int.MAX_VALUE
                                val eci = if (cell.lteEci > 0) cell.lteEci else Int.MAX_VALUE
                                result = Bundle().apply {
                                    putInt("cid", eci)
                                    putInt("lac", tac)
                                    putInt("psc", Int.MAX_VALUE)
                                    putInt("baseStationLatitude", (FakeLoc.latitude * 14400.0).toInt())
                                    putInt("baseStationLongitude", (FakeLoc.longitude * 14400.0).toInt())
                                    putBoolean("empty", false)
                                    putBoolean("emptyParcel", false)
                                    putInt("mFlags", 1536)
                                    putBoolean("parcelled", false)
                                    putInt("baseStationId", Int.MAX_VALUE)
                                    putInt("systemId", Int.MAX_VALUE)
                                    putInt("networkId", Int.MAX_VALUE)
                                    putInt("size", 0)
                                }
                            }
                        }
                    } != null
                    if (!hasHookOnCellLocationChanged) {
                        Logger.error("Hook onCellLocationChanged failed")
                    }
                    listener.javaClass.onceHookDoNothingMethod("onSignalStrengthChanged", Int::class.java) {
                        FakeLoc.enable
                    }
                    listener.javaClass.onceHookDoNothingMethod("onSignalStrengthsChanged", SignalStrength::class.java) { FakeLoc.enable }
                }
            }

        cTelephonyRegistry.hookMethodBefore("notifyCellInfo", List::class.java) {
            if (!FakeLoc.enable || !shouldInjectCellInfo() || BinderUtils.isSystemAppsCall()) {
                return@hookMethodBefore
            }

            if (FakeLoc.enableDebugLog) {
                Logger.debug("notifyCellInfo: injected! caller=${BinderUtils.getCallerUid()}")
            }

            args[0] = buildMockCellInfoList()
        }

        cTelephonyRegistry.hookMethodBefore("notifyCellInfoForSubscriber", Int::class.java, List::class.java) {
            if (!FakeLoc.enable || !shouldInjectCellInfo() || BinderUtils.isSystemAppsCall()) {
                return@hookMethodBefore
            }

            if (FakeLoc.enableDebugLog) {
                Logger.debug("notifyCellInfoForSubscriber: injected! caller=${BinderUtils.getCallerUid()}")
            }

            args[1] = buildMockCellInfoList()
        }

        cTelephonyRegistry.hookMethodBefore("notifyCellLocation", Bundle::class.java) {
            if (!FakeLoc.enable || !shouldInjectCellInfo() || BinderUtils.isSystemAppsCall()) {
                return@hookMethodBefore
            }

            if (FakeLoc.enableDebugLog) {
                Logger.debug("notifyCellLocation: injected! caller=${BinderUtils.getCallerUid()}")
            }

            val cell = FakeLoc.cellConfig
            val tac = if (cell.lteTac > 0) cell.lteTac else Int.MAX_VALUE
            val eci = if (cell.lteEci > 0) cell.lteEci else Int.MAX_VALUE
            args[0] = Bundle().apply {
                putInt("cid", eci)
                putInt("lac", tac)
                putInt("psc", Int.MAX_VALUE)
                putInt("baseStationLatitude", (FakeLoc.latitude * 14400.0).toInt())
                putInt("baseStationLongitude", (FakeLoc.longitude * 14400.0).toInt())
                putBoolean("empty", false)
                putBoolean("emptyParcel", false)
                putInt("mFlags", 1536)
                putBoolean("parcelled", false)
                putInt("baseStationId", Int.MAX_VALUE)
                putInt("systemId", Int.MAX_VALUE)
                putInt("networkId", Int.MAX_VALUE)
                putInt("size", 0)
            }
        }
        cTelephonyRegistry.hookMethodBefore("notifyCellLocationForSubscriber", Int::class.java, Bundle::class.java) {
            if (!FakeLoc.enable || !shouldInjectCellInfo() || BinderUtils.isSystemAppsCall()) {
                return@hookMethodBefore
            }

            if (FakeLoc.enableDebugLog) {
                Logger.debug("notifyCellLocationForSubscriber: injected! caller=${BinderUtils.getCallerUid()}")
            }

            val cell = FakeLoc.cellConfig
            val tac = if (cell.lteTac > 0) cell.lteTac else Int.MAX_VALUE
            val eci = if (cell.lteEci > 0) cell.lteEci else Int.MAX_VALUE
            args[1] = Bundle().apply {
                putInt("cid", eci)
                putInt("lac", tac)
                putInt("psc", Int.MAX_VALUE)
                putInt("baseStationLatitude", (FakeLoc.latitude * 14400.0).toInt())
                putInt("baseStationLongitude", (FakeLoc.longitude * 14400.0).toInt())
                putBoolean("empty", false)
                putBoolean("emptyParcel", false)
                putInt("mFlags", 1536)
                putBoolean("parcelled", false)
                putInt("baseStationId", Int.MAX_VALUE)
                putInt("systemId", Int.MAX_VALUE)
                putInt("networkId", Int.MAX_VALUE)
                putInt("size", 0)
            }
        }
    }

    private fun buildMockCellInfoList(): List<CellInfo> {
        val infos = arrayListOf<CellInfo>()
        if (FakeLoc.cellConfig.enabled) {
            if (FakeLoc.enableDebugLog) {
                Logger.debug(
                    "buildMockCellInfoList: mcc=${FakeLoc.cellConfig.mcc}, mnc=${FakeLoc.cellConfig.mnc}, " +
                        "lteTac=${FakeLoc.cellConfig.lteTac}, lteEci=${FakeLoc.cellConfig.lteEci}, nrNci=${FakeLoc.cellConfig.nrNci}"
                )
            }
            val lte = buildLteCellInfo()
            if (lte != null) {
                infos.add(lte)
            }
            if (FakeLoc.cellConfig.nrNci > 0L) {
                val nr = buildNrCellInfo()
                if (nr != null) {
                    infos.add(nr)
                }
            }
        }
        if (infos.isEmpty()) {
            infos.add(buildCdmaCellInfo())
        }
        return infos
    }

    private fun shouldInjectCellInfo(): Boolean {
        return FakeLoc.needDowngradeToCdma || FakeLoc.cellConfig.enabled
    }

    private fun buildLteCellInfo(): CellInfo? = kotlin.runCatching {
        val cfg = FakeLoc.cellConfig
        val info = CellInfoLte::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        val identity = CellIdentityLte::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        val signal = CellSignalStrengthLte::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()

        XposedHelpers.setIntField(identity, "mCi", cfg.lteEci)
        XposedHelpers.setIntField(identity, "mTac", cfg.lteTac)
        XposedHelpers.setIntField(identity, "mPci", cfg.ltePci)
        XposedHelpers.setIntField(identity, "mEarfcn", cfg.lteEarfcn)
        XposedHelpers.setObjectField(identity, "mMccStr", cfg.mcc.toString())
        XposedHelpers.setObjectField(identity, "mMncStr", cfg.mnc.toString())

        XposedHelpers.setIntField(signal, "mRsrp", -95)
        XposedHelpers.setIntField(signal, "mRsrq", -11)
        XposedHelpers.setIntField(signal, "mRssnr", 70)

        XposedHelpers.setObjectField(info, "mCellIdentity", identity)
        XposedHelpers.setObjectField(info, "mCellSignalStrength", signal)
        XposedHelpers.callMethod(info, "setRegistered", true)
        XposedHelpers.callMethod(info, "setTimeStamp", System.nanoTime())
        XposedHelpers.callMethod(info, "setCellConnectionStatus", 0)
        info
    }.getOrNull()

    private fun buildNrCellInfo(): CellInfo? {
        val cfg = FakeLoc.cellConfig
        val cl = javaClass.classLoader ?: return null
        val cInfoNr = XposedHelpers.findClassIfExists("android.telephony.CellInfoNr", cl) ?: return null
        val cIdentityNr = XposedHelpers.findClassIfExists("android.telephony.CellIdentityNr", cl) ?: return null
        val cSignalNr = XposedHelpers.findClassIfExists("android.telephony.CellSignalStrengthNr", cl) ?: return null

        return kotlin.runCatching {
            val info = cInfoNr.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
            val identity = cIdentityNr.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
            val signal = cSignalNr.getDeclaredConstructor().apply { isAccessible = true }.newInstance()

            XposedHelpers.setLongField(identity, "mNci", cfg.nrNci)
            XposedHelpers.setIntField(identity, "mTac", cfg.lteTac)
            XposedHelpers.setIntField(identity, "mPci", cfg.nrPci)
            XposedHelpers.setIntField(identity, "mNrArfcn", cfg.nrArfcn)
            XposedHelpers.setObjectField(identity, "mMccStr", cfg.mcc.toString())
            XposedHelpers.setObjectField(identity, "mMncStr", cfg.mnc.toString())

            XposedHelpers.setIntField(signal, "mCsiRsrp", -100)
            XposedHelpers.setIntField(signal, "mCsiRsrq", -12)
            XposedHelpers.setIntField(signal, "mCsiSinr", 15)
            XposedHelpers.setIntField(signal, "mSsRsrp", -99)
            XposedHelpers.setIntField(signal, "mSsRsrq", -10)
            XposedHelpers.setIntField(signal, "mSsSinr", 18)

            XposedHelpers.setObjectField(info, "mCellIdentity", identity)
            XposedHelpers.setObjectField(info, "mCellSignalStrength", signal)
            XposedHelpers.callMethod(info, "setRegistered", false)
            XposedHelpers.callMethod(info, "setTimeStamp", System.nanoTime())
            XposedHelpers.callMethod(info, "setCellConnectionStatus", 1)
            info as CellInfo
        }.getOrNull()
    }

    private fun buildCdmaCellInfo(): CellInfo {
        return kotlin.runCatching {
            CellInfoCdma::class.java.getConstructor().newInstance().also {
                XposedHelpers.callMethod(it, "setRegistered", true)
                XposedHelpers.callMethod(it, "setTimeStamp", System.nanoTime())
                XposedHelpers.callMethod(it, "setCellConnectionStatus", 0)
            }
        }.getOrElse {
            CellInfoCdma::class.java.getConstructor(
                Int::class.java,
                Boolean::class.java,
                Long::class.java,
                CellIdentityCdma::class.java,
                CellSignalStrengthCdma::class.java
            ).newInstance(
                0,
                true,
                System.nanoTime(),
                CellIdentityCdma::class.java.newInstance(),
                CellSignalStrengthCdma::class.java.newInstance()
            )
        }
    }

    private fun buildMockCellIdentityForCallback(): Any {
        if (FakeLoc.cellConfig.enabled) {
            val lteIdentity = kotlin.runCatching {
                val cfg = FakeLoc.cellConfig
                val identity = CellIdentityLte::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
                XposedHelpers.setIntField(identity, "mCi", cfg.lteEci)
                XposedHelpers.setIntField(identity, "mTac", cfg.lteTac)
                XposedHelpers.setIntField(identity, "mPci", cfg.ltePci)
                XposedHelpers.setIntField(identity, "mEarfcn", cfg.lteEarfcn)
                XposedHelpers.setObjectField(identity, "mMccStr", cfg.mcc.toString())
                XposedHelpers.setObjectField(identity, "mMncStr", cfg.mnc.toString())
                identity
            }.getOrNull()
            if (lteIdentity != null) return lteIdentity
        }
        return CellIdentityCdma::class.java.getConstructor(
            Int::class.java,
            Int::class.java,
            Int::class.java,
            Int::class.java,
            Int::class.java,
            String::class.java,
            String::class.java
        ).newInstance(
            Int.MAX_VALUE,
            Int.MAX_VALUE,
            Int.MAX_VALUE,
            (FakeLoc.latitude * 14400.0).toInt(),
            (FakeLoc.longitude * 14400.0).toInt(),
            null,
            null
        )
    }

    @Suppress("LocalVariableName")
    fun hookSubOnTransact(classLoader: ClassLoader) {
        val cISub = XposedHelpers.findClassIfExists("com.android.internal.telephony.ISub\$Stub", classLoader)
        if (cISub == null) {
            Logger.error("ISub.Stub not found")
            return
        }

        val subClassName = "com.android.internal.telephony.ISub"
        val TRANSACTION_getActiveSubInfoCount = kotlin.runCatching { XposedHelpers.getStaticIntField(cISub, "TRANSACTION_getActiveSubInfoCount") }.getOrDefault(-1)
        val TRANSACTION_getActiveSubInfoCountMax = kotlin.runCatching { XposedHelpers.getStaticIntField(cISub, "TRANSACTION_getActiveSubInfoCountMax") }.getOrDefault(-1)
        val TRANSACTION_getActiveSubscriptionInfoList = kotlin.runCatching { XposedHelpers.getStaticIntField(cISub, "TRANSACTION_getActiveSubscriptionInfoList") }.getOrDefault(-1)
        val TRANSACTION_getPhoneId = kotlin.runCatching { XposedHelpers.getStaticIntField(cISub, "TRANSACTION_getPhoneId") }.getOrDefault(-1)
        val TRANSACTION_getSimStateForSlotIndex = kotlin.runCatching { XposedHelpers.getStaticIntField(cISub, "TRANSACTION_getSimStateForSlotIndex") }.getOrDefault(-1)
        val TRANSACTION_isActiveSubId = kotlin.runCatching { XposedHelpers.getStaticIntField(cISub, "TRANSACTION_isActiveSubId") }.getOrDefault(-1)
        val TRANSACTION_getNetworkCountryIsoForPhone = kotlin.runCatching { XposedHelpers.getStaticIntField(cISub, "TRANSACTION_getNetworkCountryIsoForPhone") }.getOrDefault(-1)
        val TRANSACTION_getSimStateForSubscriber = kotlin.runCatching { XposedHelpers.getStaticIntField(cISub, "TRANSACTION_getSimStateForSubscriber") }.getOrDefault(-1)
        val TRANSACTION_getSimStateForSlotIdx = kotlin.runCatching { XposedHelpers.getStaticIntField(cISub, "TRANSACTION_getSimStateForSlotIdx") }.getOrDefault(-1)

        val hookOnTransact = beforeHook {
            if (!FakeLoc.enable || BinderUtils.isSystemAppsCall()) {
                return@beforeHook
            }

            val code = args[0] as Int
            val data = args[1] as Parcel
            val reply = args[2] as Parcel
            val flags = args[3] as Int

            if (code == -1) return@beforeHook

            when (code) {
                TRANSACTION_isActiveSubId -> {
                    data.enforceInterface(subClassName)
                    data.readInt()
                    reply.writeNoException()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        reply.writeBoolean(true)
                    } else {
                        reply.writeInt(1)
                    }
                    result = true
                }
                TRANSACTION_getSimStateForSlotIndex -> {
                    data.enforceInterface(subClassName)
                    data.readInt()
                    reply.writeNoException()
                    reply.writeInt(5)
                    result = true
                }
                TRANSACTION_getSimStateForSubscriber -> {
                    data.enforceInterface(subClassName)
                    data.readInt()
                    reply.writeNoException()
                    reply.writeInt(5)
                    result = true
                }
                TRANSACTION_getSimStateForSlotIdx -> {
                    data.enforceInterface(subClassName)
                    data.readInt()
                    reply.writeNoException()
                    reply.writeInt(5)
                    result = true
                }
//                TRANSACTION_getActiveSubInfoCount -> {
//                    data.enforceInterface(subClassName)
//                    data.readString()
//                    reply.writeNoException()
//                    reply.writeInt(1)
//                    result = true
//                }
//                TRANSACTION_getActiveSubscriptionInfoList -> {
//                    data.enforceInterface(subClassName)
//                    data.readString()
//                    reply.writeNoException()
//                    reply.writeTypedList(arrayListOf())
//                    result = true
//                }
//                TRANSACTION_getActiveSubInfoCountMax -> {
//                    data.enforceInterface(subClassName)
//                    reply.writeNoException()
//                    reply.writeInt(1)
//                    result = true
//                }
                TRANSACTION_getNetworkCountryIsoForPhone -> {
                    data.enforceInterface(subClassName)
                    data.readInt()
                    reply.writeNoException()
                    reply.writeString("CHN")
                    result = true
                }
                TRANSACTION_getPhoneId -> {
                    data.enforceInterface(subClassName)
                    data.readInt()
                    reply.writeNoException()
                    reply.writeInt(0)
                    result = true
                }
            }
        }
        if(cISub.hookAllMethods("onTransact", hookOnTransact).isEmpty()) {
            Logger.error("Hook ISub.Stub.onTransact failed")
        }
    }

//    private fun hookOnTransactForServiceInstance(m: Method) {
//        var hook: XC_MethodHook.Unhook? = null
//        hook = XposedBridge.hookMethod(m, object : XC_MethodHook() {
//            override fun beforeHookedMethod(param: MethodHookParam?) {
//                if (param == null) return
//
//                val thisObject = param.thisObject
//
//                if (hook == null || thisObject == null) return
//                onFetchServiceInstance(thisObject)
//                hook?.unhook()
//                hook = null
//            }
//        })
//    }

//    private fun onFetchServiceInstance(thisObject: Any) {
//        val cITelephony = thisObject.javaClass
//
//        println("[Portal] found " + cITelephony.declaredMethods.mapNotNull {
//            if (it.returnType.javaClass.name.contains("CellLocation")) {
//                if (FakeLocationConfig.DEBUG) {
//                    XposedBridge.log("[Portal] hook method: $it")
//                }
//                XposedBridge.hookMethod(it, hookGetCellLocation)
//            } else null
//        }.size + " methods(CellLocation) to hook in ITelephony\$Stub")
//
//        XposedBridge.hookAllMethods(cITelephony, "getNeighboringCellInfo", hookGetNeighboringCellInfoList)
//    }
}
