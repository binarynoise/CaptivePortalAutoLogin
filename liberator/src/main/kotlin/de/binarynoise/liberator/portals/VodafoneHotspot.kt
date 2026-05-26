package de.binarynoise.liberator.portals

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import de.binarynoise.liberator.LiberatorExtras
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.liberator.tryOrNull
import de.binarynoise.util.json.getBoolean
import de.binarynoise.util.json.getJsonArray
import de.binarynoise.util.json.getJsonObject
import de.binarynoise.util.json.getString
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.parseJsonObject
import de.binarynoise.util.okhttp.postJson
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
@SSID(
    "@VodafoneWifi",
    "Kunstmuseum WLAN",
    "_free-wifi-stuttgart_(official)",
)
object VodafoneHotspot : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == "hotspot.vodafone.de"
    }
    
    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        val session = client.get(response.requestUrl, "/api/v4/session").parseJsonObject()
        val sessionToken = session.getString("session")
        val landingPageElements = session.getJsonArray("landingPageElements")
        val landingPageLoginProfileId = landingPageElements.filterIsInstance<JsonObject>()
            .first { tryOrNull { it.getString("type") } == "loginProfile" }
            .getString("value")
        val loginProfiles = session.getJsonObject("loginProfiles")
        val landingPageLoginProfile = loginProfiles.getJsonObject(landingPageLoginProfileId)
        val accessType = landingPageLoginProfile.getString("accessType")
        val result = client.postJson(
            response.requestUrl, "/api/v4/login", buildJsonObject {
                put("loginProfile", landingPageLoginProfileId.toInt())
                put("session", sessionToken)
                put("accessType", accessType)
            }.toString(), mapOf(
                "sessionID" to sessionToken,
            )
        ).parseJsonObject()
        check(result.getBoolean("success")) { error("success is not true") }
    }
}
