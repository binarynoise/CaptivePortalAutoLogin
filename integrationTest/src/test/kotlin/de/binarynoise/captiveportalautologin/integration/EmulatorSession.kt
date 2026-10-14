package de.binarynoise.captiveportalautologin.integration

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import de.binarynoise.logger.Logger.log

/**
 * Boots an emulator, runs a test against it and shuts it down again.
 *
 * Only one emulator runs at a time, they are used one after another.
 */
class EmulatorSession(
    private val environment: TestEnvironment,
    val avd: Avd,
    val logDirectory: Path,
) : AutoCloseable {
    
    private var emulatorProcess: Process? = null
    
    lateinit var adb: Adb
        private set
    
    init {
        logDirectory.createDirectories()
    }
    
    fun boot() {
        val consolePort = findFreeConsolePort()
        val serial = "emulator-$consolePort"
        adb = Adb(environment, serial)
        
        val command = listOf(
            environment.emulatorBinary.toAbsolutePath().toString(),
            "-avd", avd.name,
            "-port", consolePort.toString(),
            "-no-window",
            "-no-audio",
            "-no-boot-anim",
            "-no-snapshot",
            "-no-metrics",
            "-gpu", "swiftshader_indirect",
            "-read-only",
        )
        emulatorProcess = ProcessBuilder(command).directory(environment.projectDirectory.toFile())
            .redirectErrorStream(true)
            .redirectOutput(logDirectory.resolve("${avd.name}-emulator.log").toFile())
            .start()
        
        waitUntilBoot(serial)
        prepareDevice()
    }
    
    private fun waitUntilBoot(serial: String) {
        retry(bootTimeout, interval = 2.seconds, description = "the emulator $serial to show up") {
            val states = deviceStates()
            check(states[serial] !in listOf("offline", "unauthorized")) {
                "the emulator $serial is ${states[serial]}"
            }
            states[serial] == "device"
        }
        retry(bootTimeout, interval = 2.seconds, description = "$serial to finish booting") {
            adb.property("sys.boot_completed") == "1"
        }
    }
    
    /**
     * Prepares the device so that the ui can be driven reliably.
     *
     * Some images boot with a lockscreen, and the screen also locks again while a long onboarding runs, which
     * leaves the test tapping a keyguard. Disabling the lock screen and keeping the screen awake avoids both.
     */
    private fun prepareDevice() {
        listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale").forEach { setting ->
            adb.setGlobalSetting(setting, "0")
        }
        adb.shellOrNull("locksettings set-disabled true")
        adb.shellOrNull("svc power stayon true")
        adb.wakeUpAndDismissKeyguard()
        retry(30.seconds, interval = 2.seconds, description = "the package manager to become available") {
            adb.shellOrNull("pm path android")?.contains("package:") == true
        }
    }
    
    override fun close() {
        val process = emulatorProcess ?: return
        emulatorProcess = null
        if (::adb.isInitialized) runCatching { adb.shell("emu kill") }
        if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly()
            if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
                log("warning: the emulator ${avd.name} did not exit after destroyForcibly")
            }
        }
    }
    
    private fun deviceStates(): Map<String, String> = runCommand(
        listOf(environment.adbBinary.toAbsolutePath().toString(), "devices"),
        workingDirectory = environment.projectDirectory,
    ).standardOutput.lines()
        .asSequence()
        .drop(1)
        .map { it.trim() }
        .filter { it.isNotEmpty() && '\t' in it }
        .associate { it.substringBefore('\t').trim() to it.substringAfter('\t').trim() }
    
    /**
     * The emulator uses the console port and the adb port right above it, both have to be free.
     */
    private fun findFreeConsolePort(): Int {
        for (consolePort in 5554..5580 step 2) {
            if (isPortFree(consolePort) && isPortFree(consolePort + 1)) return consolePort
        }
        error("no free emulator console port found")
    }
    
    companion object {
        private val bootTimeout = 10.minutes
    }
}
