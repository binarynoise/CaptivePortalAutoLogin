package de.binarynoise.captiveportalautologin.xposed

import androidx.annotation.Keep
import de.binarynoise.captiveportalautologin.BuildConfig
import de.binarynoise.logger.Logger
import de.binarynoise.logger.Logger.log
import de.binarynoise.logger.PlatformImpl.Companion.toXposedBridge
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class SelfHook : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != BuildConfig.APPLICATION_ID) return
        applyLoggerConfig(lpparam)
        log("${this::class.simpleName} handleLoadPackage ${lpparam.packageName} with process ${lpparam.processName} and pid ${android.os.Process.myPid()}")
        
        val cls = Class.forName(Xposed::class.qualifiedName!!, false, lpparam.classLoader)
        XposedHelpers.findAndHookMethod(
            cls,
            Xposed::getEnabled.name,
            XC_MethodReplacement.returnConstant(true),
        )
    }
}

object Xposed {
    @Keep
    fun getEnabled(): Boolean {
        return false
    }
}

inline fun applyLoggerConfig(lpparam: XC_LoadPackage.LoadPackageParam) {
    Logger.Config.apply {
        toSOut = true
        toXposedBridge = BuildConfig.DEBUG && lpparam.packageName != BuildConfig.APPLICATION_ID
    }
}
