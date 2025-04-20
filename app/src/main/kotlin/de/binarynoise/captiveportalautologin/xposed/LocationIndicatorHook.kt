package de.binarynoise.captiveportalautologin.xposed

import de.binarynoise.captiveportalautologin.BuildConfig
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class LocationIndicatorHook : IXposedHookLoadPackage {
    private val PACKAGE_SYSTEMUI = "com.android.systemui"
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != PACKAGE_SYSTEMUI) return
        XposedHelpers.findAndHookMethod(
            "$PACKAGE_SYSTEMUI.appops.AppOpsControllerImpl", lpparam.classLoader, "getActiveAppOps", Boolean::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    param.result = (param.result as List<*>).filter { appOpItem ->
                        !XposedHelpers.getObjectField(appOpItem, "mPackageName").equals(BuildConfig.APPLICATION_ID)
                    }
                }
            },
        )
    }
}
