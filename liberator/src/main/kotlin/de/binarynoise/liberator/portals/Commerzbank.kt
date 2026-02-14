package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.parseJsonObject
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.Response

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
@SSID("Commerzbank-Wifi")
object Commerzbank : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == "wifiaccess.co" // 
            && response.requestUrl.pathSegments.getOrNull(1) == "portal" // 
            && !response.isRedirect
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        client.postForm(response.requestUrl, "/portal_api.php", mapOf("action" to "init")).checkSuccess()
        val json1 = client.postForm(
            response.requestUrl,
            "/portal_api.php",
            mapOf(
                "action" to "subscribe",
                "type" to "one",
                "policy_accept" to "true",
            ),
        ).parseJsonObject()
        val subscribeObject = json1.getJSONObject("info").getJSONObject("subscribe")
        client.postForm(
            response.requestUrl,
            "/portal_api.php",
            mapOf(
                "action" to "authenticate",
                "login" to subscribeObject.getString("login"),
                "password" to subscribeObject.getString("password"),
                "policy_accept" to "true",
            ),
        ).checkSuccess()
    }
}
