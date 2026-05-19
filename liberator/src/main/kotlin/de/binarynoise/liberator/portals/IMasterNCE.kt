@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator.portals

import kotlin.io.encoding.Base64
import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.LiberatorExtras
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.liberator.encode
import de.binarynoise.util.json.getBoolean
import de.binarynoise.util.json.getString
import de.binarynoise.util.okhttp.parseJsonObject
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
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
    
    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        var b64ssid = response.requestUrl.queryParameter("ssid") ?: error("no ssid")
        // ssid has to be base64 encoded
        // when obtaining ssid from /portal it is in plaintext
        if (response.requestUrl.encodedPath == "/portal") b64ssid = Base64.encode(b64ssid)
        val umac = response.requestUrl.queryParameter("umac") ?: error("no umac")
        val apmac = with(response.requestUrl) {
            queryParameter("ap-mac") ?: queryParameter("apmac")
        } ?: error("no apmac")
        val uaddress = response.requestUrl.queryParameter("uaddress") ?: error("no uaddress")
        val loginResponse = client.postForm(
            response.requestUrl, "/portalauth/login", mapOf(
                "agreed" to "1",
                "userName" to "~anonymous",
                "userPass" to "~anonymous",
                "ssid" to b64ssid,
                "umac" to umac,
                "apmac" to apmac,
                "uaddress" to uaddress,
                "authType" to "2",
            )
        ).parseJsonObject()
        check(loginResponse.getBoolean("success")) { "no login success" }
        val syncPortalResponse = client.postForm(
            response.requestUrl,
            "/portalauth/syncPortalResult",
            mapOf(),
        ).parseJsonObject()
        check(syncPortalResponse.getBoolean("success")) { "no syncPortalResult success" }
        check(syncPortalResponse.getString("message") == "true") { "syncPortalResult message not \"true\"" }
    }
}
