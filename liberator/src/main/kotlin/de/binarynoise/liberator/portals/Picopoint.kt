@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.PortalRedirector
import de.binarynoise.liberator.SSID
import de.binarynoise.liberator.portals.Picopoint.PICOPOINT_GATEKEEPER_DOMAIN
import de.binarynoise.liberator.tryOrDefault
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.lastPathSegment
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.parseJsonObject
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

object Picopoint : PortalLiberator {
    const val PICOPOINT_GATEKEEPER_DOMAIN = "gatekeeper2.picopoint.com"
    
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == PICOPOINT_GATEKEEPER_DOMAIN //
            && !response.isRedirect //
            && response.requestUrl.lastPathSegment == "options" //
            && !PicopointRedirector.canRedirect(response)
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val html = response.parseHtml()
        val form = html.getElementsByTag("form").find { it.attr("name") == "pseudo_auth_form" }
            ?: error("pseudo_auth_form not found")
        val action = form.attr("action")
        if (action.isEmpty()) error("form has no action")
        val inputs = form.getElementsByTag("input")
        val postParametersMap =
            inputs.filter { it.attr("name").isNotEmpty() }.associate { it.attr("name") to it.attr("value") }
        val fid = postParametersMap["__fid"]
        val session = client.get(response.requestUrl, "/gk/rest/session").parseJsonObject()
        val clientMac = session.getJSONObject("network").getString("client_mac")
        client.postForm(
            response.requestUrl, action, postParametersMap + mapOf(
                "custom_data_1" to clientMac,
                "$fid:_c" to "continue",
            )
        ).checkSuccess()
    }
}

@SSID("Shell Free WiFi")
object PicopointRedirector : PortalRedirector {
    fun getRedirectUrl(response: Response): HttpUrl {
        val html = response.parseHtml()
        val script = html.getElementsByTag("script").first()?.data() ?: error("no script")
        val assignmentString = "window.location=\""
        val assignmentIndex = script.indexOf(assignmentString)
        val assignmentEndIndex = script.indexOf('"', assignmentIndex + assignmentString.length)
        val locationUrl = script.substring(assignmentIndex + assignmentString.length, assignmentEndIndex).toHttpUrl()
        return locationUrl
    }
    
    override fun canRedirect(response: Response): Boolean {
        if (response.requestUrl.host != PICOPOINT_GATEKEEPER_DOMAIN) return false
        if (response.isRedirect) return false
        return tryOrDefault(false) {
            getRedirectUrl(response)
            return true
        }
    }
    
    override fun redirect(client: OkHttpClient, response: Response, cookies: Set<Cookie>): Response {
        val locationUrl = getRedirectUrl(response)
        return client.get(locationUrl, null)
    }
}
