package de.binarynoise.liberator.portals

import de.binarynoise.liberator.LiberatorExtras
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.liberator.randomEmail
import de.binarynoise.util.json.JsonObject
import de.binarynoise.util.json.getBoolean
import de.binarynoise.util.json.getInt
import de.binarynoise.util.json.getString
import de.binarynoise.util.json.has
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.firstPathSegment
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.parseJsonObject
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.postJson
import de.binarynoise.util.okhttp.readText
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

// Spain Airports, tested at Valencia
@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
@SSID("AIRPORT FREE WIFI AENA")
object AenaES : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return "freewifi.aena.es" == response.requestUrl.host
    }
    
    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        val freeWifiBase = "https://freewifi.aena.es/".toHttpUrl()
        val loginBase = "https://login.aena.es/".toHttpUrl()
        
        val mac = response.requestUrl.queryParameter("mac") ?: error("no mac")
        val email = randomEmail("aena.es")
        
        val uuid = response.requestUrl.firstPathSegment
        val gigyaApiKey: String = response.readText().let { text ->
            val pattern = "gigyaApiKey\\s*[:=]\\s*[\"']([^\"']+)[\"']".toRegex()
            pattern.find(text)?.groupValues?.get(1) ?: error("no gigyaApiKey")
        }
        
        val gigyaApiConstants = mapOf(
            "apiKey" to gigyaApiKey,
        )
        
        // required to get certain cookies for later requests
        client.get(
            null,
            "https://login.aena.es/accounts.webSdkBootstrap",
            gigyaApiConstants,
        ).checkSuccess()
        
        // checks if the email is available
        // we could do multiple tries here, but uuid collision is rare anyways
        val isAvailableLoginIDResponse = client.postForm(
            null,
            "https://login.aena.es/accounts.isAvailableLoginID",
            mapOf(
                "loginID" to email,
            ) + gigyaApiConstants,
        )
        val isAvailableLoginIDJson = isAvailableLoginIDResponse.parseJsonObject()
        check(isAvailableLoginIDJson.has("isAvailable") && isAvailableLoginIDJson.getBoolean("isAvailable")) { "isAvailable not true" }
        
        val initRegistrationResponse = client.get(
            null,
            "https://login.aena.es/accounts.initRegistration",
            mapOf(
                "isLite" to "true"
            ) + gigyaApiConstants,
        )
        val initRegistrationJson = initRegistrationResponse.parseJsonObject()
        val regToken = initRegistrationJson.getString("regToken")
        
        val setAccountInfoResponse = client.postForm(
            loginBase,
            "/accounts.setAccountInfo",
            mapOf(
                "source" to "showScreenSet",
                "regToken" to regToken,
                "profile" to JsonObject(mapOf("email" to email)).toString(),
            ) + gigyaApiConstants,
        )
        val setAccountInfoJson = setAccountInfoResponse.parseJsonObject()
        check(setAccountInfoJson.getInt("statusCode") == 200)
        
        val verifyAccountResponse = client.postJson(
            freeWifiBase,
            "/api/portal/$uuid/verifyAccount",
            mapOf("email" to email),
        )
        val verifyAccountJson = verifyAccountResponse.parseJsonObject()
        check(verifyAccountJson.getInt("statusCode") == 200)
        
        val isVerified = true // normally false, but works to set true so why not
        
        val registerResponse = client.postJson(
            freeWifiBase,
            "/api/portal/$uuid/register",
            mapOf(
                "email" to email,
                "user_id" to setAccountInfoJson.getString("UID"),
                "social_network" to "guest",
                "client_mac" to mac,
                "is_verified" to isVerified,
            ),
        )
        val registerJson = registerResponse.parseJsonObject()
        check(registerJson.getInt("statusCode") == 200)
        
        val authorizeResponse = client.postJson(
            freeWifiBase,
            "/api/network/authorize",
            mapOf(
                "device_mac" to mac,
                "role_name" to "guest",
                "is_verified" to isVerified,
                "is_emergency" to false,
            ),
        )
        val authorizeJson = authorizeResponse.parseJsonObject()
        check(authorizeJson.getInt("statusCode") == 200)
        
        val routerLoginResponse = client.postForm(
            null,
            "https://captiveportal-login.wifiplex.com/cgi-bin/login",
            mapOf(
                "username" to mac,
                "password" to mac,
            ),
        )
        routerLoginResponse.checkSuccess()
    }
}
