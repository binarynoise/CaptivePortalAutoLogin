package de.binarynoise.liberator.portals

import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.liberator.asIterable
import de.binarynoise.liberator.tryOrNull
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.parseJsonObject
import de.binarynoise.util.okhttp.postJson
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.Response
import org.json.JSONObject

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
@Experimental
@SSID(
    "@VodafoneWifi",
    "Kunstmuseum WLAN",
    "_free-wifi-stuttgart_(official)",
)
object VodafoneHotspot : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == "hotspot.vodafone.de"
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val session = client.get(response.requestUrl, "/api/v4/session").parseJsonObject()
        val sessionToken = session.getString("session")
        val landingPageElements = session.getJSONArray("landingPageElements").asIterable()
        val landingPageLoginProfileId = landingPageElements.filterIsInstance<JSONObject>()
            .first { tryOrNull { it.getString("type") } == "loginProfile" }
            .getString("value")
        val loginProfiles = session.getJSONObject("loginProfiles")
        val landingPageLoginProfile = loginProfiles.getJSONObject(landingPageLoginProfileId)
        val accessType = landingPageLoginProfile.getString("accessType")
        val result = client.postJson(
            response.requestUrl, "/api/v4/login", JSONObject(
                mapOf(
                    "loginProfile" to landingPageLoginProfileId.toInt(),
                    "session" to sessionToken,
                    "accessType" to accessType,
                )
            ).toString(), mapOf(
                "sessionID" to sessionToken,
            )
        ).parseJsonObject()
        check(result.getBoolean("success")) { error("success is not true") }
    }
}
