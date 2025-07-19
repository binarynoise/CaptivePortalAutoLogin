package de.binarynoise.captiveportalautologin.xposed

import android.net.Network
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import de.robv.android.xposed.XC_MethodHook as MethodHook

class NetworkConnectedHook : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val NetworkStackNotifierClass = XposedHelpers.findClass("com.android.networkstack.NetworkStackNotifier", lpparam.classLoader)
        XposedHelpers.findAndHookMethod(NetworkStackNotifierClass, "isVenueInfoNotificationEnabled", XC_MethodReplacement.returnConstant(false))
        XposedHelpers.findAndHookMethod(NetworkStackNotifierClass, "updateNotifications", Network::class.java, object: MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val network = param.args[0] as Network
                val mNetworkStatus = XposedHelpers.getObjectField(param.thisObject, "mNetworkStatus")
                val trackedNetworkStatus = XposedHelpers.callMethod(mNetworkStatus, "get", network)
                if (trackedNetworkStatus == null)
                    return
                XposedHelpers.setBooleanField(trackedNetworkStatus, "mValidatedNotificationPending", false)
            }
        })
    }
}
