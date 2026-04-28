@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator.portals

import de.binarynoise.liberator.LiberatorExtras
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.OkHttpClient
import okhttp3.Response

/**
 * We currently don't know what the portal service provider for this portal is named.
 * For now this [PortalLiberator] is named after the many "lp" classes present in their HTML.
 */
@SSID(
    "Landratsamt Besucher",
    "MesseSpot",
    "Vector Hotspot",
)
object LPHotspot : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        if (!response.isSuccessful) return false
        val html = response.parseHtml()
        return listOf(
            "div.lp-content",
            "div.lp-content-box",
            "div.lp-loading",
            "form.lp-form",
        ).all { html.selectFirst(it) != null }
    }
    
    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        client.postForm(response.requestUrl, "/", mapOf("auth" to "free"))
    }
}
