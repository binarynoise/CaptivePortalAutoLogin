package de.binarynoise.captiveportalautologin

import kotlin.concurrent.thread
import kotlin.jvm.optionals.getOrNull
import de.binarynoise.liberator.Liberator
import de.binarynoise.liberator.tryOrIgnore
import de.binarynoise.logger.Logger.log

fun main() {
    
    val processBuilder = ProcessBuilder().apply { environment()["LC_ALL"] = "C" }
    
    thread {
        val process = processBuilder.command("nmcli", "monitor").start()
        process.inputReader().useLines {
            it.forEach {
                log(it)
                val regex = "^Connectivity is now '(\\w+)'$".toRegex()
                val result = regex.matchEntire(it)
                val connectivity = result?.groups?.get(1)?.value
                if (connectivity != null) {
                    onConnectivityChanged(connectivity)
                }
            }
            
        }
        process.errorReader().useLines { it.forEach { log(it) } }
        log("process ${process.info().command().getOrNull()} finished")
    }
    
    thread {
        val process = processBuilder.command("nmcli", "networking", "connectivity").start()
        process.inputReader().useLines { it.forEach { log(it); onConnectivityChanged(it) } }
        process.errorReader().useLines { it.forEach { log(it) } }
        log("process ${process.info().command().getOrNull()} finished")
    }
    
    thread {
        while (true) {
            val char = System.`in`.read()
            if (char == -1) break
            
            when (char.toChar()) {
                'r' -> {
                    processBuilder.command("nmcli", "networking", "off").start().waitFor()
                    processBuilder.command("nmcli", "networking", "on").start().waitFor()
                    log("restarted networking")
                }
            }
        }
    }
}

fun onConnectivityChanged(connectivity: String) {
    try {
        log("onConnectivityChanged: $connectivity")
        if (connectivity == "portal") {
            val (newLocation, tried) = Liberator({}).liberate()
            
            if (newLocation == null) {
                if (tried) {
                    log("broke out of the portal")
                } else {
                    log("not caught in portal")
                }
            } else {
                log("Failed to liberate: still in portal: $newLocation")
            }
        }
    } catch (e: Exception) {
        log("failed to liberate", e)
    }
}
