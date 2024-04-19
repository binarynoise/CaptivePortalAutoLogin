package de.binarynoise.captiveportalautologin

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import de.binarynoise.logger.Logger.log

class BootCompletedReceiver : BroadcastReceiver() {
    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    override fun onReceive(context: Context, intent: Intent) {
        log("onReceive $intent")
        ContextCompat.startForegroundService(context, Intent(context, ConnectivityChangeListenerService::class.java))
    }
}
