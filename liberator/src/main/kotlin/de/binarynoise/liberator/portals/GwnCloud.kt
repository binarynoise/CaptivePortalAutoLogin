@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator.portals

import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.LiberatorExtras
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.liberator.UnsupportedPortalException
import de.binarynoise.util.json.getInt
import de.binarynoise.util.json.getJsonObject
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.parseJsonObject
import de.binarynoise.util.okhttp.postJson
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Experimental
@SSID("3SonsGuest")
object GwnCloud : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == "cwp.gwnportal.cloud" && response.requestUrl.port == 8080
    }
    
    val unsupportedLoginKeys = setOf(
        "password",
        "radius",
        "voucher",
        "facebook",
        "twitter",
        "google",
    )
    
    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        val style = client.get(response.requestUrl, "style.json").parseJsonObject()
        val loginMethods = style.getJsonObject("login").keys
        if (loginMethods.minus(unsupportedLoginKeys).isEmpty()) {
            throw UnsupportedPortalException("login methods unsupported: ${loginMethods.joinToString()}}")
        }
        if (!loginMethods.contains("free")) {
            throw IllegalStateException(
                "login methods not implemented: ${loginMethods.minus(unsupportedLoginKeys).joinToString()}"
            )
        }
        
        val authResponse = client.postJson(
            response.requestUrl, "/GsUserAuth.cgi", mapOf(
                "GsUserAuthMethod" to "0",
                "GsUserRealReqUrl" to "http://www.baidu.com", // defined in their JS independent of actual initial request Url
            )
        ).parseJsonObject()
        val result = authResponse.getInt("result")
        if (result != 1) throw IllegalStateException("auth result not 1: $result")
    }
}
