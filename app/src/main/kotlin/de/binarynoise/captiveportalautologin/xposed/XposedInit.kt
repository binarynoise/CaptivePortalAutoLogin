package de.binarynoise.captiveportalautologin.xposed

import androidx.annotation.Keep
import de.binarynoise.captiveportalautologin.BuildConfig
import de.binarynoise.logger.Logger
import de.binarynoise.logger.Logger.log
import de.binarynoise.logger.PlatformImpl.Companion.toXposedBridge
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedInit : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        Logger.Config.apply {
            toSOut = true
            
            toXposedBridge = BuildConfig.DEBUG && lpparam.packageName != BuildConfig.APPLICATION_ID
        }
        
        log("handleLoadPackage ${lpparam.packageName} with process ${lpparam.processName} and pid ${android.os.Process.myPid()}")
        
        when (lpparam.packageName) {
            BuildConfig.APPLICATION_ID -> SelfHook().handleLoadPackage(lpparam)
            "com.android.providers.telephony", "com.android.server.telecom" -> ReevaluationHook().handleLoadPackage(lpparam)
            "com.android.systemui" -> {
                LocationIndicatorHook().handleLoadPackage(lpparam)
                DoNotAutoOpenCaptivePortalHook().handleLoadPackage(lpparam)
            }
            "com.android.networkstack" -> NetworkConnectedHook().handleLoadPackage(lpparam)
            "com.android.settings" -> DoNotAutoOpenCaptivePortalHook().handleLoadPackage(lpparam)
            else -> log("${BuildConfig.APPLICATION_ID} doesn't know how to hook ${lpparam.packageName}")
        }
    }
}

object Xposed {
    @Keep
    fun getEnabled(): Boolean {
        return false
    }
}
