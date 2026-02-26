@file:Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")

package de.binarynoise.liberator.portals

import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.liberator.tryOrDefault
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.firstPathSegment
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.readText
import de.binarynoise.util.okhttp.requestUrl
import de.binarynoise.util.okhttp.toHttpUrl
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.json.JSONObject

// https://www.juniper.net/documentation/us/en/software/mist/mist-wireless/topics/task/guest-access-external-portal.html

/*
 * test jwt tokens with this bash command:
 * function testJwt() { curl --verbose "https://portal.gc1.mist.com/authorize?jwt=$1" }
 */

fun isMistPortalUrl(url: HttpUrl): Boolean {
    return with(url.host) {
        startsWith("portal.") && endsWith("mist.com")
    }
}

fun isMistPortalAuthorizeUrl(url: HttpUrl): Boolean {
    return isMistPortalUrl(url) && url.firstPathSegment == "authorize"
}

/**
 * Perform a Juniper networks authorization as specified in the
 * [mist portal authorize howto](https://portal.mist.com/authorize-howto)
 */
fun authorizeMistPortal(
    client: OkHttpClient,
    authorizeUrl: HttpUrl,
    ap_mac: String,
    wlan_id: String,
    client_mac: String,
    secret: String,
) {
    check(isMistPortalAuthorizeUrl(authorizeUrl)) { "authorizeUrl $authorizeUrl is not a valid mist portal url" }
    val currTime = Date().time / 1000
    val expires = currTime + 600 // 10 minutes
    val payload = JSONObject(
        mapOf(
            "ap_mac" to ap_mac,
            "wlan_id" to wlan_id,
            "client_mac" to client_mac,
            "expires" to expires,
            "minutes" to Int.MAX_VALUE,
            "authorize_only" to true,
        )
    )
    val jwt = simpleJwt(payload.toString().toByteArray(), secret)
    val response = client.get(authorizeUrl, null, mapOf("jwt" to jwt))
    response.checkSuccess()
    check(response.readText() == "success") { "no success" }
}

/**
 * same as [authorizeMistPortal],
 * but parses the `ap_mac`, `wlan_id` and `client_mac`
 * from the [portalUrl]'s query parameters
 */
fun authorizeMistPortal(
    client: OkHttpClient,
    authorizeUrl: HttpUrl,
    portalUrl: HttpUrl,
    secret: String,
) {
    return authorizeMistPortal(
        client,
        authorizeUrl,
        ap_mac = portalUrl.queryParameter("ap_mac") ?: error("no ap_mac"),
        wlan_id = portalUrl.queryParameter("wlan_id") ?: error("no wlan_id"),
        client_mac = portalUrl.queryParameter("client_mac") ?: error("no client_mac"),
        secret,
    )
}

/**
 * simple JWT token generation
 */
fun simpleJwt(data: ByteArray, secret: String): String {
    val JWTBase64 = kotlin.io.encoding.Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
    val signatureMethod = "HmacSHA256"
    
    val header = JSONObject().put("alg", "HS256").put("typ", "JWT").toString()
    val encodedHeader = JWTBase64.encode(header.toByteArray())
    val encodedData = JWTBase64.encode(data)
    
    val mac = Mac.getInstance(signatureMethod)
    mac.init(SecretKeySpec(secret.toByteArray(), signatureMethod))
    val digest = mac.doFinal("$encodedHeader.$encodedData".toByteArray())
    val signature = JWTBase64.encode(digest)
    return "$encodedHeader.$encodedData.$signature"
}

@Experimental
@SSID("Rossmann Kunden-WLAN")
/**
 * liberates portals hosted on `portal.mist.com` directly
 */
object MistCom : PortalLiberator {
    override fun canSolve(response: Response): Boolean {
        return isMistPortalUrl(response.requestUrl)
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val ap_mac = response.requestUrl.queryParameter("ap_mac") ?: error("no ap_mac")
        val url = response.requestUrl.queryParameter("url") ?: error("no url")
        val client_mac = response.requestUrl.queryParameter("client_mac") ?: error("no client_mac")
        val wlan_id = response.requestUrl.queryParameter("wlan_id") ?: error("no wlan_id")
        
        client.postForm(
            response.requestUrl, "/logon", mapOf(
                "ap_mac" to ap_mac,
                "auth_method" to "passphrase",
                "tos" to "true",
                "url" to url,
                "client_mac" to client_mac,
                "wlan_id" to wlan_id,
            )
        ).checkSuccess()
    }
}

/* 
 * following liberators are intended for liberating mist.com networks,
 * where the captive portal site is hosted externally
 * 
 * since data from that site is necessary, 
 * they need to have their own portal liberators
 */

@Experimental
@SSID("@Hollister Co. Free Wi-Fi")
object Abercrombie : PortalLiberator {
    fun getAuthorizeUrl(portalUrl: HttpUrl): HttpUrl {
        return portalUrl.queryParameter("authorize_url")?.toHttpUrl(portalUrl) ?: error("no authorize_url")
    }
    
    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == "storewifi.abercrombie.com" && tryOrDefault(false) {
            isMistPortalUrl(getAuthorizeUrl(response.requestUrl))
        }
    }
    
    override fun solve(client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        val authorizeUrl = getAuthorizeUrl(response.requestUrl)
        val secret = response.requestUrl.queryParameter("s") ?: "f6xFwD8mggTUIkJnqxHXKWU5W53okorJsLFrP3kl"
        authorizeMistPortal(
            client,
            authorizeUrl,
            portalUrl = response.requestUrl,
            secret,
        )
    }
}
