package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.followRedirects
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.getInput
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.readText
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.Response

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
@SSID("DSW21-WLAN", "Hotspot Westfalenhallen")
object Dokom21Hotspot : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return "hotspot.dokom21.de" == response.requestUrl.host && "/([^/]+)/Index".toRegex().matches(response.requestUrl.encodedPath)
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val networkType = response.requestUrl.pathSegments.first()
        response.checkSuccess()
        
        val response2 = client.get(response.requestUrl, "/$networkType/Login")
        val html2 = response2.parseHtml()
        
        val response3 = client.postForm(
            response2.requestUrl,
            null,
            mapOf(
                "__EVENTTARGET" to html2.getInput("__EVENTTARGET"), // leer
                "__EVENTARGUMENT" to html2.getInput("__EVENTARGUMENT"), // leer
                "__VIEWSTATE" to html2.getInput("__VIEWSTATE"),
                "__LASTFOCUS" to html2.getInput("__LASTFOCUS"), // leer
                "__VIEWSTATEGENERATOR" to html2.getInput("__VIEWSTATEGENERATOR"),
                "__EVENTVALIDATION" to html2.getInput("__EVENTVALIDATION"),
                
                "ctl00\$GenericContent\$AgbAccepted" to "on",
                "ctl00\$GenericContent\$PrivacyPolicyAccepted" to "on",
                "ctl00\$LanguageSelect" to "DE",
                "ctl00\$GenericContent\$SubmitLogin" to "Einloggen",
            ),
        )
        check(response3.followRedirects(client).readText().contains("Login erfolgreich.")) {
            "response does not contain success"
        }
    }
}
