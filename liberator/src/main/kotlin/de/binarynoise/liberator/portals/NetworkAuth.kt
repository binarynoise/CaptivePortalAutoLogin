@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.liberator.portals.NetworkAuth.isNetworkAuthDomain
import de.binarynoise.liberator.portals.NetworkAuth.isNetworkAuthGrantUrl
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.followRedirects
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.getLocationUrl
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@SSID(
    "BACK-FACTORY Besucher",
    "MEET ME @ STARBUCKS",
    "CLIENTES.LME",
    "Free Wifi Ris8tto",
    "JD-Gast-WiFi",
)
object NetworkAuth : PortalLiberator {
    
    fun HttpUrl.isNetworkAuthDomain(): Boolean {
        return this.host.endsWith("network-auth.com")
    }
    
    fun HttpUrl.isNetworkAuthGrantUrl(): Boolean {
        return this.isNetworkAuthDomain() && this.pathSegments.last() == "grant"
    }
    
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.isNetworkAuthDomain() && !response.isRedirect
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        client.get(response.requestUrl, "grant").followRedirects(client).checkSuccess()
    }
}

/**
 * This [PortalLiberator] is to be used for [NetworkAuth] networks,
 * where the embedded subportal does not do any authorization on its own.
 * In this case [NetworkAuthSubPortal] will force [NetworkAuth] to solve the portal anyways.
 */
@SSID("dm Kunden WLAN")
object NetworkAuthSubPortal : PortalLiberator {
    val knownDomains = listOf(
        "de-freewifiaccess.dm-drogeriemarkt.org",
    )
    
    override fun canSolve(response: Response): Boolean {
        if (!response.requestUrl.isNetworkAuthDomain()) return false
        if (!response.isRedirect) return false
        val locationUrl = response.getLocationUrl() ?: return false
        if (knownDomains.contains(locationUrl.host)) return false
        val base_grant_url = locationUrl.queryParameter("base_grant_url")?.toHttpUrl() ?: return false
        return base_grant_url.isNetworkAuthDomain() && base_grant_url.isNetworkAuthGrantUrl()
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        return NetworkAuth.solve(client, response, cookies)
    }
}
