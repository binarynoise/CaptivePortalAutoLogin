package de.binarynoise.captiveportalautologin

import kotlin.concurrent.thread
import kotlin.jvm.optionals.getOrNull
import de.binarynoise.liberator.Liberator

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
}

fun onConnectivityChanged(connectivity: String) {
    log("onConnectivityChanged: $connectivity")
    if (connectivity == "portal") {
        Liberator({}).liberate()
    }
}
