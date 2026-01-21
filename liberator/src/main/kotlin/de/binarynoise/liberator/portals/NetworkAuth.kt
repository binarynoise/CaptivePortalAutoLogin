package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.followRedirects
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.Response

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
@SSID(
    "BACK-FACTORY Besucher",
    "MEET ME @ STARBUCKS",
    "CLIENTES.LME",
    "Free Wifi Ris8tto",
    "dm Kunden WLAN",
    "JD-Gast-WiFi",
)
object NetworkAuth : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host.endsWith("network-auth.com") && !response.isRedirect
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        client.get(response.requestUrl, "grant").followRedirects(client).checkSuccess()
    }
}
