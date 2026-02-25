@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.decodedPath
import de.binarynoise.util.okhttp.hasQueryParameter
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.Cookie
import okhttp3.OkHttpClient
import okhttp3.Response

@SSID(
    "media-kunden",
    "saturn-kunden",
)
object CiscoWirelessMobility : PortalLiberator {
    // https://www.cisco.com/c/en/us/support/docs/wireless-mobility/wireless-lan-wlan/118826-config-https-webauth-00.html
    override fun canSolve(response: Response): Boolean {
        return with(response.requestUrl) {
            decodedPath == "/fs/customwebauth/login.html" //
                && hasQueryParameter("switch_url") // 
                && hasQueryParameter("redirect")
        }
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val switch_url = response.requestUrl.queryParameter("switch_url") ?: error("no login_url")
        val redirect_url = response.requestUrl.queryParameter("redirect") ?: error("no redirect_url")
        client.postForm(
            response.requestUrl, switch_url,
            mapOf(
                "del[]" to "on",
                "redirect_url" to redirect_url,
                "err_flag" to "0",
                "buttonClicked" to "4",
            ),
        ).checkSuccess()
    }
}
