package de.binarynoise.captiveportalautologin.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class DoNotAutoOpenCaptivePortalHook : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val StandardWifiEntryClass = XposedHelpers.findClass("com.android.wifitrackerlib.StandardWifiEntry", lpparam.classLoader)
        val ConnectCallbackClass = XposedHelpers.findClass("com.android.wifitrackerlib.WifiEntry\$ConnectCallback", lpparam.classLoader)
        XposedHelpers.findAndHookMethod(StandardWifiEntryClass, "connect", ConnectCallbackClass, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                XposedHelpers.setBooleanField(param.thisObject, "mShouldAutoOpenCaptivePortal", false)
            }
        })
    }
}
