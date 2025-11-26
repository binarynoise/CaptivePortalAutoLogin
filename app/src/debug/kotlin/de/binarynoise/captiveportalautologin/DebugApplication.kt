package de.binarynoise.captiveportalautologin

import java.util.concurrent.*
import android.os.Build
import android.os.StrictMode
import android.os.StrictMode.VmPolicy
import android.os.strictmode.DiskReadViolation
import android.os.strictmode.UntaggedSocketViolation
import android.widget.Toast
import de.binarynoise.liberator.PortalLiberatorConfig
import de.binarynoise.logger.Logger.log

class DebugApplication : Application() {
    private val penaltyExecutor: Executor = Executors.newSingleThreadScheduledExecutor()
    
    override fun onCreate() {
        super.onCreate()
        
        /*
        StrictMode.setVmPolicy(
            VmPolicy.Builder().apply {
                detectAll()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    penaltyListener(penaltyExecutor) { v ->
                        if (v is UntaggedSocketViolation) return@penaltyListener
                        
                        log("StrictMode VmPolicy violation", v)
                        val errorMessage = v.message ?: "StrictMode policy violation"
                        Toast.makeText(applicationContext, errorMessage, Toast.LENGTH_SHORT).show()
                    }
                }
            }.build()
        )
        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().apply {
            detectAll()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                penaltyListener(penaltyExecutor) { v ->
                    if (v is DiskReadViolation) {
                        val allowed = v.stackTrace.any { stackTraceElement ->
                            arrayOf(
                                "androidx.preference.Preference",
                                "androidx.preference.PreferenceManager",
                            ).any { className ->
                                className == stackTraceElement.className
                            }
                        }
                        if (allowed) {
                            return@penaltyListener
                        }
                    }
                    
                    log("StrictMode ThreadPolicy violation", v)
                    val errorMessage = v.message ?: "StrictMode policy violation"
                    Toast.makeText(applicationContext, errorMessage, Toast.LENGTH_SHORT).show()
                }
            }
        }.build())
        */
        PortalLiberatorConfig.experimental = true
    }
}
