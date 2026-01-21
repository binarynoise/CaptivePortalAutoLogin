@file:Suppress("Deprecation", "LocalVariableName")

package de.binarynoise.captiveportalautologin.xposed

import android.net.wifi.WifiConfiguration
import android.os.Build
import de.binarynoise.liberator.SSID
import de.binarynoise.liberator.portals.allPortalLiberators
import de.binarynoise.logger.Logger.log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class ReEnableSupportedNetworksHook : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (lpparam.packageName != "com.android.networkstack") return
        applyLoggerConfig(lpparam)
        log("${this::class.simpleName} handleLoadPackage ${lpparam.packageName} with process ${lpparam.processName} and pid ${android.os.Process.myPid()}")
        
        val whitelistedSSIDs: List<String> = allPortalLiberators.flatMap { portalLiberator ->
            portalLiberator::class.java.annotations.filterIsInstance<SSID>().flatMap { it.ssid.asIterable() }
        }
        
        val WifiBlocklistMonitor =
            XposedHelpers.findClass("com.android.server.wifi.WifiBlocklistMonitor", lpparam.classLoader)
        
        XposedHelpers.findAndHookMethod(
            WifiBlocklistMonitor,
            "shouldEnableNetwork",
            WifiConfiguration::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val config = param.args[0] as WifiConfiguration
                    if (whitelistedSSIDs.contains(config.SSID)) param.result = true
                }
            },
        )
    }
}
