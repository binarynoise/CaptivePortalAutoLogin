package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.followRedirects
import de.binarynoise.util.okhttp.get
import okhttp3.Cookie
import okhttp3.HttpUrl
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
    override fun canSolve(locationUrl: HttpUrl, response: Response): Boolean {
        return locationUrl.host.endsWith("network-auth.com") && !response.isRedirect
    }
    
    override fun solve(locationUrl: HttpUrl, client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        client.get(locationUrl, "grant").followRedirects(client).checkSuccess()
    }
}
