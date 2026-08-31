package de.binarynoise.captiveportalautologin.util

import java.security.PublicKey
import java.util.Locale
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
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

fun Context.getSignaturePublicKey(): PublicKey? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
    this.packageManager.getPackageInfo(
        this.packageName, PackageManager.GET_SIGNING_CERTIFICATES
    ).signingInfo?.publicKeys?.singleOrNull()
} else {
    this.packageManager.getPackageInfo(
        this.packageName, PackageManager.GET_SIGNATURES
    ).signatures!![0].invokeHiddenMethod("getPublicKey") as PublicKey?
}

val Context.englishResources: Resources
    get() = createConfigurationContext(Configuration(resources.configuration).apply { setLocale(Locale.ENGLISH) }).resources
