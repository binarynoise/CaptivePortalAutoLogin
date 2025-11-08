package de.binarynoise.captiveportalautologin

import java.util.concurrent.TimeUnit.*
import kotlin.concurrent.thread
import kotlin.jvm.optionals.getOrNull
import kotlin.system.exitProcess
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import de.binarynoise.liberator.Liberator
import de.binarynoise.liberator.PortalDetection
import de.binarynoise.liberator.PortalLiberatorConfig
import de.binarynoise.logger.Logger.log
import okhttp3.ConnectionPool

fun main(args: Array<String>) = CaptivePortalAutoLoginLinux().main(args)

class CaptivePortalAutoLoginLinux : CliktCommand() {
    val service by option().flag("--oneshot", defaultForHelp = "oneshot").help { "Run as a service or only once" }
    val force by option().flag()
        .help { "Force liberation without connectivity check by NetworkManager (implies --oneshot)" }
    val experimental by option().flag().help { "enable experimental and incomplete Portals" }
    val restartNetworking by option().flag()
        .help { "Restart networking on start. Also available as keyboard shortcut 'r' while running as service" }
    
    override fun run() {
        log("CaptivePortalAutoLogin for Linux")
        
        if (experimental) {
            PortalLiberatorConfig.experimental = true
        }
        
        if (restartNetworking) {
            restartNetworking()
            Thread.sleep(1000)
        }
        
        if (force) {
            onConnectivityChanged("portal", oneshot = true)
            // TODO: find out why this doesn't allow the program to exit
            return
        }
        
        thread(block = ::startupCheck)
        
        if (service) {
            thread(block = ::backgroundService)
            thread(block = ::keyboardInput)
        }
    }
    
    
    /**
     * Builds a [ProcessBuilder] with `LC_ALL=C` set in the environment.
     * This is necessary to produce consistent `nmcli` output across different languages.
     */
    val processBuilder: ProcessBuilder
        get() = ProcessBuilder().apply { environment()["LC_ALL"] = "C" }
    
    
    /**
     * Monitors the connectivity state using the `nmcli monitor` command.
     *
     * Reads the output of the `nmcli monitor` command and parses it to determine the current connectivity state.
     * When the connectivity state changes, the [onConnectivityChanged] function is called with the new state.
     *
     * The output of the `nmcli monitor` command and any errors that occur are logged.
     */
    private fun backgroundService() {
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
     * Retrieves the current connectivity state using the `nmcli networking connectivity` command.
     *
     * Runs the `nmcli networking connectivity` command and parses the output
     * to determine the current connectivity state.
     * The [onConnectivityChanged] function is called with the new state.
     *
     * The thread logs the output of the `nmcli networking connectivity` command and any errors that occur.
     */
    private fun startupCheck() {
        val process = processBuilder.command("nmcli", "networking", "connectivity").start()
        process.inputReader().useLines { line -> line.forEach { log(it); onConnectivityChanged(it, oneshot = true) } }
        process.errorReader().useLines { it.forEach { line -> log(line) } }
        log("process ${process.info().command().getOrNull()} finished")
    }
    
    
    /**
     * Reads the input from the console and executes actions based on the input.
     *
     * - `r`: restart networking
     * - `q`: quit
     */
    private fun keyboardInput() {
        while (true) {
            val char = System.`in`.read()
            if (char == -1) break
            
            when (char.toChar()) {
                'r' -> {
                    restartNetworking()
                }
                'q' -> {
                    exitProcess(0)
                }
            }
        }
    }
    
    
    /**
     * Restarts the networking by turning it off and on again.
     *
     * This function runs the `nmcli networking off` and `nmcli networking on` commands to restart the networking.
     */
    private fun restartNetworking() {
        processBuilder.command("nmcli", "networking", "off").start().waitFor()
        processBuilder.command("nmcli", "networking", "on").start().waitFor()
        log("restarted networking")
    }
    
    
    /**
     * Handles the event of connectivity state change.
     *
     * Parses the connectivity state and attempts to liberate the user if the state is "portal".
     *
     * @param connectivity The new connectivity state.
     */
    fun onConnectivityChanged(connectivity: String, oneshot: Boolean = false) {
        try {
            log("onConnectivityChanged: $connectivity")
            if (connectivity == "portal") {
                val result = Liberator({
                    if (oneshot) it.connectionPool(ConnectionPool(0, 1, SECONDS))
                }, PortalDetection.defaultBackend, PortalDetection.defaultUserAgent).liberate()
                
                when (result) {
                    is Liberator.LiberationResult.Success -> log("broke out of the portal")
                    is Liberator.LiberationResult.Error -> log(
                        "Failed to liberate: ${result.message}", result.exception
                    )
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
}
