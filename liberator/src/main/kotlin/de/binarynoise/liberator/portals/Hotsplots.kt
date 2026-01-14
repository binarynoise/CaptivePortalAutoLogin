package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.followRedirects
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.getInput
import de.binarynoise.util.okhttp.hasInput
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@SSID("WIFI@DB")
@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
object Hotsplots : PortalLiberator {
    override fun canSolve(locationUrl: HttpUrl, response: Response): Boolean {
        return "www.hotsplots.de" == locationUrl.host && "/auth/login.php" == locationUrl.encodedPath
    }
    
    override fun solve(locationUrl: HttpUrl, client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val response1 = client.get(locationUrl, null)
        val html1 = response1.parseHtml()
        
        client.postForm(
            null, "https://www.hotsplots.de/auth/login.php",
            buildMap {
                if (html1.hasInput("hotsplots-colibri-terms")) {
                    set("hotsplots-colibri-terms", "on")
                } else {
                    set("termsOK", "on")
                    set("termsChkbx", "on")
                    set("haveTerms", html1.getInput("haveTerms"))
                }
                
                set("challenge", html1.getInput("challenge"))
                set("uamip", html1.getInput("uamip"))
                set("uamport", html1.getInput("uamport"))
                set("userurl", html1.getInput("userurl"))
                set("myLogin", html1.getInput("myLogin"))
                set("ll", html1.getInput("ll"))
                set("nasid", html1.getInput("nasid"))
                set("custom", html1.getInput("custom"))
            },
        ).followRedirects(client)
    }
}

object HotsplotsAuth : PortalLiberator {
    override fun canSolve(locationUrl: HttpUrl, response: Response): Boolean {
        return "auth.hotsplots.de" == locationUrl.host && "/login" == locationUrl.encodedPath
    }
    
    override fun solve(locationUrl: HttpUrl, client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val response1 = client.get(locationUrl, null)
        val html1 = response1.parseHtml()
        
        val login_status_form = html1.getElementById("login_status_form") ?: error("no login_status_form")
        val inputs = login_status_form.children().associate { element ->
            element.attr("name") to element.attr("value")
        }.filter { (name, _) -> name.isNotBlank() }
        check(inputs.isNotEmpty()) { "no inputs" }
        
        client.postForm(
            response1.requestUrl, null,
            inputs,
        ).followRedirects(client)
    }
}
