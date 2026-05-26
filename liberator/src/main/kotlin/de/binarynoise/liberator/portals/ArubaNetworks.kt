package de.binarynoise.liberator.portals

import kotlinx.serialization.json.JsonObject
import de.binarynoise.liberator.LiberatorExtras
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.liberator.UnsupportedPortalException
import de.binarynoise.liberator.tryOrNull
import de.binarynoise.rhino.RhinoParser
import de.binarynoise.util.json.JsonObject
import de.binarynoise.util.json.getBoolean
import de.binarynoise.util.json.getJsonObject
import de.binarynoise.util.json.getString
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.followRedirects
import de.binarynoise.util.okhttp.hasQueryParameter
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import de.binarynoise.util.okhttp.toHttpUrl
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
@SSID(
    "H&M Free WiFi",
    "IKEA WiFi",
    "LEVIS GUEST",
)
object ArubaNetworks : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host.endsWith(".cloudguest.central.arubanetworks.com") //
            && response.requestUrl.pathSegments.firstOrNull() == "portal" //
            && response.requestUrl.pathSegments.lastOrNull() == "login" //
            && response.requestUrl.hasQueryParameter("capture")
    }
    
    fun getPortalLoginPageConfig(response: Response): JsonObject {
        val html = response.parseHtml()
        val script = html.getElementsByTag("script")
            .filter { !it.hasAttr("src") }
            .filter { !it.hasAttr("type") }
            .map { it.data() }
            .single { it.contains("portal_login_page_config") }
        val assignments = RhinoParser().parseAssignments(script)
        return JsonObject(assignments.getValue("portal_login_page_config"))
    }
    
    fun performArubaLogin(client: OkHttpClient, base: HttpUrl, username: String, password: String) {
        client.postForm(
            base, "/cgi-bin/login", mapOf(
                "cmd" to "authenticate",
                "user" to username,
                "password" to password,
            )
        ).checkSuccess()
    }
    
    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        val portal_login_page_config1 = getPortalLoginPageConfig(response)
        val pageConfig = portal_login_page_config1.getJsonObject("page")
        if (!pageConfig.getBoolean("require_accept_terms") || pageConfig.getBoolean("require_sponsor_approval")) throw UnsupportedPortalException()
        
        val capture = response.requestUrl.queryParameter("capture") ?: tryOrNull {
            val loginConfig = portal_login_page_config1.getJsonObject("capture")
            loginConfig.getString("capture")
        } ?: error("no capture")
        
        val response2 = client.postForm(
            response.requestUrl, null, mapOf(
                "accept_terms" to "on",
                "capture" to capture,
            )
        ).followRedirects(client)
        
        val portal_login_page_config2 = getPortalLoginPageConfig(response2)
        val network_login_config = portal_login_page_config2.getJsonObject("network_login")
        
        performArubaLogin(
            client,
            network_login_config.getString("action").toHttpUrl(response2.requestUrl),
            network_login_config.getString("username"),
            network_login_config.getString("password"),
        )
    }
}
