@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator.portals

import kotlinx.serialization.json.JsonObject
import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.LiberatorExtras
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.liberator.UnsupportedPortalException
import de.binarynoise.liberator.btoa
import de.binarynoise.liberator.randomEmail
import de.binarynoise.liberator.tryOrDefault
import de.binarynoise.liberator.tryOrIgnore
import de.binarynoise.liberator.tryOrNull
import de.binarynoise.rhino.RhinoParser
import de.binarynoise.util.json.getBoolean
import de.binarynoise.util.json.getJsonArray
import de.binarynoise.util.json.getJsonObject
import de.binarynoise.util.json.getString
import de.binarynoise.util.json.has
import de.binarynoise.util.json.toAny
import de.binarynoise.util.okhttp.decodedPath
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.getInput
import de.binarynoise.util.okhttp.hasQueryParameter
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.parseJsonObject
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Experimental
@SSID(
    "Cotidiano-Gast",
    "FreeWiFi 24 Autohof Mühldorf",
    "FreeWiFi Burger King",
    "FreeWiFi Teufel",
    "FreeWiFi Wenkers am Markt",
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
    
    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        return solve(
            client,
            res = response.requestUrl.queryParameter("res") ?: error("no res query parameter"),
            auth = response.requestUrl.queryParameter("auth") ?: error("no auth"),
            redir = response.requestUrl.queryParameter("redir"),
            extras,
        )
    }
    
    fun solve(client: OkHttpClient, res: String, auth: String, redir: String?, extras: LiberatorExtras) {
        val helloJson = getHelloJson(client, res)
        
        if (!helloJson.getBoolean("Success")) {
            val reason = helloJson.getString("Reason")
            if (reason == "DeactivatedLocation") throw UnsupportedPortalException("Deactivated Location")
            throw IllegalStateException("helloJson not successful reason=$reason")
        }
        
        if (tryOrDefault(false) {
                helloJson.getJsonObject("Settings").getBoolean("IsCurrentlyOffBySchedule")
            }) throw UnsupportedPortalException("Hotspot turned off by schedule")
        
        val authenticationMethods = helloJson.getJsonObject("Settings")
            .getJsonArray("AuthenticationMethods")
            .toTypedArray()
            .map { it.toAny() }
            .filterIsInstance<String>()
        if (authenticationMethods.isEmpty()) throw IllegalStateException("authentication methods are empty")
        when {
            "anonymous" in authenticationMethods -> return solveAnonymous(client, helloJson, res, auth, redir, extras)
            "email" in authenticationMethods -> return solveEmail(client, helloJson, res, auth, redir, extras)
        }
        
        // at this point we don't support any offered authentication methods
        // if there are unknown authentication methods offered, throw them to log them
        val unsupportedAuthenticationMethods = listOf(
            "password",
            "facebook",
            "instagram",
        )
        val filteredAuthenticationMethods = authenticationMethods.filterNot { it in unsupportedAuthenticationMethods }
        if (filteredAuthenticationMethods.isEmpty()) throw UnsupportedPortalException("unsupported authentication method")
        throw IllegalArgumentException("invalid authentication methods: ${filteredAuthenticationMethods.joinToString()}")
    }
    
    fun getHelloJson(client: OkHttpClient, res: String): JsonObject {
        return client.get(
            SOCIALWAVE_SPLASH_API_BASE, "hello.json", mapOf(
                "query" to res,
            )
        ).parseJsonObject()
    }
    
    fun solveAnonymous(
        client: OkHttpClient,
        helloJson: JsonObject,
        res: String,
        auth: String,
        redir: String?,
        extras: LiberatorExtras,
    ) {
        val loginJson = client.get(
            SOCIALWAVE_SPLASH_API_BASE, "anonymous/login.json", mapOf(
                "query" to res,
                "agree_marketing" to "false",
                "agree_terms" to "false",
                "language" to "en",
            )
        ).parseJsonObject()
        performAuth(client, helloJson, loginJson, auth, redir)
    }
    
    fun solveEmail(
        client: OkHttpClient,
        helloJson: JsonObject,
        res: String,
        auth: String,
        redir: String?,
        extras: LiberatorExtras,
    ) {
        val registerEmailJson = client.postForm(
            SOCIALWAVE_SPLASH_API_BASE, "email/register.json", mapOf(
                "query" to res,
                "email" to randomEmail(),
                "assigned_mac" to (extras.cookies.find { it.name == "assigned_mac" }?.value ?: ""),
                "language" to "en",
                "agree_terms" to "false",
                "agree_marketing" to "false",
            )
        ).parseJsonObject()
        performAuth(client, helloJson, registerEmailJson, auth, redir)
    }
    
    fun performAuth(
        client: OkHttpClient,
        helloJson: JsonObject,
        registerJson: JsonObject,
        auth: String,
        redir: String?,
    ) {
        client.get(
            getAuthUrl(helloJson, registerJson, auth, redir),
            null,
        )
    }
    
    fun getAuthUrl(helloJson: JsonObject, registerJson: JsonObject, auth: String, redir: String?): HttpUrl {
        val token = registerJson.getString("AuthenticationToken")
        var redir = redir
        
        if (!registerJson.has("Username")) {
            // authoriseOnRouterOpenwrt
            if (redir == null) error("no redir token")
            return "http://$auth/nodogsplash_auth_tok/".toHttpUrl()
                .newBuilder()
                .setQueryParameter("token", token)
                .setQueryParameter("redir", redir)
                .build()
        }
        
        if (redir == null) tryOrIgnore {
            redir = helloJson.getJsonObject("Settings").getString("RedirectUrl")
        }
        // authoriseOnRouterRouterOs
        // query parameter "auth" has to be a viable URL
        return auth.toHttpUrl()
            .newBuilder()
            .setQueryParameter("username", registerJson.getString("Username"))
            .setQueryParameter("password", token)
            .setQueryParameter("dst", redir)
            .build()
    }
}

@Experimental
@SSID(
    "MeinHotspot",
)
object MeinWlan : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == "login.meinwlan.com" //
            && response.requestUrl.decodedPath == "/login" //
            && response.requestUrl.hasQueryParameter("dst")
    }
    
    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        val html = response.parseHtml()
        val auth = html.getInput("auth")
        val rhino = RhinoParser()
        val res = html.getElementsByTag("script").mapNotNull {
            tryOrNull {
                val data = it.data()
                val assigments = rhino.parseAssignments(data)
                assigments["btoa.0"]
            }
        }.map { btoa(it) }.first()
        SocialWave.solve(client, res, auth, null, extras)
    }
}
