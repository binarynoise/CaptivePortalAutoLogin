package de.binarynoise.liberator.portals

import de.binarynoise.liberator.LiberatorExtras
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.json.getJsonObject
import de.binarynoise.util.json.getString
import de.binarynoise.util.okhttp.decodedPath
import de.binarynoise.util.okhttp.parseJsonObject
import de.binarynoise.util.okhttp.postJson
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@SSID(
    "Telekom_free",
    "Airport-Frankfurt",
    "AIRPORT-FREE-WIFI",
    "Telekom",
    "LHS-FREE",
    "Galeria Kunden-WLAN",
)
object TMobileHotspot : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return "hotspot.t-mobile.net" == response.requestUrl.host && response.requestUrl.decodedPath == "/wlan/redirect.do"
    }
    
    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        val response1 = client.postJson(response.requestUrl, "/wlan/rest/freeLogin", mapOf("rememberMe" to false))
        val wlanLoginStatus = response1.parseJsonObject().getJsonObject("user").getString("wlanLoginStatus")
        check(wlanLoginStatus == "online") { """wlanLoginStatus: "$wlanLoginStatus" != "online"""" }
    }
}
