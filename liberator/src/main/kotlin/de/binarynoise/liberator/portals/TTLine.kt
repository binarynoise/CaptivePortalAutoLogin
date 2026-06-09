@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator.portals

import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.LiberatorExtras
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.liberator.UnsupportedPortalException
import de.binarynoise.liberator.randomEmail
import de.binarynoise.util.json.getString
import de.binarynoise.util.okhttp.parseJsonObject
import de.binarynoise.util.okhttp.postJson
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Experimental
@SSID("TTLINE-TB-GUEST")
object TTLine : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == "tbguest.ttline.com" && response.requestUrl.port == 8000
    }
    
    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        val status = client.postJson(
            response.requestUrl,
            "/api/captiveportal/access/status/",
            mapOf("user" to "", "password" to ""),
        ).parseJsonObject()
        
        val clientState = status.getString("clientState")
        if (clientState == "AUTHORIZED") return
        check(clientState == "NOT_AUTHORIZED") {
            error("status is $clientState instead of NOT_AUTHORIZED")
        }
        val authType = status.getString("authType")
        if (authType != "none") throw UnsupportedPortalException("authType is $authType")
        
        val logon = client.postJson(
            response.requestUrl,
            "/api/captiveportal/access/logon/",
            mapOf(
                "terms_accepted" to "true",
                "lang" to "en",
                "user" to "",
                "email" to randomEmail("ttline.com"),
                "passenger_type" to "Passenger",
                "password" to "",
                "newsletter_accepted" to "true",
            ),
        ).parseJsonObject()
        
        val clientState2 = logon.getString("clientState")
        check(clientState2 == "AUTHORIZED") {
            error("clientState2 is $clientState2 instead of AUTHORIZED")
        }
    }
}
