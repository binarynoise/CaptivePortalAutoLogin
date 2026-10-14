package de.binarynoise.captiveportalautologin.integration

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Puts the emulator into a state where it is caught in the captive portal served by [PortalProxyFixture],
 * and reads what the system thinks about that network.
 *
 * The emulator only probes for captive portals while it validates a network for the first time, so the
 * Wi-Fi has to be cycled after the settings are applied.
 */
class CaptivePortalNetwork(
    private val environment: TestEnvironment,
    private val adb: Adb,
) {
    
    private val portalTestUrl = "http://${environment.hostAddress}:${environment.proxyPort}/"
    
    fun configure() {
        // all traffic of the device goes through the proxy, it answers the portal url with a redirect and
        // forwards everything else, which is what lets the app report to the api server while it is captured
        mapOf(
            "captive_portal_http_url" to portalTestUrl,
            "captive_portal_https_url" to portalTestUrl,
            "captive_portal_use_https" to "0",
            "captive_portal_fallback_url" to "",
            "http_proxy" to "${environment.hostAddress}:${environment.proxyPort}",
        ).forEach { (key, value) -> adb.setGlobalSetting(key, value) }
    }
    
    fun enableLocationServices() {
        adb.shellOrNull("cmd location set-location-enabled true")
        adb.setGlobalSetting("location_mode", "3", secure = true)
    }
    
    fun reconnectWifi() {
        adb.shell("svc wifi disable")
        retry(30.seconds, interval = 1.seconds, description = "the wifi to turn off") {
            adb.getGlobalSetting("wifi_on") == "0"
        }
        adb.shell("svc wifi enable")
        retry(60.seconds, interval = 1.seconds, description = "the wifi to turn on") {
            adb.getGlobalSetting("wifi_on") == "1"
        }
        try {
            retry(60.seconds, interval = 2.seconds, description = "the wifi to connect") {
                hasWifiNetwork()
            }
        } catch (failure: IllegalStateException) {
            throw IllegalStateException(
                "the wifi did not connect, networks:\n${describeNetworks()}",
                failure,
            )
        }
        awaitPortalPresence()
    }
    
    private fun awaitPortalPresence() {
        awaitNetworkCondition(
            timeout = 90.seconds,
            description = "the system to report a captive portal",
            failureMessage = "the system does not report a captive portal",
        ) { isCaughtInPortal() }
    }
    
    /**
     * The wifi can flap briefly after it reconnects, so the check gives it a moment to settle.
     */
    fun requireCaughtInPortal() {
        awaitNetworkCondition(
            timeout = 30.seconds,
            description = "the captive portal to still be reported",
            failureMessage = "the system does not report a captive portal",
        ) { isCaughtInPortal() }
    }
    
    /**
     * Whether the system currently reports `NET_CAPABILITY_CAPTIVE_PORTAL` for the Wi-Fi network.
     *
     * The app only ever handles Wi-Fi networks, see the `TRANSPORT_Wi-Fi` of its network request.
     */
    fun isCaughtInPortal(): Boolean = isCaughtInPortal(connectivityDump())
    
    /**
     * Whether a Wi-Fi network exists at all, regardless of its capabilities.
     *
     * A missing Wi-Fi network calls for different repairs than a connected one without a portal,
     * so the reconnect waits for this before it waits for the portal.
     */
    fun hasWifiNetwork(): Boolean = wifiNetworkLines(connectivityDump()).isNotEmpty()
    
    /**
     * Whether the system considers the Wi-Fi network to have internet access.
     *
     * The app tells the system whether the network has connectivity after every liberation attempt, so a
     * validated network is what tells a successful liberation apart from a failed one.
     */
    fun isValidated(): Boolean = isValidated(connectivityDump())
    
    private fun connectivityDump(): String = adb.shellOrNull("dumpsys connectivity").orEmpty()
    
    /**
     * The capabilities of every network, to make a failing check readable.
     *
     * Only the network state, the transports and the capabilities are shown, the full dump buries
     * them in transport details that span several hundred characters per network.
     */
    fun describeNetworks(): String = describeNetworks(connectivityDump())
    
    fun waitUntilLiberated(timeout: Duration = 60.seconds) {
        awaitNetworkCondition(
            timeout = timeout,
            description = "the system to stop reporting a captive portal",
            failureMessage = "the system does not consider the network liberated",
        ) { !isCaughtInPortal() && isValidated() }
    }
    
    private fun awaitNetworkCondition(
        timeout: Duration,
        description: String,
        failureMessage: String,
        condition: () -> Boolean,
    ) {
        try {
            retry(timeout, interval = 2.seconds, description = description, block = condition)
        } catch (failure: IllegalStateException) {
            throw IllegalStateException("$failureMessage:\n${describeNetworks()}", failure)
        }
    }
    
    companion object {
        internal fun isCaughtInPortal(dump: String): Boolean =
            wifiNetworkLines(dump).any { line -> line.contains("CAPTIVE_PORTAL") }
        
        internal fun isValidated(dump: String): Boolean =
            wifiNetworkLines(dump).any { line -> line.contains("VALIDATED") }
        
        internal fun wifiNetworkLines(dump: String): List<String> =
            dump.lineSequence().filter { line -> line.contains("nc{[") && line.contains("Transports: WIFI") }.toList()
        
        internal fun describeNetworks(dump: String): String {
            val lines = dump.lineSequence().filter { line -> line.contains("nc{[") }.toList()
            if (lines.isEmpty()) return "(no networks reported)"
            return lines.joinToString("\n", transform = ::summarizeNetworkLine)
        }
        
        private fun summarizeNetworkLine(line: String): String {
            val networkInfo = networkInfoPattern.find(line)?.value
            val transports = transportsPattern.find(line)?.value
            val capabilities = capabilitiesPattern.find(line)?.value
            if (networkInfo == null && transports == null && capabilities == null) {
                return line.trim().take(500)
            }
            return listOfNotNull(networkInfo, transports, capabilities).joinToString(" | ")
        }
        
        private val networkInfoPattern = Regex("""ni\{[^}]*\}""")
        private val transportsPattern = Regex("""Transports: [^]]*""")
        private val capabilitiesPattern = Regex("""Capabilities: [^]]*""")
    }
}
