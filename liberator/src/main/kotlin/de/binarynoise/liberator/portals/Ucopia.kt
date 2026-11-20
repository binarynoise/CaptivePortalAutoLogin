@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator.portals

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import de.binarynoise.liberator.LiberatorExtras
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.liberator.UnsupportedPortalException
import de.binarynoise.util.json.getJsonObject
import de.binarynoise.util.json.getOptBoolean
import de.binarynoise.util.json.getOptJsonObject
import de.binarynoise.util.json.getString
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.parseJsonObject
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@SSID(
    "Commerzbank-Wifi",
    "Hofmeister-Gast",
    "Messe-for free",
)
object Ucopia : PortalLiberator {
    fun getUcopiaApiUrl(baseUrl: HttpUrl): HttpUrl = baseUrl.newBuilder("/portal_api.php")!!.build()
    fun JsonObject.getUcoptiaSupportedAuthModes(): List<String> {
        return this.entries.filter { it.value.jsonPrimitive.booleanOrNull ?: false }.map { it.key }.toList()
    }
    
    override fun canSolve(client: OkHttpClient, response: Response, extras: LiberatorExtras): Boolean {
        if (response.isRedirect) return false
        if (response.requestUrl.pathSegments.getOrNull(1) != "portal") return false
        if (response.requestUrl.pathSegments.getOrNull(0)?.toIntOrNull() == null) return false
        try {
            // verify the Ucopia API is reachable
            client.postForm(
                getUcopiaApiUrl(response.requestUrl),
                null,
                mapOf(
                    "action" to "init",
                ),
            ).checkSuccess()
        } catch (_: Exception) {
            return false
        }
        return true
    }
    
    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        val apiUrl = getUcopiaApiUrl(response.requestUrl)
        val initResponse = client.postForm(
            apiUrl,
            null,
            mapOf(
                "action" to "init",
            ),
        ).parseJsonObject()
        val supportedAuthModes = initResponse.getJsonObject("auth_modes").getUcoptiaSupportedAuthModes()
        if ("one" !in supportedAuthModes) {
            if ("standard" in supportedAuthModes) throw UnsupportedPortalException("Login Required")
            throw IllegalArgumentException("unknown auth_mode")
        }
        val logonInfo = initResponse.getJsonObject("logon")
        if (logonInfo.getOptJsonObject("facebook")?.getOptBoolean("need_like") ?: false) {
            throw UnsupportedPortalException("requires facebook like")
        }
        if (logonInfo.getOptJsonObject("twitter")?.getOptBoolean("need_follow") ?: false) {
            throw UnsupportedPortalException("requires twitter follow")
        }
        
        val subscribeResponse = client.postForm(
            apiUrl,
            null,
            mapOf(
                "action" to "subscribe",
                "type" to "one",
                "connect_policy_accept" to "true",
            ),
        ).parseJsonObject()
        val subscribeInfo = subscribeResponse.getJsonObject("info").getJsonObject("subscribe")
        val authenticateResponse = client.postForm(
            apiUrl,
            null,
            mapOf(
                "action" to "authenticate",
                "login" to subscribeInfo.getString("login"),
                "password" to subscribeInfo.getString("password"),
                "policy_accept" to "true",
            ),
        ).parseJsonObject()
        
        if (authenticateResponse.getOptJsonObject("error")?.getString("code") == "error_logon_volume-quota-reached-detail") {
            // TODO: change MAC address
            throw UnsupportedPortalException("quota reached")
        }
    }
}
