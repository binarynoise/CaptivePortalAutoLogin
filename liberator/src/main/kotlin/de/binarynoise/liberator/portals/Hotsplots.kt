package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.followRedirects
import de.binarynoise.util.okhttp.getInput
import de.binarynoise.util.okhttp.hasInput
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import de.binarynoise.util.okhttp.toParameterMap
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.FormElement

@SSID("WIFI@DB")
@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
object Hotsplots : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return "www.hotsplots.de" == response.requestUrl.host && "/auth/login.php" == response.requestUrl.encodedPath
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val html1 = response.parseHtml()
        
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

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
object HotsplotsAuth : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return "auth.hotsplots.de" == response.requestUrl.host && "/login" == response.requestUrl.encodedPath
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val html1 = response.parseHtml()
        
        val login_status_form = html1.getElementsByAttributeValue("name", "login_status_form").singleOrNull()
            ?: error("no login_status_form")
        login_status_form as? FormElement ?: error("login_status_form is not a form")
        val inputs = login_status_form.toParameterMap()
        check(inputs.isNotEmpty()) { "no inputs" }
        
        client.postForm(
            response.requestUrl, null,
            inputs,
        ).followRedirects(client)
    }
}
