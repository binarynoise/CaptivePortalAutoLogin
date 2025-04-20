package de.binarynoise.captiveportalautologin.xposed

import de.binarynoise.captiveportalautologin.BuildConfig
import de.binarynoise.liberator.cast
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import de.robv.android.xposed.XC_MethodHook as MethodHook


class LocationIndicatorHook : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedHelpers.findAndHookMethod(
            "com.android.systemui.appops.AppOpsControllerImpl", lpparam.classLoader, "getActiveAppOps", Boolean::class.java,
            object : MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    param.result = param.result.cast<MutableList<*>>().filter { appOpItem ->
                        !XposedHelpers.getObjectField(appOpItem, "mPackageName").equals(BuildConfig.APPLICATION_ID)
                    }
                }
            },
        )
    }
}
