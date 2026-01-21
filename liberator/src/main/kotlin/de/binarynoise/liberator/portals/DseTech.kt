package de.binarynoise.liberator.portals

import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.rhino.RhinoParser
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.firstPathSegment
import de.binarynoise.util.okhttp.followRedirects
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.getInput
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
@Experimental
@SSID("DeichmannGast")
object DseTech : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == "disclaimer.dse-tech.net"
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val html = response.parseHtml()
        val form = html.getElementsByTag("form")
        if (!form.hasAttr("action")) error("no action url")
        val action = form.attr("action")
        client.postForm(
            null, action, mapOf(
                "magic" to html.getInput("magic"),
                "username" to html.getInput("username"),
                "password" to html.getInput("password"),
                "submit" to html.getInput("submit"),
            )
        ).followRedirects(client).checkSuccess()
    }
}

object DseTechRedirect : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        if (DseTech.canSolve(response)) return false
        if (response.isRedirect) return false
        val html = response.parseHtml()
        val script = html.getElementsByTag("script").first()?.data() ?: return false
        val assignments = RhinoParser().parseAssignments(script)
        val redirectUrl = assignments["window.location"]?.toHttpUrl() ?: return false
        if (redirectUrl.firstPathSegment == "fgtauth") return true
        if (redirectUrl.host == "disclaimer.dse-tech.net") return true
        return false
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val html = response.parseHtml()
        val script = html.getElementsByTag("script").first()?.data() ?: error("no script")
        val assignments = RhinoParser().parseAssignments(script)
        val redirectUrl = assignments["window.location"]?.toHttpUrl() ?: error("no window.location")
        val response = client.get(redirectUrl, null)
        if (canSolve(response)) return solve(client, response, cookies)
        if (DseTech.canSolve(response)) return DseTech.solve(client, response, cookies)
        error("${this::class.java.name} couldn't find redirect successor")
    }
}
