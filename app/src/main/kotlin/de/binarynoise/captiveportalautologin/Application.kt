package de.binarynoise.captiveportalautologin

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
