@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.PortalRedirector
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.followRedirects
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.hasQueryParameter
import de.binarynoise.util.okhttp.requestUrl
import de.binarynoise.util.okhttp.submitOnlyForm
import de.binarynoise.util.okhttp.toHttpUrl
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

// https://docs.cradlepoint.com/r/NCOS-How-to-Setup-Hotspot-Services-Captive-Portal/Details-on-Using-UAM-Authentication-for-Your-Hotspot

fun HttpUrl.isEricssonCaptured(): Boolean {
    return this.queryParameter("res") in listOf("notyet", "failed", "other", "timeout", "rejected", "logoff")
}

fun HttpUrl.isEricssonSuccess(): Boolean {
    return this.queryParameter("res") in listOf("success", "already")
}

@SSID(
    "Bogestra",
    "KampsHotspot",
    "WIFI@DB",
    "WLAN@MainzerMobilität",
    "WLAN@RMV S-BAHN",
)
object Hotsplots : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        if (!response.requestUrl.isEricssonCaptured()) return false
        with(response.requestUrl) {
            if (host == "www.hotsplots.de" && encodedPath == "/auth/login.php") return true
            if (host == "auth.hotsplots.de" && encodedPath == "/login") return true
        }
        return false
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        response.submitOnlyForm(client).followRedirects(client) { !it.isEricssonSuccess() }
    }
}

@SSID("RRX Hotspot")
object IOB : PortalRedirector {
    override fun canRedirect(response: Response): Boolean {
        return response.requestUrl.host == "portal.iob.de" && response.requestUrl.hasQueryParameter("loginurl")
    }
    
    override fun redirect(client: OkHttpClient, response: Response, cookies: Set<Cookie>): Response {
        val loginUrl = response.requestUrl.queryParameter("loginurl")?.toHttpUrl(response.requestUrl)
        return client.get(loginUrl, null)
    }
}
