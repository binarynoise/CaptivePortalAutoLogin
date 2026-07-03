package de.binarynoise.captiveportalautologin

import android.os.Build
import androidx.work.Configuration
import androidx.work.WorkManager
import de.binarynoise.captiveportalautologin.preferences.SharedPreferences
import de.binarynoise.logger.Logger

open class Application : android.app.Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        Logger.Config.apply {
            toSOut = true
            toFile = true
            folder = filesDir.resolve("logs").apply { mkdir() }
        }
        
        setupUncaughtExceptionHandler()
        
        // Ensure WorkManager is initialized before queuing any Jobs
        WorkManager.initialize(this, Configuration.Builder().build())
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && SharedPreferences.network_suggestions.get()) {
            enqueueUpdateNetworkSuggestionSSIDsWork(this)
        }
    }
    
    /**
     * Register a handler to log uncaught exceptions that crash the app.
     */
    private fun setupUncaughtExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Logger.log("Uncaught exception in thread: ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
