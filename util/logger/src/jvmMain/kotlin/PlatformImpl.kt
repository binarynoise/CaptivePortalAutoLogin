package de.binarynoise.logger

import java.util.concurrent.*

@Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
class PlatformImpl : Platform {
    override fun <T> print(t: T) {
        System.out.print(t)
    }
    
    override fun <T> println(t: T) {
        System.out.println(t)
    }
    
    override fun <T> printErr(t: T) {
        System.err.print(t)
    }
    
    override fun <T> printlnErr(t: T) {
        System.err.println(t)
    }
    
    private val backgroundExecutor: ExecutorService = Executors.newSingleThreadExecutor {
        Thread(it, "Logger-Background").apply {
            isDaemon = true
        }
    }
    
    
    override fun runInBackground(block: () -> Unit) {
        backgroundExecutor.execute(block)
    }
}

internal actual val platform: Platform = PlatformImpl()
