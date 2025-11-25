package de.binarynoise.captiveportalautologin.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class SelfHook : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val cls = Class.forName(Xposed::class.qualifiedName!!, false, lpparam.classLoader)
        XposedHelpers.findAndHookMethod(
            cls,
            Xposed::getEnabled.name,
            XC_MethodReplacement.returnConstant(true),
        )
    }
}
