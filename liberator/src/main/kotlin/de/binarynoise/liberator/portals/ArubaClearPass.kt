package de.binarynoise.liberator.portals

import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.LiberatorExtras
import de.binarynoise.liberator.LocationRedirector
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.liberator.UnsupportedPortalException
import de.binarynoise.liberator.portals.ArubaNetworks.performArubaLogin
import de.binarynoise.util.okhttp.decodedPath
import de.binarynoise.util.okhttp.followRedirects
import de.binarynoise.util.okhttp.getInput
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.requestUrl
import de.binarynoise.util.okhttp.submitOnlyForm
import de.binarynoise.util.okhttp.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

abstract class ArubaClearPassLiberator(val hosts: List<String>) : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        if (LocationRedirector.canRedirect(response)) return false
        return response.requestUrl.host in hosts
    }
    
    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        return solve(client, response, extras, mapOf())
    }
    
    fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras, formParameters: Map<String, String>) {
        val response2 = response.submitOnlyForm(
            client, queryParameters = mapOf(
                "_browser" to "1",
            )
        ).followRedirects(client)
        val response3 = response2.submitOnlyForm(
            client, parameters = formParameters
        )
        val html = response3.parseHtml()
        val form = html.forms().single()
        performArubaLogin(
            client,
            form.attr("action").toHttpUrl(response3.requestUrl),
            form.getInput("user"),
            form.getInput("password"),
        )
    }
}

@SSID(
    "Bershka-WiFi",
    "PULL&BEAR-FreeWiFi",
    "Stradivarius-WiFi",
    "Zara-WiFi",
)
object Inditex : ArubaClearPassLiberator(listOf("wifi.inditex.com")) {
    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        if (response.requestUrl.decodedPath.endsWith("Employees.php")) throw UnsupportedPortalException("employee portal page")
        return super.solve(
            client,
            response,
            extras,
            mapOf(
                // setting "visitor_name" seems to only be necessary for Stradivarius-WiFi
                "visitor_name" to "Oscar",
            ),
        )
    }
}

@Experimental
@SSID("URBAN_GUEST_WIFI")
object UrbanOutfitters : ArubaClearPassLiberator(listOf("register.urbn.com"))
