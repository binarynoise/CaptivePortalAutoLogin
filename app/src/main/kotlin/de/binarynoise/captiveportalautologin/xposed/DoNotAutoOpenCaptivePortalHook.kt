package de.binarynoise.captiveportalautologin.xposed

import android.os.Build
import de.binarynoise.logger.Logger.log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class DoNotAutoOpenCaptivePortalHook : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        if (!listOf("com.android.systemui", "com.android.settings").contains(lpparam.packageName)) return
        if (lpparam.packageName == "com.android.systemui" && Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        applyLoggerConfig(lpparam)
        log("${this::class.simpleName} handleLoadPackage ${lpparam.packageName} with process ${lpparam.processName} and pid ${android.os.Process.myPid()}")
        
        val StandardWifiEntryClass =
            XposedHelpers.findClass("com.android.wifitrackerlib.StandardWifiEntry", lpparam.classLoader)
        val connectMethod = StandardWifiEntryClass.declaredMethods.find { it.name == "connect" }
        
        XposedBridge.hookMethod(
            connectMethod,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    XposedHelpers.setBooleanField(param.thisObject, "mShouldAutoOpenCaptivePortal", false)
                }
            },
        )
    }
}
