package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.logger.Logger.log
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.firstPathSegment
import de.binarynoise.util.okhttp.followRedirects
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.postJson
import de.binarynoise.util.okhttp.readText
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.json.JSONObject

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
object DBWifi : PortalLiberator {
    val domains = setOf(
        "login.wifionice.de",
        "portal.wifi.bahn.de",
        "wifi-bahn.de",
        "wifi.bahn.de",
    )
    
    override fun canSolve(locationUrl: HttpUrl): Boolean {
        return locationUrl.host in domains
    }
    
    override fun solve(locationUrl: HttpUrl, client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val response1 = client.get(locationUrl, null).followRedirects(client)
        
        when {
            "cna" == response1.requestUrl.firstPathSegment -> {
                log("cna")
                val response1 = response.followRedirects(client)
                val response2 = client.postJson(response1.requestUrl, "/cna/logon", "{}") {
                    header("X-Csrf-Token", "csrf")
                }
                check(JSONObject(response2.readText()).getString("result") == "success") { "response does not contain success" }
            }
            "sp" == response1.requestUrl.firstPathSegment -> {
                // works
                log("sp")
                client.postForm(
                    locationUrl, "/login", mapOf(
                        "login" to "oneclick",
                        "oneSubscriptionForm_connect_policy_accept" to "on",
                    )
                ).followRedirects(client).checkSuccess()
            }
            else -> {
                log("else")
                val csrfToken = cookies.find { it.name == "csrf" }?.value ?: error("no csrf")
                client.postForm(
                    response1.requestUrl, null, mapOf(
                        "login" to "true",
                        "CSRFToken" to csrfToken,
                    )
                ).followRedirects(client)
            }
        }
    }
}
