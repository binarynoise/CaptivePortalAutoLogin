package de.binarynoise.captiveportalautologin.integration

import java.nio.file.Path
import kotlin.io.path.name
import kotlin.time.Duration.Companion.seconds
import de.binarynoise.logger.Logger.log

/**
 * The global setting the tests point the app at their api server with. Has to match the key in
 * `SharedPreferences`, only adb shell can write it.
 */
const val testApiBaseUrlKey = "captive_portal_auto_login_test_api_base_url"

class Adb(private val environment: TestEnvironment, val serial: String) {
    fun shell(command: String): String {
        val result = shellResult(command)
        if (result.exitCode != 0) error("$serial: adb shell failed: $command -> ${result.exitCode}\n${result.standardOutput}")
        return result.standardOutput.trim()
    }
    
    fun shellOrNull(command: String): String? {
        val result = shellResult(command)
        if (result.exitCode != 0) return null
        return result.standardOutput.trim()
    }
    
    private fun shellResult(command: String): CommandResult = adb("shell", command)
    
    fun execOut(command: String, outputFile: Path): Path {
        val result = adb("exec-out", command)
        if (result.exitCode != 0) error("$serial: adb exec-out failed: $command\n${result.standardOutput}")
        return writeFile(outputFile, result.standardOutput)
    }
    
    fun install(apk: Path) {
        val result = adb("install", "-r", "-t", apk.toAbsolutePath().toString())
        val failure = when {
            result.exitCode != 0 -> result.standardOutput.trim()
            result.standardOutput.contains("Success") -> null
            else -> result.standardOutput.trim()
        }
        failure?.let { error("$serial: installing ${apk.name} failed: $it") }
    }
    
    fun uninstall(packageName: String) {
        adb("uninstall", packageName)
    }
    
    fun reverse(devicePort: Int, hostPort: Int) {
        adb(
            "reverse", "tcp:$devicePort", "tcp:$hostPort"
        ).requireSuccess("forwarding device port $devicePort to host port $hostPort")
    }
    
    fun property(name: String): String? = shellOrNull("getprop $name")?.takeIf { it.isNotBlank() }
    
    fun setGlobalSetting(key: String, value: String, secure: Boolean = false) {
        val namespace = if (secure) "secure" else "global"
        shell("settings put $namespace $key ${value.shellQuoted()}")
    }
    
    fun getGlobalSetting(key: String): String? = shellOrNull("settings get global $key")
    
    fun startHomeActivity() {
        startActivity(TestEnvironment.appHomeActivity)
    }
    
    /**
     * Points the app at the api server of this run through a global setting, which only adb shell can
     * write, so release builds stay testable without an intent hook that any app could call.
     */
    fun setTestApiBaseUrl(apiBaseUrl: String) {
        setGlobalSetting(testApiBaseUrlKey, apiBaseUrl)
    }
    
    fun clearTestApiBaseUrl() {
        adb("shell", "settings delete global $testApiBaseUrlKey").requireSuccess("clearing the test api server url")
    }
    
    fun emuKill() {
        adb("emu", "kill").requireSuccess("killing the emulator $serial")
    }
    
    /**
     * Restarts adbd as root. `svc wifi` silently does nothing on some images unless adbd runs as
     * root, so the test run tries this after every boot. Returns whether the device runs as root
     * now, production builds refuse it and the run continues without root then.
     */
    fun root(): Boolean {
        val result = adb("root")
        val output = result.standardOutput.trim()
        val refused = result.exitCode != 0 || //
            output.contains("cannot", ignoreCase = true) || //
            output.contains("unable", ignoreCase = true)
        if (refused) {
            log("$serial: adb root unavailable, continuing without root: ${output.ifBlank { "exit code ${result.exitCode}" }}")
            return false
        }
        log("$serial: ${output.ifBlank { "restarting adbd as root" }}")
        return true
    }
    
    fun startActivity(activity: String) {
        adb("shell", "am", "start", "-W", "-n", activity).requireSuccess("starting $activity")
    }
    
    /**
     * The app logs a lot while it walks through the portal, and the default buffer drops the beginning of a
     * test run long before it is over, which makes log-based checks flaky.
     *
     * Best effort only: older images reject large sizes, and the liberation itself does not depend on it.
     */
    fun enlargeLogcatBuffer() {
        for (size in listOf("16M", "4M", "1M")) {
            val result = adb("logcat", "-G", size)
            if (result.exitCode == 0) {
                if (size != "16M") log("$serial: using logcat buffer size $size")
                return
            }
            log("$serial: setting the logcat buffer size to $size failed: ${result.standardOutput.trim()}")
        }
        log("$serial: warning: leaving the logcat buffer at its default size")
    }
    
    /**
     * Clearing is hygiene for the logcat dump written after the test, not part of the liberation itself:
     * after `adb root` some images answer `logcat -c` with `failed to clear the 'main' log`,
     * so a clear that keeps failing only logs a warning instead of failing the test.
     */
    fun clearLogcat() {
        try {
            retry(15.seconds, interval = 1.seconds, description = "logcat to become clearable") {
                adb("logcat", "-c").exitCode == 0 || adb("logcat", "-b", "all", "-c").exitCode == 0
            }
        } catch (failure: IllegalStateException) {
            val result = adb("logcat", "-c")
            log(
                "$serial: warning: clearing logcat failed, continuing with uncleared logs: ${result.standardOutput.trim()}",
            )
        }
    }
    
    fun wakeUpAndDismissKeyguard() {
        shellOrNull("input keyevent KEYCODE_WAKEUP")
        shellOrNull("wm dismiss-keyguard")
    }
    
    fun logcatDump(): String = adb("logcat", "-d", "-v", "threadtime").standardOutput
    
    fun crashLogDump(): String = adb("logcat", "-b", "crash", "-d", "-v", "threadtime").standardOutput
    
    private fun adb(vararg arguments: String): CommandResult = runCommand(
        listOf(environment.adbBinary.toAbsolutePath().toString(), "-s", serial) + arguments,
        workingDirectory = environment.projectDirectory,
    )
}

/**
 * Quotes a value for the device shell, so that settings containing spaces or
 * special characters survive `adb shell` the way the caller spelled them.
 */
private fun String.shellQuoted(): String = "'${replace("'", "'\\''")}'"
