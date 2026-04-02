package de.binarynoise.captiveportalautologin.util

import android.os.Build
import org.lsposed.hiddenapibypass.HiddenApiBypass


fun invokeSystemApiFunction(
    clazz: Class<*>,
    thiz: Any?,
    methodName: String,
    vararg args: Any?,
): Any? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        HiddenApiBypass.invoke(clazz, thiz, methodName, *args)
    } else {
        TODO()
    }
}
