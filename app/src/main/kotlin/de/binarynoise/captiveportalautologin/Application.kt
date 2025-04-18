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
    }
}
