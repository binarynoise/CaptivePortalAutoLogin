package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.followRedirects
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.getLocation
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.Response

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
@SSID("SSB fuer Dich - WiFi Free")
object CiscoISE : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.isRedirect && response.requestUrl.encodedPath == "/portal/gateway" // redirect to Cisco ISE CWA service
            && response.requestUrl.queryParameter("action") == "cwa" // CWA (Central Web Authentication)
            && response.requestUrl.queryParameter("type") == "drw" // DRW (Device Registration Web Authentication) is used for no-login guest WiFis
        // Cisco ISE portal port should be 8443, but was observed to be 8448 @ gast11.ssb-ag.de
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val response = client.get(response.requestUrl, response.getLocation()).followRedirects(client)
        response.checkSuccess()
        
        fun getCookie(name: String): String? {
            return cookies.singleOrNull { it.name == name }?.value
        }
        
        val token = response.headers("token").firstOrNull() ?: getCookie("token") ?: error("no token")
        val portalSessionId = getCookie("portalSessionId") ?: error("no portalSessionId")
        
        // accept AUP (Acceptable Use Policy)
        client.postForm(
            response.requestUrl,
            "AupSubmit.action",
            mapOf(
                "token" to token,
                "aupAccepted" to "true",
            ),
            mapOf(
                "from" to "AUP",
            ),
            preConnectSetup = {
                header("X-Requested-With", "XMLHttpRequest")
            },
        )
        
        client.postForm(
            response.requestUrl,
            "DoCoA.action",
            mapOf(
                "delayToCoA" to "0",
                "waitForCoA" to "true",
                "coaReason" to "Guest authenticated for network access",
                "coaSource" to "GUEST",
                "token" to token,
                "portalSessionId" to portalSessionId,
                "coaType" to "Reauth",
            ),
            preConnectSetup = {
                header("X-Requested-With", "XMLHttpRequest")
            },
        ).checkSuccess()
    }
}
