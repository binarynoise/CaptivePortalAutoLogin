package de.binarynoise.reflection

import java.lang.reflect.Field
import java.lang.reflect.Modifier
import android.os.Build
import org.lsposed.hiddenapibypass.HiddenApiBypass

var setHiddenApiExemptionsCalled = false
private fun setHiddenApiExemptions() {
    if (setHiddenApiExemptionsCalled) return
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
    HiddenApiBypass.setHiddenApiExemptions("")
}

fun Any.invokeHiddenMethod(name: String, vararg args: Any?, cls: Class<*> = this::class.java): Any? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        HiddenApiBypass.invoke(cls, this, name, *args)
    } else {
        cls.getDeclaredMethod(name).invoke(this, *args)
    }
}

fun Class<*>.getHiddenStaticField(fieldName: String): Field {
    setHiddenApiExemptions()
    return (this.fields + this.declaredFields).toSet()
        .filter { Modifier.isStatic(it.modifiers) }
        .single { it.name == fieldName }
}

fun Class<*>.getHiddenStaticFieldValue(fieldName: String): Any? = this.getHiddenStaticField(fieldName).get(null)
fun Any.getHiddenStaticField(fieldName: String): Field = this::class.java.getHiddenStaticField(fieldName)
fun Any.getHiddenStaticFieldValue(fieldName: String): Any? = this::class.java.getHiddenStaticFieldValue(fieldName)

fun Any.getHiddenInstanceField(fieldName: String): Field {
    setHiddenApiExemptions()
    return (this::class.java.fields + this::class.java.declaredFields).toSet()
        .filterNot { Modifier.isStatic(it.modifiers) }
        .single { it.name == fieldName }
}
