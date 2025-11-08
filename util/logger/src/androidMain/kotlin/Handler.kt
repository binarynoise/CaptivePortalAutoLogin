package de.binarynoise.logger

import android.os.Handler
import android.os.HandlerThread

fun Any.createBackgroundHandler(): Handler {
    val handlerThread = HandlerThread(this::class.simpleName + "-background")
    handlerThread.isDaemon = true
    handlerThread.start()
    return Handler(handlerThread.looper)
}
