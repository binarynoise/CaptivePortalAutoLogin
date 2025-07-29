package de.binarynoise.captiveportalautologin

import java.util.concurrent.*
import android.os.Build
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import android.widget.Toast
import de.binarynoise.logger.Logger.log

class DebugApplication : Application() {
    private val penaltyExecutor: Executor = Executors.newSingleThreadScheduledExecutor()
    
    override fun onCreate() {
        super.onCreate()
        StrictMode.setVmPolicy(
            VmPolicy.Builder().apply {
                detectAll()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    penaltyListener(penaltyExecutor) { v ->
                        log("StrictMode policy violation", v)
                        Toast.makeText(applicationContext, v.message ?: "StrictMode policy violation", Toast.LENGTH_SHORT).show()
                    }
                }
            }.build()
        )
    }
}
