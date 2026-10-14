package de.binarynoise.captiveportalautologin.integration

import java.nio.file.Path
import kotlin.io.path.name

/**
 * The global setting the tests point the app at their api server with. Has to match the key in
 * `SharedPreferences`, only adb shell can write it.
 */
const val testApiBaseUrlKey = "captive_portal_auto_login_test_api_base_url"

class Adb(private val environment: TestEnvironment, val serial: String) {
    fun shell(command: String): String {
        val result = shellResult(command)
        if (result.exitCode != 0) error("$serial: adb shell failed: $command\n${result.standardError}")
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
        if (result.exitCode != 0) error("$serial: adb exec-out failed: $command\n${result.standardError}")
        return writeFile(outputFile, result.standardOutput)
    }
    
    fun install(apk: Path) {
        val result = adb("install", "-r", "-t", apk.toAbsolutePath().toString())
        val failure = when {
            result.exitCode != 0 -> result.standardError.ifBlank { result.standardOutput }.trim()
            result.standardOutput.contains("Success") -> null
            else -> result.standardOutput.trim().ifBlank { result.standardError.trim() }
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
    
    fun startActivity(activity: String) {
        adb("shell", "am", "start", "-W", "-n", activity).requireSuccess("starting $activity")
    }
    
    /**
     * The app logs a lot while it walks through the portal, and the default buffer drops the beginning of a
     * test run long before it is over, which makes log-based checks flaky.
     */
    fun enlargeLogcatBuffer() {
        adb("logcat", "-G", "16M").requireSuccess("enlarging the logcat buffer")
    }
    
    fun clearLogcat() {
        adb("logcat", "-c").requireSuccess("clearing logcat")
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
