package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.enforceHttps
import de.binarynoise.util.okhttp.followRedirects
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.Response

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
@SSID(
    "HUGO-BOSS-WIFI",
    "WiFi Darmstadt",
    "mycloud",
    "o2 free Wifi",
)
object TheCloud : PortalLiberator {
    const val THECLOUD_DOMAIN = "service.thecloud.eu"
    
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == THECLOUD_DOMAIN && response.requestUrl.isHttps
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val baseUrl = response.requestUrl.enforceHttps()
        val getOnlineResponse = client.get(baseUrl, "getonline").followRedirects(client)
        client.postForm(baseUrl, "macauthlogin/v2/registration", mapOf("terms" to "true")) //
            .followRedirects(client) { it.host == THECLOUD_DOMAIN }.checkSuccess()
    }
}
