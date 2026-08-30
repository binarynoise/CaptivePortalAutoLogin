package de.binarynoise.captiveportalautologin.util

import java.lang.reflect.Field
import java.lang.reflect.Modifier
import android.os.Build
import org.lsposed.hiddenapibypass.HiddenApiBypass

fun Any.invokeHiddenMethod(name: String, vararg args: Any?): Any {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        HiddenApiBypass.invoke(this::class.java, this, name, *args)
    } else {
        this::class.java.getDeclaredMethod(name).invoke(this, *args)
    }
}

fun Class<*>.getHiddenStaticField(fieldName: String): Field {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        HiddenApiBypass.getStaticFields(this)
    } else {
        (this.fields + this.declaredFields).toSet().filter { Modifier.isStatic(it.modifiers) }
    }.single { it.name == fieldName }
}

fun Class<*>.getHiddenStaticFieldValue(fieldName: String): Any? = this.getHiddenStaticField(fieldName).get(null)
fun Any.getHiddenStaticField(fieldName: String): Field = this::class.java.getHiddenStaticField(fieldName)
fun Any.getHiddenStaticFieldValue(fieldName: String): Any? = this::class.java.getHiddenStaticFieldValue(fieldName)

fun Any.getHiddenInstanceField(name: String): Field {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        HiddenApiBypass.getInstanceFields(this::class.java)
    } else {
        (this::class.java.fields + this::class.java.declaredFields).filterNot { Modifier.isStatic(it.modifiers) }
    }.single { it.name == name }
}
