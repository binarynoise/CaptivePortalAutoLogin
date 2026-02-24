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
            && with(response.requestUrl.pathSegments) {
            firstOrNull() == "portal" || (firstOrNull() == "portalpage" && lastOrNull() == "auth.html")
        }
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        var ssid = response.requestUrl.queryParameter("ssid") ?: error("no ssid")
        if (response.requestUrl.encodedPath == "/portal") ssid = Base64.encode(ssid.encodeToByteArray())
        val umac = response.requestUrl.queryParameter("umac") ?: error("no umac")
        val apmac = with(response.requestUrl) {
            queryParameter("ap-mac") ?: queryParameter("apmac")
        } ?: error("no apmac")
        val uaddress = response.requestUrl.queryParameter("uaddress") ?: error("no uaddress")
        val response = client.postForm(
            response.requestUrl, "/portalauth/login", mapOf(
                "agreed" to "1",
                "userName" to "~anonymous",
                "userPass" to "~anonymous",
                "ssid" to ssid,
                "umac" to umac,
                "apmac" to apmac,
                "uaddress" to uaddress,
                "authType" to "2",
            )
        ).parseJsonObject()
        check(response.getBoolean("success")) { "no success" }
        check(response.getBoolean("isEscape")) { "not escaped" }
    }
}
