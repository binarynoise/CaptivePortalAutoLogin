package de.binarynoise.liberator.portals

import de.binarynoise.liberator.LiberatorExtras
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.PortalRedirector
import de.binarynoise.liberator.SSID
import de.binarynoise.logger.Logger.log
import de.binarynoise.util.json.getString
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.firstPathSegment
import de.binarynoise.util.okhttp.followRedirects
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.parseJsonObject
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.postJson
import de.binarynoise.util.okhttp.requestUrl
import de.binarynoise.util.okhttp.submitOnlyForm
import okhttp3.OkHttpClient
import okhttp3.Response

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
@SSID(
    "WIFIonICE",
    "dbs4public",
)
object DBWifi : PortalLiberator {
    val domains = setOf(
        "login.wifionice.de",
        "portal.wifi.bahn.de",
        "wifi-bahn.de",
        "wifi.bahn.de",
    )
    
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host in domains //
            && !response.isRedirect //
            && response.requestUrl.firstPathSegment in setOf("cna", "sp", "cp", "auth_login_update")
    }
    
    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        when (response.requestUrl.firstPathSegment) {
            "cna" -> {
                log("cna")
                val response2 = client.postJson(response.requestUrl, "/cna/logon", mapOf())
                check(
                    response2.parseJsonObject().getString("result") == "success"
                ) { "response does not contain success" }
            }
            "sp" -> {
                log("sp")
                client.postForm(
                    response.requestUrl, "/login", mapOf(
                        "login" to "oneclick",
                        "oneSubscriptionForm_connect_policy_accept" to "on",
                    )
                ).followRedirects(client).checkSuccess()
            }
            "cp" -> {
                log("cp")
                response.submitOnlyForm(client)
            }
            "auth_login_update" -> {
                log("auth_login_update")
                response.submitOnlyForm(client)
            }
        }
    }
}

object WifiOnIceRedirector : PortalRedirector {
    override fun canRedirect(response: Response): Boolean {
        return response.requestUrl.host == "login.wifionice.de" //
            && response.parseHtml(true).select("form[id=urlform]").isNotEmpty()
    }
    
    override fun redirect(
        client: OkHttpClient, response: Response, extras: LiberatorExtras
    ): Response {
        return response.submitOnlyForm(client, cssQuery = "form[id=urlform]")
    }
}
