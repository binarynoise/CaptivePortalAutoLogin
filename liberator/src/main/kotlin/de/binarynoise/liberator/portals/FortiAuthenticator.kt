@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator.portals

import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.PortalRedirector
import de.binarynoise.liberator.SSID
import de.binarynoise.liberator.portals.FortiAuthenticator.isFortiAuthenticatorUrl
import de.binarynoise.rhino.RhinoParser
import de.binarynoise.util.okhttp.firstPathSegment
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import de.binarynoise.util.okhttp.toParameterMap
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.FormElement

/**
 * Identifies this [PortalLiberator] as a sub portal of [FortiAuthenticator],
 * which makes it eligible for solving after a [FortiAuthenticatorRedirect].
 */
@Target(AnnotationTarget.CLASS)
annotation class FortiAuthenticatorSubPortal

@Experimental
@SSID(
    "ITS - FREEWIFI",
    "Douglas Guest",
)
object FortiAuthenticator : PortalLiberator {
    fun HttpUrl.isFortiAuthenticatorUrl(): Boolean {
        return this.port == 1000 && this.firstPathSegment == "fgtauth"
    }
    
    override fun canSolve(response: Response): Boolean {
        if (!response.requestUrl.isFortiAuthenticatorUrl()) return false
        if (response.isRedirect) return false
        return true
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val html = response.parseHtml()
        val form = html.getElementsByTag("form").single() as FormElement
        val parameters = form.toParameterMap()
        client.postForm(
            response.requestUrl, "/", parameters + mapOf(
                "answer" to "1",
            )
        )
    }
}

@Experimental
object FortiAuthenticatorRedirect : PortalRedirector {
    override fun canRedirect(response: Response): Boolean {
        if (response.isRedirect) return false
        val html = response.parseHtml()
        val script = html.getElementsByTag("script").first()?.data() ?: return false
        val assignments = RhinoParser().parseAssignments(script)
        val redirectUrl = assignments["window.location"]?.toHttpUrl() ?: return false
        return redirectUrl.isFortiAuthenticatorUrl()
    }
    
    override fun redirect(client: OkHttpClient, response: Response, cookies: Set<Cookie>): Response {
        val html = response.parseHtml()
        val script = html.getElementsByTag("script").first()?.data() ?: error("no script")
        val assignments = RhinoParser().parseAssignments(script)
        val redirectUrl = assignments["window.location"] ?: error("no window.location")
        return client.get(response.requestUrl, redirectUrl)
    }
}
