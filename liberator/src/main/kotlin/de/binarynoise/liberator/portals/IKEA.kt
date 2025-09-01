package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.firstPathSegment
import de.binarynoise.util.okhttp.followRedirects
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.getLocation
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.readText
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.json.JSONObject

@SSID("IKEA WiFi")
@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
object IKEA : PortalLiberator {
    override fun canSolve(locationUrl: HttpUrl): Boolean {
        return "yo-wifi.net" == locationUrl.host && "authen" == locationUrl.firstPathSegment
    }
    
    override fun solve(locationUrl: HttpUrl, client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val response1 = client.get(response.requestUrl, response.getLocation())
        
        val url1 = response1.getLocation() ?: error("no location 1")
        val mac = response1.requestUrl.queryParameter("user_mac") ?: error("no mac")
        val deviceName = response1.requestUrl.queryParameter("device_name") ?: error("no deviceName")
        
        val response2 = client.get(response1.requestUrl, url1)
        
        val response3 = client.postForm(
            response2.requestUrl,
            "/login/tc",
            mapOf(),
            queryParameters = mapOf(
                "client_id" to "1",
                "nasid" to deviceName,
                "save_mac" to "false",
                "user_mac" to mac.replace(":", "-"),
                "user_type" to "9", // TERMS_AND_CONDITIONS
            ),
        )
        
        val json = JSONObject(response3.readText())
        
        val payload = json.getJSONObject("payload")
        val realm = payload.getString("realm")
        val username = payload.getString("username")
        val password = payload.getString("password")
        
        val userid = realm + "1\\" + username + "\\9"
        
        val response4 = client.get(
            response3.requestUrl,
            "/authen/login/",
            mapOf(
                "userid" to userid,
                "password" to password,
            ),
        )
        response4.followRedirects(client).checkSuccess()
    }
}
