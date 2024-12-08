package de.binarynoise.captiveportalautologin

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.binarynoise.logger.Logger.log

class BootCompletedReceiver : BroadcastReceiver() {
    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    override fun onReceive(context: Context, intent: Intent) {
        log("onReceive $intent")
        ConnectivityChangeListenerService.start(true)
    }
}
