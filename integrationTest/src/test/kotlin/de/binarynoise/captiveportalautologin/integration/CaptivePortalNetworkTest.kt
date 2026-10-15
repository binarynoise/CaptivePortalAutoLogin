package de.binarynoise.captiveportalautologin.integration

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The portal detection parses `dumpsys connectivity` output, which buries the transports and the
 * capabilities in lines spanning several hundred characters.
 */
class CaptivePortalNetworkTest {
    @Test
    fun `detects a captive portal on the wifi network`() {
        assertTrue(CaptivePortalNetwork.isCaughtInPortal(wifiCaptiveDump))
        assertFalse(CaptivePortalNetwork.isValidated(wifiCaptiveDump))
    }
    
    @Test
    fun `detects a validated wifi network`() {
        assertFalse(CaptivePortalNetwork.isCaughtInPortal(wifiValidatedDump))
        assertTrue(CaptivePortalNetwork.isValidated(wifiValidatedDump))
    }
    
    @Test
    fun `ignores a captive portal on the mobile network`() {
        assertFalse(CaptivePortalNetwork.isCaughtInPortal(mobileCaptiveDump))
        assertTrue(CaptivePortalNetwork.wifiNetworkLines(mobileCaptiveDump).isEmpty())
    }
    
    @Test
    fun `finds a captive portal beyond the first wifi network`() {
        val dump = "$wifiValidatedDump\n$wifiCaptiveDump"
        assertTrue(CaptivePortalNetwork.isCaughtInPortal(dump))
    }
    
    @Test
    fun `describes the transports and capabilities instead of truncating them away`() {
        val description = CaptivePortalNetwork.describeNetworks("$mobileCaptiveDump\n$wifiCaptiveDump")
        assertTrue("Transports: CELLULAR" in description, description)
        assertTrue("Transports: WIFI" in description, description)
        assertTrue("CAPTIVE_PORTAL" in description, description)
        assertTrue("MOBILE[HSPA]" in description, description)
    }
    
    @Test
    fun `describes the absence of any network`() {
        val description = CaptivePortalNetwork.describeNetworks("")
        assertTrue("no networks" in description, description)
    }
    
    private companion object {
        const val mobileCaptiveDump =
            "NetworkAgentInfo{network{100}  handle{432902426637}  ni{MOBILE[HSPA] CONNECTED extra: epc.tmobile.com} Score(50 ; KeepConnected : 0 ; Policies : )  created everCaptivePortal isCaptivePortal  lp{{InterfaceName: eth0 LinkAddresses: [ 10.0.2.15/24 ]}}  nc{[ Transports: CELLULAR Capabilities: MMS&SUPL&DUN&FOTA&IMS&CBS&INTERNET&NOT_RESTRICTED&TRUSTED&NOT_VPN&CAPTIVE_PORTAL&NOT_ROAMING&NOT_CONGESTED&NOT_SUSPENDED&NOT_VCN_MANAGED LinkUpBandwidth>=14Kbps LinkDnBandwidth>=14Kbps Specifier: <TelephonyNetworkSpecifier [mSubId = 1]> SubscriptionIds: {1} UnderlyingNetworks: Null]}  factorySerialNumber=4}"
        const val wifiCaptiveDump =
            "NetworkAgentInfo{network{101}  handle{437197393933}  ni{WIFI CONNECTED extra: } Score(60 ; KeepConnected : 0 ; Policies : TRANSPORT_PRIMARY&IS_UNMETERED)  created everCaptivePortal isCaptivePortal  lp{{InterfaceName: wlan0 LinkAddresses: [ 10.0.2.16/24 ]}}  nc{[ Transports: WIFI Capabilities: NOT_METERED&INTERNET&NOT_RESTRICTED&TRUSTED&NOT_VPN&NOT_ROAMING&FOREGROUND&NOT_CONGESTED&NOT_SUSPENDED&NOT_VCN_MANAGED&CAPTIVE_PORTAL LinkUpBandwidth>=12000Kbps LinkDnBandwidth>=30000Kbps Specifier: <WifiNetworkAgentSpecifier [SSID=\"AndroidWifi\"]> TransportInfo: <SSID: \"AndroidWifi\"> SignalStrength: -50 UnderlyingNetworks: Null]}  factorySerialNumber=5}"
        const val wifiValidatedDump =
            "NetworkAgentInfo{network{101}  handle{437197393933}  ni{WIFI CONNECTED extra: } Score(60 ; KeepConnected : 0 ; Policies : TRANSPORT_PRIMARY&IS_UNMETERED)  created  lp{{InterfaceName: wlan0 LinkAddresses: [ 10.0.2.16/24 ]}}  nc{[ Transports: WIFI Capabilities: NOT_METERED&INTERNET&NOT_RESTRICTED&TRUSTED&NOT_VPN&NOT_ROAMING&FOREGROUND&NOT_CONGESTED&NOT_SUSPENDED&NOT_VCN_MANAGED&VALIDATED LinkUpBandwidth>=12000Kbps LinkDnBandwidth>=30000Kbps Specifier: <WifiNetworkAgentSpecifier [SSID=\"AndroidWifi\"]> TransportInfo: <SSID: \"AndroidWifi\"> SignalStrength: -50 UnderlyingNetworks: Null]}  factorySerialNumber=5}"
    }
}
