package de.binarynoise.captiveportalautologin.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage


class SelfHook : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val cls = Class.forName(Xposed::class.qualifiedName!!, false, lpparam.classLoader)
        val method = cls.getDeclaredMethod(Xposed::getEnabled.name)
        
        XposedBridge.hookMethod(method, object : XC_MethodReplacement() {
            override fun replaceHookedMethod(param: MethodHookParam?): Boolean {
                return true
            }
        })
    }
}
