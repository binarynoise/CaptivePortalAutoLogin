package de.binarynoise.captiveportalautologin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.binarynoise.logger.Logger.log

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            log("BootCompletedReceiver received unexpected intent: $intent")
            return
        }
        log("onReceive $intent")
        ConnectivityChangeListenerService.start(true)
    }
}
