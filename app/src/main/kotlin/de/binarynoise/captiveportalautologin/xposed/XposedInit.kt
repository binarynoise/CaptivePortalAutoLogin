package de.binarynoise.captiveportalautologin.xposed

import de.binarynoise.captiveportalautologin.BuildConfig
import de.binarynoise.logger.Logger
import de.binarynoise.logger.Logger.log
import de.binarynoise.logger.PlatformImpl.Companion.toXposedBridge
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage

class XposedInit : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == BuildConfig.APPLICATION_ID) return
        
        Logger.Config.apply {
            toSOut = true
            
            toXposedBridge = BuildConfig.DEBUG
        }
        
        log("handleLoadPackage ${lpparam.packageName} with process ${lpparam.processName} and pid ${android.os.Process.myPid()}")
        
        when (lpparam.packageName) {
            "com.android.providers.telephony", "com.android.server.telecom" -> ReevaluationHook().handleLoadPackage(lpparam)
            "com.android.systemui" -> LocationIndicatorHook().handleLoadPackage(lpparam)
        }
    }
}
