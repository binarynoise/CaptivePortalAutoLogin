package de.binarynoise.captiveportalautologin.util

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper

val mainHandler = Handler(Looper.getMainLooper())

fun BackgroundHandler(name: String) = Handler(HandlerThread(name).apply { start() }.looper)
