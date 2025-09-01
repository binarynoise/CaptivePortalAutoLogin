package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.getInput
import de.binarynoise.util.okhttp.getLocation
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.readText
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
@SSID("-free Milaneo Stuttgart")
object CloudWifiDE : PortalLiberator {
    override fun canSolve(locationUrl: HttpUrl): Boolean {
        return "start.cloudwifi.de" == locationUrl.host
    }
    
    override fun solve(locationUrl: HttpUrl, client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val response1 = client.get(response.requestUrl, response.getLocation())
        val html1 = response1.parseHtml()
        
        val response2 = client.postForm(
            locationUrl, null, mapOf(
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
        
        val html2 = response2.readText()
        val src = html2
        val start = src.indexOf("window.location.replace('")
        val end = src.indexOf("')", start)
        val url2 = src.substring(start + "window.location.replace('".length, end)
        // http://192.168.182.1:3990/logon?username=...%3D&password=...&userurl=http%3A%2F%2Fam-i-captured.binarynoise.de%2F
        check(url2.isNotBlank()) { "no url2" }
        
        val response3 = client.get(response.requestUrl, url2)
        val url3 = response3.getLocation() ?: error("no url3")
        val res = url3.toHttpUrl().queryParameter("res")
        check(res == "success") { "res=$res" }
        client.get(response.requestUrl, url3).checkSuccess()
    }
}
