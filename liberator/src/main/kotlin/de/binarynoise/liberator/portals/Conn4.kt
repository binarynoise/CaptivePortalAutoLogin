package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.PortalLiberatorConfig
import de.binarynoise.util.okhttp.firstPathSegment
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.getLocation
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.readText
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.json.JSONObject

// Kaufland, Rewe
@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
object Conn4 : PortalLiberator {
    override fun canSolve(locationUrl: HttpUrl, client: OkHttpClient): Boolean {
        return PortalLiberatorConfig.debug && locationUrl.host.endsWith(".conn4.com") && locationUrl.firstPathSegment == "ident"
    }
    
    override fun solve(locationUrl: HttpUrl, client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val site_id = locationUrl.queryParameter("site_id") ?: error("no site_id")
        val response1 = client.get(locationUrl, null)
        val location1 = response1.getLocation() // https://portal-eu-ffm01.conn4.com/#
        
        val response2 = client.get(response1.requestUrl, location1)
        val token = response2.readText().let { html ->
            // find and parse
            // conn4.hotspot.wbsToken = { "token": "...", "urls": { "grant_url": null, "continue_url": null } };
            val wbsTokenIndex = html.indexOf("hotspot.wbsToken")
            check(wbsTokenIndex != -1) { "hotspot.wbsToken not found" }
            val jsObjectStart = html.indexOf("{", wbsTokenIndex)
            check(jsObjectStart != -1) { "jsObjectStart not found" }
            val jsObjectEnd = html.indexOf(";", jsObjectStart)
            check(jsObjectEnd != -1) { "jsObjectEnd not found" }
            
            val jsObject = JSONObject(html.substring(jsObjectStart, jsObjectEnd))
            jsObject.getString("token")
        }
        
        val response3 = client.postForm(
            locationUrl, "/wbs/api/v1/create-session/",
            mapOf(
                "authorization" to "token=$token",
                "locale" to "de_DE",
                "locationId" to site_id,
                "session_id" to "",
                "with-tariffs" to "1",
            ),
        )
        val session = JSONObject(response3.readText()).getString("session") ?: error("no session")
        
        val response4 = client.postForm(
            locationUrl, "/wbs/api/v1/register/free/",
            mapOf(
                "authorization" to "session:$session",
                "registration_type" to "terms-only",
                "registration[terms]" to "1",
            ),
        )
        check(JSONObject(response4.readText()).getBoolean("ok"))
    }
}
