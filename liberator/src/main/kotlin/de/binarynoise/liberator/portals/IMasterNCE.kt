@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator.portals

import kotlin.io.encoding.Base64
import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.parseJsonObject
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.Response

@Experimental
@SSID("Cosmo-Gast")
object IMasterNCE : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == "device.imaster-nce.de" //
            && response.requestUrl.port == 19008 //
            && response.requestUrl.pathSegments.firstOrNull() == "portal"
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val ssid = Base64.encode(response.requestUrl.queryParameter("ssid")?.encodeToByteArray() ?: error("no ssid"))
        val response = client.postForm(
            response.requestUrl, "/portalauth/login", mapOf(
                "agreed" to "1",
                "userName" to "~anonymous",
                "userPass" to "~anonymous",
                "ssid" to ssid,
                "umac" to response.requestUrl.queryParameter("umac"),
                "apmac" to response.requestUrl.queryParameter("apmac"),
                "uaddress" to response.requestUrl.queryParameter("uaddress"),
                "authType" to "2",
            )
        ).parseJsonObject()
        check(response.getBoolean("success")) { "no success" }
        check(response.getBoolean("isEscape")) { "not escaped" }
    }
}
