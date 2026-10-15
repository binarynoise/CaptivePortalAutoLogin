package de.binarynoise.captiveportalautologin.integration

import java.util.stream.Stream
import kotlin.io.path.createDirectories
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import de.binarynoise.captiveportalautologin.portalproxy.portal.PortalState
import de.binarynoise.logger.Logger.log
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.TestInstance

/**
 * Installs the app on every emulator of this machine, one after another, and checks that it liberates
 * the captive portal served by `portalProxy`.
 *
 * Every emulator is set up like a phone joining a hotspot:
 * - `portalProxy` runs on the host machine and serves a portal page the app has to log into.
 * - an [HttpCaptureProxy] sits in front of it on the port the emulator is pointed at. It answers the captive
 *   portal probe with a redirect to the portal while the app is captured and with `204 No Content` after the
 *   app logged in, forwards everything else, and rejects tunnelled connections. Android ignores the exclusion
 *   list of its proxy settings for app traffic, so this is what lets the app reach the api server while it is
 *   still captured.
 * - the api server of this repository runs on the host machine, the app reports its liberation to it.
 *
 * A debug and a release build are tested the same way. The difference is that only a debug build can be
 * checked for the report itself, see [checkReportedToApiServer].
 *
 * Run it with `./gradlew :integrationTest:integrationTest`.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalAutoLoginIntegrationTest {
    
    private val environment = TestEnvironment.load()
    private lateinit var apiServer: ApiServerFixture
    private lateinit var portalProxy: PortalProxyFixture
    
    @BeforeAll
    fun startInfrastructure() {
        log("starting the api server on port ${environment.apiServerPort}")
        apiServer = ApiServerFixture(environment)
        apiServer.start()
        
        log("starting the portal proxy on ports ${environment.proxyPort} and ${environment.portalPort}")
        portalProxy = PortalProxyFixture(environment)
        portalProxy.start()
    }
    
    @AfterAll
    fun stopInfrastructure() {
        if (::portalProxy.isInitialized) runCatching { portalProxy.close() }
        if (::apiServer.isInitialized) runCatching { apiServer.close() }
    }
    
    @TestFactory
    fun liberatesCaptivePortalOnEveryEmulator(): Stream<DynamicTest> {
        val avds = AvdCatalog.listAvds(environment)
        val buildTypes = requestedBuildTypes()
        check(avds.isNotEmpty()) { "no emulators found" }
        return avds.stream().flatMap { avd ->
            buildTypes.stream().map { buildType ->
                dynamicTest("${avd.name} - ${buildType.displayName}") {
                    liberateCaptivePortal(avd, buildType)
                }
            }
        }
    }
    
    private fun requestedBuildTypes(): List<AppBuildType> {
        val requested = commaSeparatedProperty("captivePortalAutoLogin.buildTypes")?.map { it.lowercase() }
            ?: return AppBuildType.entries.toList()
        val buildTypes = AppBuildType.entries.filter { it.gradleName.lowercase() in requested }
        check(buildTypes.isNotEmpty()) { "no known build types in \"$requested\"" }
        return buildTypes
    }
    
    private fun liberateCaptivePortal(avd: Avd, buildType: AppBuildType) {
        val skipReason = avd.skipReason()
        if (skipReason != null) {
            log("skipping ${avd.name} (${buildType.displayName}): $skipReason")
            assumeTrue(false, "skipping ${avd.name}: $skipReason")
        }
        
        val logDirectory = environment.logDirectory.resolve("${avd.name}-${buildType.gradleName}")
        logDirectory.createDirectories()
        log("=== ${avd.name} (${buildType.displayName}) ===")
        
        portalProxy.updateState(PortalState.CAPTURED)
        
        EmulatorSession(environment, avd, logDirectory).use { session ->
            session.boot()
            try {
                runTestOn(session, buildType)
            } finally {
                writeFile(session.logDirectory.resolve("logcat.txt"), session.adb.logcatDump())
                // the test api url survives on the emulator image and must not leak into later runs
                runCatching { session.adb.clearTestApiBaseUrl() }.onFailure { log("clearing the test api server url failed: $it") }
            }
        }
    }
    
    private fun runTestOn(session: EmulatorSession, buildType: AppBuildType) {
        val network = CaptivePortalNetwork(environment, session.adb)
        val ui = AppUiFlow(session.adb, session.logDirectory)
        // the app uploads its statistics on its own schedule, which can be before this test starts looking,
        // so the number of reports has to be counted before the app even goes through the onboarding
        val successesBefore = apiServer.reportedSuccesses().size
        
        session.adb.uninstall(TestEnvironment.appPackageName)
        session.adb.install(environment.apk(buildType))
        session.adb.reverse(environment.deviceApiServerPort, environment.apiServerPort)
        
        network.enableLocationServices()
        network.configure()
        // the wifi is cycled because the system only probes for captive portals while it validates a network
        // for the first time, and the reconnect waits until the portal is reported
        network.reconnectWifi()
        network.requireCaughtInPortal()
        
        session.adb.enlargeLogcatBuffer()
        session.adb.clearLogcat()
        // the app reads the api server url of this run from a global setting that only adb shell can write
        session.adb.setTestApiBaseUrl(environment.apiBaseUrl.toString())
        session.adb.startHomeActivity()
        
        ui.completeOnboarding()
        
        // the experimental portal liberators are off by default, and only they can solve this portal
        ui.openAdvancedSettings()
        ui.enableExperimentalPortalLiberators()
        
        awaitLiberation(session, ui)
        // the app asks the system to reevaluate the network, which happens asynchronously
        network.waitUntilLiberated()
        
        checkReportedToApiServer(session, buildType, ui, successesBefore)
        checkNoCrashes(session)
    }
    
    /**
     * Nudges the app into a liberation attempt and waits for the portal to accept its login.
     *
     * The nudge is necessary: the app does not start liberating on its own after the experimental portal
     * liberators have been enabled in the advanced settings.
     */
    private fun awaitLiberation(session: EmulatorSession, ui: AppUiFlow) {
        ui.liberateNow()
        try {
            portalProxy.awaitLiberated(liberationTimeout)
        } catch (e: IllegalStateException) {
            throw IllegalStateException("the app did not log into the portal, see ${session.logDirectory}", e)
        }
        log("the portal reports the client as liberated")
    }
    
    /**
     * The app hands its result to a periodic `WorkManager` job that is only due an hour later, and forcing that
     * job with `cmd jobscheduler run` does not make `WorkManager` dispatch work that is not due yet. On top of
     * that a release build strips every `Log` call, so there is nothing left to observe until the job runs. A
     * debug build ships `QueuedWorkActivity`, which uploads whatever is queued right away, which is where the
     * report itself is checked.
     */
    private fun checkReportedToApiServer(
        session: EmulatorSession,
        buildType: AppBuildType,
        ui: AppUiFlow,
        successesBefore: Int,
    ) {
        if (!buildType.isDebuggable) {
            log("a release build reports with its hourly job, which a test cannot wait for")
            return
        }
        retry(2.minutes, interval = 2.seconds, description = "the app to report the liberation to the api server") {
            session.adb.startActivity("${TestEnvironment.appPackageName}/.QueuedWorkActivity")
            ui.uploadQueuedStatistics()
            apiServer.reportedSuccesses().size > successesBefore
        }
        val success = apiServer.reportedSuccesses().last()
        log("the app reported ${success.solver} for ${success.ssid} as ${success.version}")
    }
    
    private fun checkNoCrashes(session: EmulatorSession) {
        val crashLog = session.adb.crashLogDump()
        val crashLogFile = writeFile(session.logDirectory.resolve("crash-log.txt"), crashLog)
        check(TestEnvironment.appPackageName !in crashLog) {
            "the app crashed, see $crashLogFile"
        }
    }
    
    private companion object {
        val liberationTimeout = 3.minutes
    }
}
