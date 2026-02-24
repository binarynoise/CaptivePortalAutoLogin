@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator.portals

import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.liberator.randomEmail
import de.binarynoise.liberator.tryOrIgnore
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.hasQueryParameter
import de.binarynoise.util.okhttp.parseJsonObject
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.json.JSONObject

@Experimental
@SSID(
    "FreeWiFi 24 Autohof Mühldorf",
    "FreeWiFi Burger King",
    "FreeWiFi Teufel",
    "WienerRiesenrad",
)
object SocialWave : PortalLiberator {
    val SOCIALWAVE_DOMAINS = listOf(
        "go.social-wave.com",
        "go.meinwlan.com",
    )
    val SOCIALWAVE_SPLASH_API_BASE = "https://splash-api.daisy.meinwlan.com/api/".toHttpUrl()
    
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host in SOCIALWAVE_DOMAINS //
            && response.requestUrl.hasQueryParameter("res") // 
            && response.requestUrl.hasQueryParameter("auth")
    }
    
    fun getAuthUrl(startPage: Response, helloJson: JSONObject, registerJson: JSONObject): HttpUrl {
        val authQueryParameter = startPage.requestUrl.queryParameter("auth") ?: error("no auth")
        var redir = startPage.requestUrl.queryParameter("redir")
        val token = registerJson.getString("AuthenticationToken")
        
        if (!registerJson.has("Username")) {
            // authoriseOnRouterOpenwrt
            if (redir == null) error("no redir token")
            return "http://$authQueryParameter/nodogsplash_auth_tok/".toHttpUrl()
                .newBuilder()
                .setQueryParameter("token", token)
                .setQueryParameter("redir", redir)
                .build()
        }
        
        if (!startPage.requestUrl.hasQueryParameter("redir")) tryOrIgnore {
            redir = helloJson.getJSONObject("Settings").getString("RedirectUrl")
        }
        // authoriseOnRouterRouterOs
        // query parameter "auth" has to be a viable URL
        return authQueryParameter.toHttpUrl()
            .newBuilder()
            .setQueryParameter("username", registerJson.getString("Username"))
            .setQueryParameter("password", token)
            .setQueryParameter("dst", redir)
            .build()
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val res = response.requestUrl.queryParameter("res") ?: error("no res query parameter")
        val helloJson = client.get(
            SOCIALWAVE_SPLASH_API_BASE, "hello.json", mapOf(
                "query" to res,
            )
        ).parseJsonObject()
        val registerEmailJson = client.postForm(
            SOCIALWAVE_SPLASH_API_BASE, "email/register.json", mapOf(
                "assigned_mac" to (cookies.find { it.name == "assigned_mac" }?.value ?: ""),
                "language" to "de",
                "email" to randomEmail(),
                "query" to res,
                "agree_terms" to "true",
                "agree_marketing" to "true",
            )
        ).parseJsonObject()
        val authUrl = getAuthUrl(response, helloJson, registerEmailJson)
        client.get(authUrl, null)
    }
}
