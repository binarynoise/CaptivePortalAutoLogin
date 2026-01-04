package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.followRedirects
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.getInput
import de.binarynoise.util.okhttp.getLocation
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
object NetworkAuth : PortalLiberator {
    override fun canSolve(locationUrl: HttpUrl): Boolean {
        return locationUrl.host.endsWith("network-auth.com")
    }
    
    override fun solve(locationUrl: HttpUrl, client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val response1 = client.get(locationUrl, null)
        val location1 = response1.getLocation() ?: error("no location1")
        
        val response2 = client.get(locationUrl, location1)
        val html2 = response2.parseHtml()
        val `csrf-token` = html2.getElementsByAttributeValue("name", "csrf-token").single().attr("content")
        val `csrf-param` = html2.getElementsByAttributeValue("name", "csrf-param").single().attr("content")
        
        val form_action = html2.id("splash-form").attr("action")
        val base_grant_url = html2.getInput("base_grant_url")
        val success_url = html2.getInput("success_url")
        
        val response3 = client.postForm(
            response2.requestUrl, form_action,
            mapOf(
                `csrf-param` to `csrf-token`,
                "success_url" to success_url,
                "base_grant_url" to base_grant_url,
            ),
        )
        response3.followRedirects(client).checkSuccess()
    }
}
