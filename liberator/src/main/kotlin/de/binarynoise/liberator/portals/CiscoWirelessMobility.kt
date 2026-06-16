@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator.portals

import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.LiberatorExtras
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.decodedPath
import de.binarynoise.util.okhttp.hasQueryParameter
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.requestUrl
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

// https://www.cisco.com/c/en/us/support/docs/wireless-mobility/wireless-lan-wlan/118826-config-https-webauth-00.html
// https://github.com/stuartst/cisco-wlc-captive-portal/blob/master/README.md

@SSID(
    "media-kunden",
    "saturn-kunden",
)
object CiscoWirelessMobility : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return with(response.requestUrl) {
            decodedPath == "/fs/customwebauth/login.html" //
                && hasQueryParameter("switch_url") // 
                && hasQueryParameter("redirect")
        }
    }
    
    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        val switch_url = response.requestUrl.queryParameter("switch_url") ?: error("no login_url")
        val redirect_url = response.requestUrl.queryParameter("redirect") ?: error("no redirect_url")
        solve(client, response.requestUrl, switch_url, redirect_url)
    }
    
    fun solve(
        client: OkHttpClient,
        baseUrl: HttpUrl,
        switch_url: String,
        redirect_url: String,
        additionalParameters: Map<String, String> = mapOf(),
    ) {
        client.postForm(
            baseUrl,
            switch_url,
            mapOf(
                "redirect_url" to redirect_url,
                "buttonClicked" to "4", // mandatory
                "err_flag" to "0",
            ) + additionalParameters,
        ).checkSuccess()
    }
}

@Experimental
@SSID("MBMuseum_FreeWifi")
object MercedesBenzMuseum : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == "wifi.media.mercedes-benz.museum"
    }
    
    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        CiscoWirelessMobility.solve(client, response.requestUrl, "", "success.html")
    }
}

@Experimental
@SSID("ColesFreeWiFi")
object Coles : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == "app.coles.com.au" //
            && response.requestUrl.decodedPath == "/colesfreewifi.htm"
    }
    
    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        CiscoWirelessMobility.solve(client, response, extras)
    }
}
