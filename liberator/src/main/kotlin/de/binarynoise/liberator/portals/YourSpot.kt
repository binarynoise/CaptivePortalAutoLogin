package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.followRedirects
import de.binarynoise.util.okhttp.getInput
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
@SSID("EDEKA free-wifi")
object YourSpot : PortalLiberator {
    override fun canSolve(locationUrl: HttpUrl, response: Response): Boolean {
        return "cp1.your-spot.de" == locationUrl.host
    }
    
    override fun solve(locationUrl: HttpUrl, client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        
        val html1 = response.parseHtml()
        val `csrf-token` = html1.getElementsByAttributeValue("name", "csrf-token").single().attr("content")
        val `csrf-param` = html1.getElementsByAttributeValue("name", "csrf-param").single().attr("content")
        
        val form_action = html1.id("splash-form").attr("action")
        val base_grant_url = html1.getInput("base_grant_url")
        val success_url = html1.getInput("success_url")
        
        val response2 = client.postForm(
            response.requestUrl, form_action,
            mapOf(
                `csrf-param` to `csrf-token`,
                "success_url" to success_url,
                "base_grant_url" to base_grant_url,
            ),
        )
        response2.followRedirects(client).checkSuccess()
    }
}
