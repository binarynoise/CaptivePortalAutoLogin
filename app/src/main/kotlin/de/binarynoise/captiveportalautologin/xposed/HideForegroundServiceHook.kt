package de.binarynoise.captiveportalautologin.xposed

import android.os.Build
import de.binarynoise.captiveportalautologin.BuildConfig
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class HideForegroundServiceHook : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
            return
        val UIControl = XposedHelpers.findClass(
            "com.android.systemui.qs.FgsManagerControllerImpl\$UIControl",
            lpparam.classLoader,
        )
        val UIControlHideEntry = XposedHelpers.getStaticObjectField(UIControl, "HIDE_ENTRY")
        XposedHelpers.findAndHookMethod(
            "com.android.systemui.qs.FgsManagerControllerImpl\$UserPackage",
            lpparam.classLoader,
            "updateUiControl",
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    val packageName = XposedHelpers.getObjectField(param.thisObject, "packageName") as String
                    if (packageName != BuildConfig.APPLICATION_ID)
                        return
                    XposedHelpers.setObjectField(param.thisObject, "uiControl", UIControlHideEntry)
                }
            },
        )
    }
}
