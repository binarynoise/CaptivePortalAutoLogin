package de.binarynoise.captiveportalautologin.xposed

import android.os.Build
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class DoNotAutoOpenCaptivePortalHook : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == "com.android.systemui" && Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        
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
