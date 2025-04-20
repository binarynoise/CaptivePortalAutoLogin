package de.binarynoise.captiveportalautologin.util

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

val mainHandler = Handler(Looper.getMainLooper())

context(lifecycleOwner: LifecycleOwner) //
inline fun Handler.postIfCreated(crossinline r: () -> Unit) {
    post {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            r()
        }
    }
}

fun BackgroundHandler(name: String) = Handler(HandlerThread(name).apply { start() }.looper)
