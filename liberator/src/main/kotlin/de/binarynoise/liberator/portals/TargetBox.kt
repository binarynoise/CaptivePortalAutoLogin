@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator.portals

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.LiberatorExtras
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.json.getBoolean
import de.binarynoise.util.json.getString
import de.binarynoise.util.okhttp.followRedirects
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.parseJsonObject
import de.binarynoise.util.okhttp.postJson
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Experimental
@SSID(
    "Zalando Free Wifi",
)
object TargetBox : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == "wifi.targetbox.de"
    }
    
    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        val skipResponse = client.postJson(
            response.requestUrl, "/wifidog/skip", buildJsonObject {
                put("gwAddress", (response.requestUrl.queryParameter("gw_address") ?: error("no gw_address")))
                put("gwPort", (response.requestUrl.queryParameter("gw_port") ?: error("no gw_port")))
                put("gwId", (response.requestUrl.queryParameter("gw_id") ?: error("no gw_id")))
                put("mac", (response.requestUrl.queryParameter("mac") ?: error("no mac")))
            }.toString()
        ).parseJsonObject()
        check(skipResponse.getBoolean("success")) { "no success" }
        val redirectUrl = skipResponse.getString("redirectUrl")
        client.get(response.requestUrl, redirectUrl).followRedirects(client)
    }
}
