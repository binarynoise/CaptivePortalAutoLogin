package de.binarynoise.captiveportalautologin.integration

import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.createDirectories
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
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
    private var shutdownHook: Thread? = null
    private val closed = AtomicBoolean(false)
    
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
            .apply { environment()["ANDROID_EMULATOR_WAIT_TIME_BEFORE_KILL"] = "0" }
            .start()
        val hook = Thread(::shutdownFromHook)
        shutdownHook = hook
        runCatching { Runtime.getRuntime().addShutdownHook(hook) }
        
        waitUntilBoot(serial)
        prepareDevice()
    }
    
    private fun waitUntilBoot(serial: String) {
        retry(bootTimeout, interval = 500.milliseconds, description = "the emulator $serial to show up") {
            val states = deviceStates()
            check(states[serial] !in listOf("offline", "unauthorized")) {
                "the emulator $serial is ${states[serial]}"
            }
            states[serial] == "device"
        }
        retry(bootTimeout, interval = 500.milliseconds, description = "$serial to finish booting") {
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
        retry(30.seconds, interval = 500.milliseconds, description = "the package manager to become available") {
            adb.shellOrNull("pm path android")?.contains("package:") == true
        }
    }
    
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            shutdownEmulator()
        } finally {
            val hook = shutdownHook
            shutdownHook = null
            if (hook != null) runCatching { Runtime.getRuntime().removeShutdownHook(hook) }
        }
    }

    private fun shutdownFromHook() {
        if (!closed.compareAndSet(false, true)) return
        shutdownEmulator()
    }

    private fun shutdownEmulator() {
        val process = emulatorProcess ?: return
        emulatorProcess = null
        if (::adb.isInitialized) runCatching { adb.emuKill() }
        if (process.isAlive) runCatching { process.destroy() }
        if (!waitForExit(process, 30.seconds)) {
            runCatching { process.destroyForcibly() }
            if (!waitForExit(process, 30.seconds)) {
                log("warning: the emulator ${avd.name} did not exit after destroyForcibly")
            }
        }
    }

    private fun waitForExit(process: Process, timeout: Duration): Boolean {
        val deadline = System.nanoTime() + timeout.inWholeNanoseconds
        var interrupted = false
        try {
            while (true) {
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0L) return !process.isAlive
                try {
                    return process.waitFor(remaining, TimeUnit.NANOSECONDS)
                } catch (_: InterruptedException) {
                    interrupted = true
                }
                if (!process.isAlive) return true
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt()
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
