package de.binarynoise.captiveportalautologin.integration

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

data class CommandResult(
    val command: List<String>,
    val exitCode: Int,
    val standardOutput: String,
    val standardError: String,
) {
    fun requireSuccess(description: String): CommandResult {
        if (exitCode != 0) {
            error(
                buildString {
                    append(description)
                    append(" failed with exit code ")
                    append(exitCode)
                    append("\ncommand: ")
                    append(command.joinToString(" "))
                    if (standardOutput.isNotBlank()) append("\nstdout: ").append(standardOutput.trim())
                    if (standardError.isNotBlank()) append("\nstderr: ").append(standardError.trim())
                },
            )
        }
        return this
    }
}

fun runCommand(
    command: List<String>,
    workingDirectory: java.nio.file.Path? = null,
    environment: Map<String, String> = emptyMap(),
): CommandResult {
    val processBuilder = ProcessBuilder(command).apply {
        workingDirectory?.let { directory(it.toFile()) }
        // merged into stdout, reading both streams one after the other could deadlock on large stderr output
        redirectErrorStream(true)
    }
    environment.forEach { (key, value) -> processBuilder.environment()[key] = value }
    
    val process = processBuilder.start()
    val standardOutput = process.inputStream.bufferedReader().use { it.readText() }
    val standardError = process.errorStream.bufferedReader().use { it.readText() }
    val exitCode = process.waitFor()
    
    return CommandResult(command, exitCode, standardOutput, standardError)
}

inline fun retry(timeout: Duration, interval: Duration = 500.milliseconds, description: String, block: () -> Boolean) {
    retryValue(timeout, interval, description) {
        check(block()) { "condition not met yet" }
    }
}

inline fun <T> retryValue(timeout: Duration, interval: Duration = 500.milliseconds, description: String, block: () -> T): T {
    val timeSource = TimeSource.Monotonic
    val start = timeSource.markNow()
    var lastFailure: Throwable? = null
    while (start.elapsedNow() < timeout) {
        try {
            return block()
        } catch (e: Exception) {
            lastFailure = e
        }
        val remaining = timeout - start.elapsedNow()
        if (remaining <= Duration.ZERO) break
        Thread.sleep(minOf(interval, remaining).inWholeMilliseconds)
    }
    val illegalStateException = IllegalStateException("timed out after $timeout waiting for $description")
    if (lastFailure != null) illegalStateException.addSuppressed(lastFailure)
    throw illegalStateException
}

fun writeFile(file: java.nio.file.Path, content: String): java.nio.file.Path {
    file.parent?.createDirectories()
    file.writeText(content)
    return file
}

/**
 * A comma separated system property like `-Pavds` forwarded as `captivePortalAutoLogin.avds`, or `null`
 * when it is absent or blank. Shared by the avd selection and the build type selection.
 */
fun commaSeparatedProperty(name: String): List<String>? =
    System.getProperty(name)?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.takeIf { it.isNotEmpty() }

fun isPortListening(port: Int, timeoutMillis: Int = 500): Boolean = runCatching {
    Socket().use { socket ->
        socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), timeoutMillis)
    }
}.isSuccess

fun isPortFree(port: Int): Boolean = runCatching { ServerSocket(port).close() }.isSuccess

fun freePort(): Int = ServerSocket(0).use { it.localPort }

/**
 * A leftover server of an earlier run would silently answer the probes of this run, so the ports have to
 * be free before a fixture is started.
 */
fun requirePortsAreFree(vararg ports: Int, owner: String) {
    val occupied = ports.filter(::isPortListening)
    check(occupied.isEmpty()) { "the ports $occupied are already in use, $owner is probably still running" }
}
