package de.binarynoise.liberator.portals

import de.binarynoise.liberator.LiberatorExtras
import de.binarynoise.liberator.LocationRedirector
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.liberator.UnsupportedPortalException
import de.binarynoise.liberator.dramaNumber
import de.binarynoise.liberator.filterNotNullValues
import de.binarynoise.liberator.portals.ArubaNetworks.performArubaLogin
import de.binarynoise.liberator.randomEmail
import de.binarynoise.liberator.tryOrNull
import de.binarynoise.util.okhttp.decodedPath
import de.binarynoise.util.okhttp.firstPathSegment
import de.binarynoise.util.okhttp.followRedirects
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.getInput
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.requestUrl
import de.binarynoise.util.okhttp.submitOnlyForm
import de.binarynoise.util.okhttp.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.FormElement

abstract class ArubaClearPassLiberator(vararg val hosts: String) : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        if (LocationRedirector.canRedirect(response)) return false
        if (hosts.isNotEmpty() && response.requestUrl.host !in hosts) return false
        return with(response.requestUrl) {
            queryParameter("cmd") == "login" // 
                && queryParameter("_browser") == "1" // 
                && firstPathSegment == "guest"
        }
    }
    
    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        return solveArubaClearPass(client, response, extras)
    }
    
    fun solveArubaClearPass(
        client: OkHttpClient,
        response: Response,
        extras: LiberatorExtras,
        additionalInitialFormParameters: Map<String, String> = mapOf(),
        additionalReceiptFormParameters: Map<String, String> = mapOf(),
        additionalSelfRegistrationParameters: Map<String, String> = mapOf(),
    ) {
        val initialResponse = response.submitOnlyForm(
            client,
            parameters = additionalInitialFormParameters,
            queryParameters = mapOf(
                "_browser" to "1",
            ),
        ).followRedirects(client)
        if (findWebLoginForm(initialResponse) != null) return submitWebLoginForm(client, initialResponse)
        if (hasVisibleAuthFields(response.parseHtml())) return solveArubaClearPassRegistrationPath(
            client,
            initialResponse,
            extras,
            additionalReceiptFormParameters = additionalReceiptFormParameters,
            additionalSelfRegistrationParameters = additionalSelfRegistrationParameters,
        )
        val receiptResponse = initialResponse.submitOnlyForm(
            client,
            parameters = additionalReceiptFormParameters,
        )
        submitWebLoginForm(client, receiptResponse)
    }
    
    fun hasVisibleAuthFields(document: Document): Boolean {
        return document.select("input[name=user]:not([type=hidden])").isNotEmpty() // 
            || document.select("input[name=password]:not([type=hidden])").isNotEmpty()
    }
    
    // seen on DG_Hotspot
    fun solveArubaClearPassRegistrationPath(
        client: OkHttpClient,
        response: Response,
        extras: LiberatorExtras,
        additionalReceiptFormParameters: Map<String, String> = mapOf(),
        additionalSelfRegistrationParameters: Map<String, String> = mapOf(),
    ) {
        val selfRegistration =
            client.get(response.requestUrl, "selfregistrationeu.php", queryParameters = mapOf("_browser" to "1"))
        val form = selfRegistration.parseHtml().forms().single()
        val selfRegistrationResponse = response.submitOnlyForm(
            client,
            parameters = generateSelfRegistrationDummyData(form) + additionalSelfRegistrationParameters,
        ).followRedirects(client)
        val selfRegistrationReceiptResponse = selfRegistrationResponse.submitOnlyForm(client)
        submitWebLoginForm(client, selfRegistrationReceiptResponse)
    }
    
    fun generateSelfRegistrationDummyData(form: FormElement): Map<String, String> {
        return form.select("input:not([type=hidden])")
            .map { it.attr("name") }
            .filter { it.isNotEmpty() }
            .associateWith {
                when (it) {
                    "visitor_name" -> "Antonio"
                    "visitor_phone" -> dramaNumber()
                    "visitor_phone_intl" -> dramaNumber()
                    "email" -> randomEmail()
                    "creator_accept_terms" -> "1"
                    else -> null
                }
            }
            .filterNotNullValues()
    }
    
    fun findWebLoginForm(response: Response): FormElement? {
        val html = response.parseHtml()
        return tryOrNull { html.expectForm("form[name=weblogin_form]") }
    }
    
    fun submitWebLoginForm(client: OkHttpClient, response: Response) {
        val form = findWebLoginForm(response)
        require(form != null) { "no weblogin_form found" }
        performArubaLogin(
            client,
            form.attr("action").toHttpUrl(response.requestUrl),
            form.getInput("user"),
            form.getInput("password"),
        )
    }
}

/**
 * generic [ArubaClearPassLiberator] which should activate for all ArubaClearPass portals which do not have a specific implementation
 */
@SSID(
    "DG_Hotspot",
    "KlinikumDO",
    "Segmueller-Hotspot",
    "Tally's Bunny Wifi",
    "URBAN_GUEST_WIFI",
    "scandic_easy",
)
object ArubaClearPass : ArubaClearPassLiberator() {
    override fun canSolve(response: Response): Boolean {
        if (allPortalLiberators.filterIsInstance<ArubaClearPassLiberator>()
                .filterNot { it == this }
                .any { it.canSolve(response) }
        ) return false
        return super.canSolve(response)
    }
}

@SSID(
    "Bershka-WiFi",
    "MassimoDutti-WiFi",
    "PULL&BEAR-FreeWiFi",
    "Stradivarius-WiFi",
    "Zara-WiFi",
)
object Inditex : ArubaClearPassLiberator("wifi.inditex.com") {
    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        if (response.requestUrl.decodedPath.endsWith("Employees.php")) throw UnsupportedPortalException("employee portal page")
        return solveArubaClearPass(
            client,
            response,
            extras,
            additionalReceiptFormParameters = mapOf(
                // setting "visitor_name" seems to only be necessary for Stradivarius-WiFi
                "visitor_name" to "Oscar",
            ),
        )
    }
}
