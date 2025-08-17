package de.binarynoise.captiveportalautologin

import kotlin.concurrent.thread
import kotlin.jvm.optionals.getOrNull
import de.binarynoise.liberator.Liberator
import de.binarynoise.liberator.PortalDetection
import de.binarynoise.logger.Logger.log

/**
 * Builds a [ProcessBuilder] with `LC_ALL=C` set in the environment.
 * This is necessary to produce consistent output across different languages.
 */
val processBuilder: ProcessBuilder
    get() = ProcessBuilder().apply { environment()["LC_ALL"] = "C" }

fun main() {
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
        process.inputReader().useLines { lines ->
            lines.forEach { line ->
                log(line)
                val regex = "^Connectivity is now '(\\w+)'$".toRegex()
                val result = regex.matchEntire(line)
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
            val result = Liberator({}, PortalDetection.defaultBackend, PortalDetection.defaultUserAgent).liberate()
            
            when (result) {
                is Liberator.LiberationResult.Success -> log("broke out of the portal")
                is Liberator.LiberationResult.Error -> log("Failed to liberate: ${result.message}", result.exception)
                Liberator.LiberationResult.NotCaught -> log("not caught in portal")
                is Liberator.LiberationResult.StillCaptured -> log("Failed to liberate: still in portal: ${result.url}")
                is Liberator.LiberationResult.Timeout -> log("Failed to liberate: timeout")
                is Liberator.LiberationResult.UnknownPortal -> log("Failed to liberate: unknown portal: ${result.url}")
                is Liberator.LiberationResult.UnsupportedPortal -> log("Failed to liberate: Portal will not be supported: ${result.url}")
            }
        }
    } catch (e: Exception) {
        log("failed to liberate", e)
    }
}
