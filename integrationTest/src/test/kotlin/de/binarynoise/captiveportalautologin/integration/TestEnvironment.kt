package de.binarynoise.captiveportalautologin.integration

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Everything the test run needs to know about the machine it runs on and about the app under test.
 *
 * Most of the ports used here are not arbitrary:
 * - [proxyPort] and [portalPort] have to be `8000`/`8001`, because `BinarynoisePortalProxy` is the only
 *   `PortalLiberator` that solves the portal served by `portalProxy`, and it matches `portal.binarynoise.de:8001` only.
 * - [deviceApiServerPort] is the port the app talks to, the test forwards it to [apiServerPort] on the host with `adb reverse`.
 */
class TestEnvironment private constructor(
    val projectDirectory: Path,
    val androidSdkDirectory: Path,
    val appBuildDirectory: Path,
    val apiBaseUrl: HttpUrl,
    val deviceApiServerPort: Int,
    val apiServerPort: Int,
) {
    
    val emulatorBinary: Path = androidSdkDirectory.resolve("emulator/emulator")
    val adbBinary: Path = androidSdkDirectory.resolve("platform-tools/adb")
    
    /**
     * The emulator reaches the host machine through this address, everything else stays on the device.
     */
    val hostAddress: String = "10.0.2.2"
    
    /**
     * The address the app uses to reach the captive portal, the device resolves it through the proxy.
     */
    val portalHost: String = "portal.binarynoise.de"
    
    val proxyPort: Int = 8000
    val portalPort: Int = 8001
    
    /**
     * The proxy port of the `portalProxy` server itself, which the emulator never talks to because
     * [HttpCaptureProxy] sits in front of it.
     */
    val portalProxyPort: Int = 18000
    
    val logDirectory: Path = projectDirectory.resolve("build/integrationTest").toAbsolutePath()
    
    fun apk(buildType: AppBuildType): Path = appBuildDirectory.resolve(
        "outputs/apk/universal/${buildType.gradleName}/app-universal-${buildType.gradleName}.apk"
    ).also { apk ->
        check(apk.exists()) {
            "$apk does not exist, run ./gradlew :app:assembleUniversal${buildType.gradleName.replaceFirstChar(Char::uppercase)}"
        }
    }
    
    companion object {
        const val appPackageName = "de.binarynoise.captiveportalautologin"
        const val appHomeActivity = "$appPackageName/.HomeActivity"
        
        /**
         * `android.defaultConfig.minSdk`, emulators below this cannot run the app at all.
         */
        const val minSdk = 26
        
        fun load(): TestEnvironment {
            fun requiredProperty(name: String): String = System.getProperty(name)
                ?: error("system property $name is missing, run the tests through the :integrationTest:integrationTest task")
            
            val deviceApiServerPort = requiredProperty("captivePortalAutoLogin.deviceApiServerPort").toInt()
            return TestEnvironment(
                projectDirectory = Path(requiredProperty("captivePortalAutoLogin.projectDirectory")),
                androidSdkDirectory = Path(requiredProperty("captivePortalAutoLogin.androidSdkDirectory")).absolute()
                    .normalize(),
                appBuildDirectory = Path(requiredProperty("captivePortalAutoLogin.appBuildDirectory")).absolute()
                    .normalize(),
                apiBaseUrl = "http://127.0.0.1:$deviceApiServerPort/api/".toHttpUrl(),
                deviceApiServerPort = deviceApiServerPort,
                apiServerPort = requiredProperty("captivePortalAutoLogin.apiServerPort").toInt(),
            )
        }
    }
}
