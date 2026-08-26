@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator.portals

import kotlinx.serialization.json.JsonObject
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
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.decodedPath
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.getInput
import de.binarynoise.util.okhttp.hasQueryParameter
import de.binarynoise.util.okhttp.hostAndPort
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.parseJsonObject
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@SSID(
    "Cotidiano-Gast",
    "FreeWiFi Wenkers am Markt",
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
    
    /**
     * check if the [response]'s `Success` [Boolean] is `true`
     * and if not throw [IllegalStateException]
     * 
     * @param checkReason if not successful, this lambda is called with the reason, 
     * so it can throw a different exception instead
     */
    fun checkApiSuccess(response: JsonObject, checkReason: (String) -> Unit = {}) {
        if (!response.getBoolean("Success")) {
            val reason = response.getString("Reason")
            checkReason(reason)
            throw IllegalStateException("checkApiSuccess no Success: $reason")
        }
    }
    
    fun solve(client: OkHttpClient, res: String, auth: String, redir: String?, extras: LiberatorExtras) {
        val helloJson = getHelloJson(client, res)
        
        checkApiSuccess(helloJson) { reason ->
            if (reason == "DeactivatedLocation") throw UnsupportedPortalException("Deactivated Location")
        }
        
        if (tryOrDefault(false) { helloJson.getJsonObject("Settings").getBoolean("IsCurrentlyOffBySchedule") }) {
            throw UnsupportedPortalException("Hotspot turned off by schedule")
        }
        
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
            SOCIALWAVE_SPLASH_API_BASE,
            "hello.json",
            mapOf(
                "query" to res,
            ),
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
                "user_agent" to extras.userAgent,
            )
        ).parseJsonObject()
        checkApiSuccess(loginJson)
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
                "language" to "en",
                "agree_terms" to "true",
                "agree_marketing" to "false",
                "user_agent" to extras.userAgent,
            )
        ).parseJsonObject()
        checkApiSuccess(registerEmailJson)
        performAuth(client, helloJson, registerEmailJson, auth, redir)
    }
    
    fun performAuth(
        client: OkHttpClient,
        helloJson: JsonObject,
        loginJson: JsonObject,
        auth: String,
        redir: String?,
    ): Response {
        return client.get(
            getAuthUrl(helloJson, loginJson, auth, redir),
            null,
        ).also { it.checkSuccess() }
    }
    
    /**
     * get an authentication url
     * 
     * @param loginJson a [JsonObject] containing `username` and `password` 
     * obtained after following the previous authentication flow
     * @param auth content of the query parameter from initial redirection
     * @param redir content of the query parameter from initial redirection, if present
     */
    fun getAuthUrl(helloJson: JsonObject, loginJson: JsonObject, auth: String, redir: String?): HttpUrl {
        val token = loginJson.getString("AuthenticationToken")
        var redir = redir
        
        if (!loginJson.has("Username")) {
            // authoriseOnRouterOpenwrt
            require(redir != null) { "no redir token" }
            return HttpUrl.Builder()
                .scheme("http")
                .hostAndPort(auth)
                .decodedPath("/nodogsplash_auth_tok/")
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
            .setQueryParameter("username", loginJson.getString("Username"))
            .setQueryParameter("password", token)
            .setQueryParameter("dst", redir)
            .build()
    }
}

@SSID(
    "FreeWiFi Burger King",
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
