package de.binarynoise.liberator.portals

import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.liberator.UnsupportedPortalException
import de.binarynoise.liberator.portals.ArubaNetworks.performArubaLogin
import de.binarynoise.liberator.tryOrNull
import de.binarynoise.rhino.RhinoParser
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.decodedPath
import de.binarynoise.util.okhttp.followRedirects
import de.binarynoise.util.okhttp.getInput
import de.binarynoise.util.okhttp.hasQueryParameter
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import de.binarynoise.util.okhttp.submitOnlyForm
import de.binarynoise.util.okhttp.toHttpUrl
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.json.JSONObject

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
@Experimental
@SSID(
    "H&M Free WiFi",
)
object ArubaNetworks : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == "euw1.cloudguest.central.arubanetworks.com" //
            && response.requestUrl.pathSegments.firstOrNull() == "portal" //
            && response.requestUrl.pathSegments.lastOrNull() == "login" //
            && response.requestUrl.hasQueryParameter("capture")
    }
    
    fun getPortalLoginPageConfig(response: Response): JSONObject {
        val html = response.parseHtml()
        val script = html.getElementsByTag("script")
            .filter { !it.hasAttr("src") }
            .filter { !it.hasAttr("type") }
            .map { it.data() }
            .single { it.contains("portal_login_page_config") }
        val assignments = RhinoParser().parseAssignments(script)
        return JSONObject(assignments["portal_login_page_config"])
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
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val portal_login_page_config1 = getPortalLoginPageConfig(response)
        val pageConfig = portal_login_page_config1.getJSONObject("page")
        if (!pageConfig.getBoolean("require_accept_terms") || pageConfig.getBoolean("require_sponsor_approval")) throw UnsupportedPortalException()
        
        val capture = response.requestUrl.queryParameter("capture") ?: tryOrNull {
            val loginConfig = portal_login_page_config1.getJSONObject("capture")
            loginConfig.getString("capture")
        } ?: error("no capture")
        
        val response2 = client.postForm(
            response.requestUrl, null, mapOf(
                "accept_terms" to "on",
                "capture" to capture,
            )
        ).followRedirects(client)
        
        val portal_login_page_config2 = getPortalLoginPageConfig(response2)
        val network_login_config = portal_login_page_config2.getJSONObject("network_login")
        
        performArubaLogin(
            client,
            network_login_config.getString("action").toHttpUrl(response2.requestUrl),
            network_login_config.getString("username"),
            network_login_config.getString("password"),
        )
    }
}

@Experimental
@SSID(
    "Bershka-WiFi",
    "PULL&BEAR-FreeWiFi",
    "Stradivarius-WiFi",
    "Zara-WiFi",
)
object Inditex : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == "wifi.inditex.com"
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        if (response.requestUrl.decodedPath.endsWith("Employees.php")) throw UnsupportedPortalException("employee portal page")
        val response2 = response.submitOnlyForm(
            client, queryParameters = mapOf(
                "_browser" to "1",
            )
        ).followRedirects(client)
        val response3 = response2.submitOnlyForm(client)
        val html = response3.parseHtml()
        val form = html.forms().single()
        performArubaLogin(
            client,
            form.attr("action").toHttpUrl(response3.requestUrl),
            form.getInput("user"),
            form.getInput("password"),
        )
    }
}
