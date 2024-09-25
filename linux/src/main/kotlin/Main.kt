package de.binarynoise.captiveportalautologin

import kotlin.concurrent.thread
import kotlin.jvm.optionals.getOrNull
import de.binarynoise.liberator.Liberator
import de.binarynoise.liberator.PortalDetection
import de.binarynoise.logger.Logger.log

fun main() {

    /**
     * Builds a [ProcessBuilder] with `LC_ALL=C` set in the environment.
     * This is necessary to produce consistent output across different languages.
     */
    val processBuilder = ProcessBuilder().apply { environment()["LC_ALL"] = "C" }
    
    /**
     * Thread that monitors the connectivity state using the `nmcli monitor` command.
     *
     * This thread reads the output of the `nmcli monitor` command and parses it to determine the current
     * connectivity state. When the connectivity state changes, the [onConnectivityChanged] function is called
     * with the new state.
     *
     * The thread logs the output of the `nmcli monitor` command and any errors that occur.
     *
     * @see onConnectivityChanged
     */
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
    
    /**
     * Thread that retrieves the current connectivity state using the `nmcli networking connectivity` command.
     *
     * This thread runs the `nmcli networking connectivity` command and parses the output to determine the
     * current connectivity state. The [onConnectivityChanged] function is called with the new state.
     *
     * The thread logs the output of the `nmcli networking connectivity` command and any errors that occur.
     *
     * @see onConnectivityChanged
     */
    thread {
        val process = processBuilder.command("nmcli", "networking", "connectivity").start()
        process.inputReader().useLines { it.forEach { log(it); onConnectivityChanged(it) } }
        process.errorReader().useLines { it.forEach { log(it) } }
        log("process ${process.info().command().getOrNull()} finished")
    }
    
    /**
     * Thread that reads input from the console and executes actions based on the input.
     *
     * - `r`: restart networking
     */
    thread {
        while (true) {
            val char = System.`in`.read()
            if (char == -1) break
            
            when (char.toChar()) {
                'r' -> {
                    // restart networking
                    processBuilder.command("nmcli", "networking", "off").start().waitFor()
                    processBuilder.command("nmcli", "networking", "on").start().waitFor()
                    log("restarted networking")
                }
            }
        }
    }
}

/**
 * Handles the event of connectivity state change.
 *
 * Parses the connectivity state and attempts to liberate the user if the state is "portal".
 *
 * @param connectivity the connectivity state as a string
 * @see Liberator.liberate
 */
fun onConnectivityChanged(connectivity: String) {
    try {
        log("onConnectivityChanged: $connectivity")
        if (connectivity == "portal") {
            val (newLocation, tried) = Liberator(PortalDetection.defaultBackend, PortalDetection.defaultUserAgent) {}.liberate()
            
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
