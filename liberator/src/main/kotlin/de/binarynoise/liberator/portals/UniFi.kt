package de.binarynoise.liberator.portals

import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.liberator.UnsupportedPortalException
import de.binarynoise.liberator.tryOrDefault
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.followRedirects
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.readText
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.Response
import org.json.JSONObject

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
@Experimental
@SSID(
    "L’Osteria",
    "Henri Willig GUEST",
)
object UniFi : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.encodedPath.startsWith("/guest/s/")
    }
    
    fun Response.parseUniFiBrokenJsonObject(skipStatusCheck: Boolean = false): JSONObject {
        val text = this.readText(skipStatusCheck).substringAfter('{', "")
        if (text.isEmpty()) throw IllegalArgumentException("empty JSON")
        return JSONObject("{$text")
    }
    
    fun JSONObject.getUniFiDataObject(): JSONObject {
        return this.getJSONArray("data").getJSONObject(0)
    }
    
    fun JSONObject.isUniFiMetaOk(): Boolean {
        return this.getJSONObject("meta").getString("rc") == "ok"
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val hotspotconfig = client.get(response.requestUrl, "hotspotconfig").parseUniFiBrokenJsonObject()
        check(hotspotconfig.isUniFiMetaOk()) { "UniFi hotspotconfig responded not ok" }
        val config = hotspotconfig.getUniFiDataObject()
        if (config.getString("auth") != "none") throw UnsupportedPortalException("auth is not none")
        if (listOf(
                "facebook_enabled",
                "password_enabled",
                "payment_enabled",
                "radius_enabled",
                "voucher_enabled",
                "wechat_enabled",
            ).any { tryOrDefault(false) { config.getBoolean(it) } }
        ) throw UnsupportedPortalException("unsupported auth method")
        val loginResponse = client.postForm(
            response.requestUrl,
            "login",
            mapOf(),
            preConnectSetup = {
                header("Referer", response.requestUrl.toString())
            },
        )
        check(!loginResponse.isRedirect) { "login redirected" }
        loginResponse.checkSuccess()
        val loginJson = loginResponse.parseUniFiBrokenJsonObject()
        check(loginJson.isUniFiMetaOk()) { "UniFi loginResponse responded not ok" }
        val loginDataJson = loginJson.getUniFiDataObject()
        check(loginDataJson.getBoolean("authorized")) { "UniFi loginResponse responded not authorized" }
        
        val redirect_url = loginDataJson.getString("redirect_url") ?: error("no redirect_url")
        client.get(response.requestUrl, redirect_url).followRedirects(client)
    }
}
