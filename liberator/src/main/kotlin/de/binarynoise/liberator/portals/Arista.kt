package de.binarynoise.liberator.portals

import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.Response

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
@Experimental
object Arista : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host.endsWith(".agni.arista.io") && response.requestUrl.pathSegments.firstOrNull() == "portal"
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val orgId = response.requestUrl.pathSegments[1]
        client.postForm(
            response.requestUrl, "/portal/$orgId/clickThrough/logon", mapOf("orgID" to orgId)
        ).checkSuccess()
    }
}
