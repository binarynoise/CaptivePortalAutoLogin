package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.decodedPath
import de.binarynoise.util.okhttp.postJson
import de.binarynoise.util.okhttp.readText
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.json.JSONObject

@SSID(
    "Telekom_free",
    "Airport-Frankfurt",
    "AIRPORT-FREE-WIFI",
    "Telekom",
    "LHS-FREE",
    "Galeria Kunden-WLAN",
)
object TMobileHotspot : PortalLiberator {
    override fun canSolve(locationUrl: HttpUrl, response: Response): Boolean {
        return "hotspot.t-mobile.net" == locationUrl.host && locationUrl.decodedPath == "/wlan/redirect.do"
    }
    
    override fun solve(locationUrl: HttpUrl, client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val response1 = client.postJson(locationUrl, "/wlan/rest/freeLogin", """{"rememberMe":false}""")
        val wlanLoginStatus = JSONObject(response1.readText()).getJSONObject("user").getString("wlanLoginStatus")
        check(wlanLoginStatus == "online") { """wlanLoginStatus: "$wlanLoginStatus" != "online"""" }
    }
}
