package de.binarynoise.logger

import android.annotation.SuppressLint
import android.app.Application

@SuppressLint("PrivateApi")
internal var applicationContext: Application = Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as Application