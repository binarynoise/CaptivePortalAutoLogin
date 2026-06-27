@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator.portals

import de.binarynoise.liberator.LiberatorExtras
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.enforceHttps
import de.binarynoise.util.okhttp.firstPathSegment
import de.binarynoise.util.okhttp.followRedirects
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.getLocationUrl
import de.binarynoise.util.okhttp.lastPathSegment
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.relativeTo
import de.binarynoise.util.okhttp.requestUrl
import de.binarynoise.util.okhttp.toHttpUrlOrNull
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@SSID(
    "GERBER-FreeWifi",
    "HUGO-BOSS-WIFI",
    "_BUCHERER Free WiFi_",
    "mycloud",
    "o2 free Wifi",
)
object TheCloud : PortalLiberator {
    const val THECLOUD_DOMAIN = "service.thecloud.eu"
    
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == THECLOUD_DOMAIN //
            && response.requestUrl.firstPathSegment == "service-platform"
    }
    
    /**
     * recursively crawl to a until hitting a target [HttpUrl]
     * 
     * @param isTargetUrlCallback check whether this [HttpUrl] is the one searched for
     * @param getPossibleUrls get all [HttpUrl]s which should be crawled next
     * @param doRequest override how a request to a [HttpUrl] is performed, defaults to [get]
     */
    fun crawlToUrl(
        client: OkHttpClient,
        response: Response,
        isTargetUrlCallback: (url: HttpUrl) -> Boolean,
        getPossibleUrls: (response: Response) -> List<HttpUrl>,
        doRequest: (url: HttpUrl) -> Response = { url -> client.get(url, null) },
        alreadySeenUrls: Set<HttpUrl> = setOf(response.requestUrl),
    ): Response? {
        if (isTargetUrlCallback(response.requestUrl)) return response
        if (response.isRedirect) return crawlToUrl(
            client,
            doRequest(response.getLocationUrl()!!),
            isTargetUrlCallback,
            getPossibleUrls,
            doRequest,
            alreadySeenUrls + response.requestUrl,
        )
        val possibleUrls = getPossibleUrls(response).distinct()
        return possibleUrls.asSequence().map { url ->
            crawlToUrl(
                client,
                doRequest(url),
                isTargetUrlCallback,
                getPossibleUrls,
                doRequest,
                alreadySeenUrls + url,
            )
        }.firstOrNull()
    }
    
    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        val baseUrl = response.requestUrl.enforceHttps()
        
        /**
         * macauthlogin redirect chain:
         * initial request -> activate (-> getonline) -> macauthlogin -> register 
         */
        
        val activateResponse = client.get(baseUrl, "activate")
        val crawlResponse = crawlToUrl(
            client,
            activateResponse,
            { url -> url.relativeTo(baseUrl).firstPathSegment == "macauthlogin" },
            getPossibleUrls@{ response ->
                if (!response.isSuccessful) return@getPossibleUrls listOf()
                return@getPossibleUrls response.parseHtml()
                    .getElementsByTag("a")
                    .asSequence()
                    .filter { it.hasAttr("href") }
                    .map { it.attr("href") }
                    .mapNotNull { it.toHttpUrlOrNull(baseUrl) }
                    .map { it.enforceHttps() }
                    .filter { it.relativeTo(baseUrl).firstPathSegment == "url" }
                    .toMutableList()
                    .apply {
                        // the first url on every page appears to be irrelevant for us, so we try that link last
                        if (size >= 2) add(removeAt(0))
                    }
            },
        )
        if (crawlResponse == null) throw IllegalStateException("no crawling path led to macauthlogin")
        
        val macAuthResponse = client.postForm(crawlResponse.requestUrl, "registration", mapOf("terms" to "true"))
        check(macAuthResponse.isRedirect) { "macAuthResponse is not a redirect" }
        if (macAuthResponse.getLocationUrl()!!.lastPathSegment == "getonline") // 
            throw IllegalStateException("macAuthResponse redirected to getonline")
        
        val onlineResponse = macAuthResponse.followRedirects(client) { it.host == THECLOUD_DOMAIN }
        onlineResponse.checkSuccess()
        check(onlineResponse.requestUrl.lastPathSegment == "online") {
            "redirection chain didn't end with online"
        }
    }
}
