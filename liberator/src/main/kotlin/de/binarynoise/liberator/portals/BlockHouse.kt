package de.binarynoise.liberator.portals

import java.util.*
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.rhino.RhinoParser
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Response

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
@SSID("BLOCK HOUSE WIFI")
object BlockHouse : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == "wlan.block-house.de"
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        response.checkSuccess()
        
        val hs_server = response.requestUrl.queryParameter("hs_server") ?: error("no hs_server")
        val Qv = response.requestUrl.queryParameter("Qv") ?: error("no Qv")
        
        val scriptNode = response.parseHtml()
            .getElementsByTag("script")
            .find { listOf("postToUrl", "port", "hs_server").all { str -> it.data().contains(str) } }
            ?: error("no script found")
        val assignments = RhinoParser().parseAssignments(scriptNode.data())
        val port = assignments["port"] ?: error("no port")
        val postToUrl = assignments["postToUrl"] ?: error("no postToUrl")
        val hs_server_url = hs_server.toHttpUrlOrNull() ?: error("failed to parse hs_server")
        val baseUrl =
            "${hs_server_url.scheme}://${hs_server_url.host}:$port${hs_server_url.encodedPath}".toHttpUrlOrNull()
                ?: error("failed to parse baseUrl")
        client.postForm(
            baseUrl, postToUrl, mapOf(
                "f_agree" to "",
                "submit" to "Ich stimme zu | I agree",
                "f_Qv" to Qv,
                "f_hs_server" to hs_server,
                "f_curr_time" to Date().time.toString(),
            )
        ).checkSuccess()
    }
}
