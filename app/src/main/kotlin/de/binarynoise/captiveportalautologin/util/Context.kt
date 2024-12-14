package de.binarynoise.captiveportalautologin.util

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent

@SuppressLint("PrivateApi")
internal var applicationContext: Application = Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as Application

inline fun <reified T> Context.startActivity() {
    startActivity(Intent(this, T::class.java))
}
