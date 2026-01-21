package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.rhino.RhinoParser
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.getInput
import de.binarynoise.util.okhttp.getLocation
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.readText
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
@SSID("-free Milaneo Stuttgart")
object CloudWifi : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return "start.cloudwifi.de" == response.requestUrl.host
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val html1 = response.parseHtml()
        
        val response2 = client.postForm(
            response.requestUrl, null, mapOf(
                "FX_lang" to html1.getInput("FX_lang"),
                "FX_loginTemplate" to html1.getInput("FX_loginTemplate"),
                "FX_loginType" to html1.getInput("FX_loginType"),
                "FX_password" to html1.getInput("FX_password"),
                "FX_username" to html1.getInput("FX_username"),
                "called" to html1.getInput("called"),
                "cbQpC" to html1.getInput("cbQpC"),
                "challenge" to html1.getInput("challenge"),
                "ip" to html1.getInput("ip"),
                "mac" to html1.getInput("mac"),
                "nasid" to html1.getInput("nasid"),
                "sessionid" to html1.getInput("sessionid"),
                "uamip" to html1.getInput("uamip"),
                "uamport" to html1.getInput("uamport"),
                "userurl" to html1.getInput("userurl"),
            )
        )
        
        // TODO: proper javascript parsing
        val html2 = response2.readText()
        val start = html2.indexOf("window.location.replace('")
        val end = html2.indexOf("')", start)
        val url2 = html2.substring(start + "window.location.replace('".length, end)
        check(url2.isNotBlank()) { "no url2" }
        
        val response3 = client.get(response.requestUrl, url2)
        val url3 = response3.getLocation() ?: error("no url3")
        val res = url3.toHttpUrl().queryParameter("res")
        check(res == "success") { "res=$res" }
        client.get(response.requestUrl, url3).checkSuccess()
    }
}

@SSID(
    "-free Koenigsbau Passagen",
    "-Free -Thier Galerie Dortmund",
    "Radiologie-GAST",
)
object CloudWifiRedirect : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        if (CloudWifi.canSolve(response)) return false
        return response.parseHtml()
            .getElementsByTag("script")
            .map { it.attr("src") }
            .filter { it.isNotEmpty() }
            .map { it.toHttpUrl() }
            .any { url -> url.host == "start.cloudwifi.de" && url.pathSegments.any { it == "redirect" } }
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val html1 = response.parseHtml()
        val script1 = html1.getElementsByTag("body").single().getElementsByTag("script").single().wholeText()
        val assignments = RhinoParser().parseAssignments(script1)
        
        val deviceMac = assignments["FX_redirect.0"] ?: error("no deviceMac")
        val userMac = assignments["FX_redirect.1"] ?: error("no userMac")
        val loginUrl = assignments["FX_redirect.2"] ?: error("no loginUrl")
//        val redirectUrl = assignments["FX_redirect.3"]
//        val error = assignments["FX_redirect.4"]
//        val success = assignments["FX_redirect.5"]
        
        val response2 = client.get(
            null, "https://start.cloudwifi.de", queryParameters = mapOf(
                "ros_mac" to deviceMac,
                "ros_user_mac" to userMac,
                "ros_login_url" to loginUrl,
            )
        )
        CloudWifi.solve(client, response2, cookies)
    }
}
