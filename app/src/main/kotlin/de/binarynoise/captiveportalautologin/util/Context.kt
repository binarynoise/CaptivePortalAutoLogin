package de.binarynoise.captiveportalautologin.util

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt

@SuppressLint("PrivateApi")
internal var applicationContext: Application =
    Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as Application

@JvmName("startActivityClass")
inline fun <reified T> Context.startActivity(setup: Intent.() -> Unit = {}) {
    startActivity(Intent(this, T::class.java).apply(setup))
}

inline fun Context.startActivity(setup: Intent.() -> Unit = {}) {
    startActivity(Intent().apply(setup))
}

inline fun <reified T> Context.startService(setup: Intent.() -> Unit = {}) {
    startService(Intent(this, T::class.java).apply(setup))
}

@ColorInt
fun Context.getColorFromAttr(
    @AttrRes attrColor: Int,
    typedValue: TypedValue = TypedValue(),
    resolveRefs: Boolean = true,
): Int {
    check(theme.resolveAttribute(attrColor, typedValue, resolveRefs))
    return typedValue.data
}
