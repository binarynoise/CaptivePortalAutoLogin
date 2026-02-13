package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.enforceHttps
import de.binarynoise.util.okhttp.followRedirects
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import de.binarynoise.util.okhttp.toHttpUrl
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.Response

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
@SSID(
    "mycloud",
    "o2 free Wifi",
    "WiFi Darmstadt",
)
object TheCloud : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == "service.thecloud.eu"
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val loginUrl = "macauthlogin/v1/registration".toHttpUrl(response.requestUrl).enforceHttps()
        client.postForm(loginUrl, null, mapOf("terms" to "true")).followRedirects(client).checkSuccess()
    }
}
