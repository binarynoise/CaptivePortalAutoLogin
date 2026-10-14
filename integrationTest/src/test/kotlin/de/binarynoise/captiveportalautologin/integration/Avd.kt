package de.binarynoise.captiveportalautologin.integration

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.bufferedReader
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

/**
 * An emulator image known to the Android SDK.
 *
 * The api level and abi are read from the AVD configuration when possible, they are only used to skip
 * emulators which can't run the app anyway.
 */
class Avd(
    val name: String,
    val apiLevel: Int?,
    val abi: String?,
) {
    /**
     * `null` means the emulator can be used, everything else explains why it is skipped.
     */
    fun skipReason(): String? = when {
        apiLevel != null && apiLevel < TestEnvironment.minSdk -> "API $apiLevel is below the minSdk of ${TestEnvironment.minSdk}"
        skippedAvds.contains(name) -> "skipped with -PskipAvds"
        else -> null
    }
    
    override fun toString(): String = "$name (api $apiLevel, $abi)"
    
    companion object {
        /**
         * Emulators to leave out of the run, for an emulator that is broken rather than for an app problem.
         *
         * Empty by default on purpose: an emulator the app crashes on has to fail the run, so that the suite
         * never reports green while a device is broken. Use `-PskipAvds=...` to leave one out while working on
         * it.
         */
        private val skippedAvds: Set<String> =
            commaSeparatedProperty("captivePortalAutoLogin.skipAvds").orEmpty().toSet()
    }
}

object AvdCatalog {
    
    /**
     * All emulators installed on this machine, in the order the Android SDK lists them.
     */
    fun listAvds(environment: TestEnvironment): List<Avd> {
        val avdHome = avdHome()
        val result = runCommand(
            listOf(environment.emulatorBinary.toAbsolutePath().toString(), "-list-avds"),
            workingDirectory = environment.projectDirectory,
        ).requireSuccess("listing the available emulators")
        
        val requested = commaSeparatedProperty("captivePortalAutoLogin.avds")
        
        return result.standardOutput.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filter { name -> requested == null || name in requested }
            .map { name -> Avd(name = name, apiLevel = readApiLevel(avdHome, name), abi = readAbi(avdHome, name)) }
    }
    
    private fun avdHome(): Path {
        val avdHomeFromEnvironment = System.getenv("ANDROID_AVD_HOME")?.takeIf { it.isNotBlank() }?.let { Path(it) }
        return avdHomeFromEnvironment ?: Path(System.getProperty("user.home"), ".android/avd")
    }
    
    private fun configurationFile(avdHome: Path, name: String): Path? {
        val indirectionFile = avdHome.resolve("$name.ini")
        val redirectedDirectory = if (indirectionFile.exists()) {
            indirectionFile.bufferedReader().useLines { lines ->
                lines.firstOrNull { it.startsWith("path=") }?.removePrefix("path=")?.trim()?.let { Path(it) }
            }
        } else {
            null
        }
        val directory = redirectedDirectory ?: avdHome.resolve("$name.avd")
        return directory.takeIf { it.isDirectory() }?.resolve("config.ini")?.takeIf { it.exists() }
    }
    
    private fun readConfiguration(avdHome: Path, name: String): Map<String, String> {
        val configurationFile = configurationFile(avdHome, name) ?: return emptyMap()
        return configurationFile.bufferedReader().useLines { lines ->
            lines.mapNotNull { line ->
                val separatorIndex = line.indexOf('=')
                if (line.startsWith("#") || separatorIndex <= 0) {
                    null
                } else {
                    line.substring(0, separatorIndex).trim() to line.substring(separatorIndex + 1).trim()
                }
            }.toMap()
        }
    }
    
    /**
     * The api level is part of the system image path, e.g. `system-images/android-33/lineage/x86_64/`.
     */
    private fun readApiLevel(avdHome: Path, name: String): Int? {
        val configuration = readConfiguration(avdHome, name)
        val systemImage = configuration["image.sysdir.1"] ?: configuration["image.sysdir.2"] ?: return null
        return systemImage.split("/").firstOrNull { it.startsWith("android-") }?.removePrefix("android-")?.toIntOrNull()
    }
    
    private fun readAbi(avdHome: Path, name: String): String? {
        val configuration = readConfiguration(avdHome, name)
        return configuration["abi.type"] ?: configuration["image.sysdir.1"]?.split("/")
            ?.lastOrNull()
            ?.takeIf { it.isNotEmpty() }
    }
}
