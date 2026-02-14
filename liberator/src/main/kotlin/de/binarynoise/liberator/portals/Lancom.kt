@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator.portals

import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.Response

@Experimental
object LancomCloudServiceHotspot : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == "hotspot.lmc.de" && response.requestUrl.pathSegments.firstOrNull() == "cloud-service-hotspot"
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        client.postForm(response.requestUrl, null, mapOf()).checkSuccess()
    }
}
