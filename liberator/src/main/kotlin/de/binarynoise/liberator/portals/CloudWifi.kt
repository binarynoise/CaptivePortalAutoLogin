package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.rhino.RhinoParser
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.readText
import de.binarynoise.util.okhttp.requestUrl
import de.binarynoise.util.okhttp.toParameterMap
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.FormElement

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
@SSID("-free Milaneo Stuttgart")
object CloudWifi : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return "start.cloudwifi.de" == response.requestUrl.host
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val html1 = response.parseHtml()
        
        val forms = html1.getElementsByTag("form")
        check(forms.isNotEmpty()) { "no forms" }
        val easyLoginForm = forms.firstOrNull { form ->
            form.getElementsByTag("input").any { input ->
                input.attr("name") == "FX_loginType" && input.attr("value") == "Easy Login"
            }
        } as FormElement?
        check(easyLoginForm != null) { "easyLoginForm is null" }
        
        val response2 = client.postForm(
            response.requestUrl,
            easyLoginForm.attribute("action")?.value,
            easyLoginForm.toParameterMap(),
        )
        
        val html2 = response2.parseHtml()
        when {
            html2.getElementsByTag("script").any { it.data().contains("window.location.replace('") } -> {
                // TODO: proper javascript parsing
                val html2 = response2.readText()
                val start = html2.indexOf("window.location.replace('")
                val end = html2.indexOf("')", start)
                val url2 = html2.substring(start + "window.location.replace('".length, end)
                check(url2.isNotBlank()) { "no url2" }
                
                client.get(response.requestUrl, url2).checkSuccess()
            }
            html2.selectFirst("form[name=hotspotlogin]") != null -> {
                val hotspotLoginForm = html2.selectFirst("form[name=hotspotlogin]") as FormElement
                client.postForm(
                    response.requestUrl,
                    hotspotLoginForm.attribute("action")?.value,
                    hotspotLoginForm.toParameterMap(),
                ).checkSuccess()
            }
            else -> error("no secondary route matched")
        }
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
            .mapNotNull { it.toHttpUrlOrNull() }
            .any { url -> url.host == "start.cloudwifi.de" && url.pathSegments.any { it == "redirect" } }
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val html1 = response.parseHtml()
        val script1 = html1.getElementsByTag("body").single().getElementsByTag("script").single().data()
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
