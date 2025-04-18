package de.binarynoise.captiveportalautologin.util

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

val mainHandler = Handler(Looper.getMainLooper())

context(lifecyleOwner: LifecycleOwner)
fun Handler.postIfCreated(r: Runnable) {
    if (lifecyleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
        post(r)
    }
}

fun BackgroundHandler(name: String) = Handler(HandlerThread(name).apply { start() }.looper)
