@file:Suppress("MemberVisibilityCanBePrivate", "UNREACHABLE_CODE")

package de.binarynoise.liberator

import java.util.concurrent.TimeUnit.*
import de.binarynoise.logger.Logger.log
import de.binarynoise.util.okhttp.checkSuccess
import de.binarynoise.util.okhttp.createDummyResponse
import de.binarynoise.util.okhttp.decodedPath
import de.binarynoise.util.okhttp.firstPathSegment
import de.binarynoise.util.okhttp.followRedirects
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.getInput
import de.binarynoise.util.okhttp.getLocation
import de.binarynoise.util.okhttp.hasInput
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.postJson
import de.binarynoise.util.okhttp.readText
import de.binarynoise.util.okhttp.requestUrl
import de.binarynoise.util.okhttp.resolveOrThrow
import de.binarynoise.util.okhttp.setLocation
import okhttp3.Cookie
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.Jsoup

class Liberator(
    private val clientInit: (OkHttpClient.Builder) -> Unit,
    val portalTestUrl: String,
    private val userAgent: String,
) {
    
    private val cookies: MutableSet<Cookie> = mutableSetOf()
    
    private val client = OkHttpClient.Builder().apply {
        cache(null)
        retryOnConnectionFailure(true)
        followRedirects(false) // we do that manually if needed
//        followSslRedirects(true) // doesn't work as followRedirects is set to false
        
        addInterceptor(::interceptRequest)
        readTimeout(1, MINUTES)
        clientInit(this)
    }.build()
    
    /**
     * Intercepts the request, to
     * - add User-Agent, Connection and Cookie headers,
     * - log request details and POST request body,
     * - proceed with the request,
     * - log the response details and body,
     * - save cookies
     */
    private fun interceptRequest(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val newRequest = originalRequest.newBuilder().apply {
            header("User-Agent", userAgent)
            header("Connection", "Keep-Alive")
            val cookiesToSend = cookies.filter { it.matches(originalRequest.url) }
            log("Loading cookies for ${originalRequest.url}: ${cookiesToSend.joinToString { "${it.name}=${it.value}" }}")
            if (cookiesToSend.isNotEmpty()) {
                val cookieHeader = cookiesToSend.joinToString(separator = "; ") { "${it.name}=${it.value}" }
                header("Cookie", cookieHeader)
            }
        }.build()
        
        log("> ${newRequest.method} ${newRequest.url}")
        newRequest.headers.forEach { (name, value) ->
            log("> $name: $value")
        }
        if (newRequest.method == "POST") {
            when (val body = newRequest.body) {
                null -> {
                }
                is FormBody -> {
                    for (i in 0..<body.size) {
                        val name = body.name(i)
                        val value = body.value(i)
                        log("> $name=$value")
                    }
                }
                is MultipartBody -> {
                    log("> Content-Type: ${body.contentType()}")
                    body.parts.forEach {
                        log("> ${it.body.contentType()} (${body.contentLength()} bytes)")
                        log(it.body.readText())
                    }
                }
                else -> {
                    log("> Content-Type: ${body.contentType()} (${body.contentLength()} bytes)")
                    log(body.readText())
                }
            }
        }
        
        val response = chain.proceed(newRequest)
        
        log("< ${response.code} ${response.message}")
        response.headers.forEach { (name, value) ->
            log("< $name: $value")
        }
        var text = response.readText(skipStatusCheck = true)
        
        val newCookies = Cookie.parseAll(newRequest.url, response.headers)
        if (newCookies.isNotEmpty()) {
            log("Saving cookies for ${newRequest.url}: ${newCookies.joinToString { "${it.name}=${it.value}" }}")
            newCookies.forEach { new ->
                val old = cookies.find { old -> old.name == new.name }
                if (old != null) {
                    cookies -= old
                }
                cookies += new
            }
            log("All cookies now: ${cookies.joinToString { "${it.name}=${it.value}" }}")
        }
        
        // prettify text if html, xml or json
        val contentType = response.header("Content-Type")
        if (contentType != null) when {
            contentType.startsWith("text/html") -> text = Jsoup.parse(text).html()
            contentType.startsWith("text/xml") -> text = Jsoup.parse(text).body().html()
            contentType.startsWith("application/json") -> text = JSONObject(text).toString(2)
        }
        
        log(text)
        
        return response
    }
    
    /**
     * Attempts to liberate the user by making a series of HTTP requests to the portal.
     */
    fun liberate(): LiberationResult {
        val response = client.get(null, portalTestUrl)
        if (response.getLocation().isNullOrBlank()) {
            return LiberationResult.NotCaught
        }
        
        val res = inner(response)
        
        if (res !is LiberationResult.Success) {
            return res
        }
        
        Thread.sleep(1000)
        
        // check if the user is still in the portal, try both http and https to avoid false positives
        val location =
            client.get(null, portalTestUrl).getLocation() ?: client.get(null, portalTestUrl.replace("http:", "https:"))
                .getLocation()
        return if (location.isNullOrBlank()) {
            res
        } else {
            LiberationResult.StillCaptured(location)
        }
    }
    
    @Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName")
    private fun inner(response: Response): LiberationResult {
        try {
            val location = response.getLocation()
            if (location.isNullOrBlank()) return LiberationResult.UnknownPortal(response.requestUrl.toString())
            
            val locationUrl: HttpUrl = response.requestUrl.resolveOrThrow(location)
            
            when {
                // Germany, Dortmund, bus, Westfalenhallen
                // verified
                // DSW21-WLAN, "Hotspot Westfalenhallen"
                //<editor-fold defaultstate="collapsed">
                //
                // https://controller.dokom21.de/?dst=...
                // https://hotspot.dokom21.de/bus~/?...
                // https://hotspot.dokom21.de/bus~/Index
                "hotspot.dokom21.de" == locationUrl.host && "/([^/]+)/Index".toRegex()
                    .matches(locationUrl.encodedPath) -> {
                    val networkType = locationUrl.pathSegments.first() // bus~
                    
                    // https://hotspot.dokom21.de/bus~/Index | Cookie: ASP.NET_SessionId=...
                    val response1 = client.get(locationUrl, null)
                    
                    // https://hotspot.dokom21.de/bus~/Login/
                    val response1b = client.get(response1.requestUrl, "/$networkType/Login")
                    
                    val html1 = response1b.parseHtml()
                    
                    val response2 = client.postForm(
                        response1b.requestUrl,
                        null, // https://hotspot.dokom21.de/bus~/Login/
                        mapOf(
                            "__EVENTTARGET" to html1.getInput("__EVENTTARGET"), // leer
                            "__EVENTARGUMENT" to html1.getInput("__EVENTARGUMENT"), // leer
                            "__VIEWSTATE" to html1.getInput("__VIEWSTATE"),
                            "__LASTFOCUS" to html1.getInput("__LASTFOCUS"), // leer
                            "__VIEWSTATEGENERATOR" to html1.getInput("__VIEWSTATEGENERATOR"),
                            "__EVENTVALIDATION" to html1.getInput("__EVENTVALIDATION"),
                            
                            "ctl00\$GenericContent\$AgbAccepted" to "on",
                            "ctl00\$GenericContent\$PrivacyPolicyAccepted" to "on",
                            "ctl00\$LanguageSelect" to "DE",
                            "ctl00\$GenericContent\$SubmitLogin" to "Einloggen",
                        ),
                    )
                    // https://controller.dokom21.de/portal_api.php?action=authenticate&...
                    // https://hotspot.dokom21.de/bus~/?...
                    // https://hotspot.dokom21.de/bus~/Login
                    check(response2.followRedirects(client).readText().contains("Login erfolgreich."))
                }
                //</editor-fold>
                
                // Germany, Deutsche Bahn
                //<editor-fold defaultstate="collapsed">
                
                // https://wifi.bahn.de/sp/7cwojgdj
                ("portal.wifi.bahn.de" == locationUrl.host || "wifi.bahn.de" == locationUrl.host) //
                    && ("/" == locationUrl.encodedPath || "sp" == locationUrl.firstPathSegment) //
                    && null == client.get(locationUrl, null).getLocation() -> {
                    client.postForm(
                        locationUrl, "/login", mapOf(
                            "login" to "oneclick",
                            "oneSubscriptionForm_connect_policy_accept" to "on",
                        )
                    ).followRedirects(client).checkSuccess()
                }
                
                ("login.wifionice.de" == locationUrl.host || "wifi.bahn.de" == locationUrl.host) //
                    && "cna" == locationUrl.firstPathSegment -> {
                    val response1 = response.followRedirects(client)
                    val response2 = client.postJson(response1.requestUrl, "/cna/logon", "{}")
                    check(JSONObject(response2.readText()).getString("result") == "success") { "response does not contain success" }
                }
                
                /*
                // http://login.wifionice.de/?url=...
                "wifi-bahn.de" == locationUrl.host || "login.wifionice.de" == locationUrl.host -> {
                    // https://login.wifionice.de/?url=...
                    // https://login.wifionice.de/de/ | Cookie: csrf
                    val response1 = client.get(locationUrl, null).followRedirects(client)
                    
                    val csrfToken = cookies.find { it.name == "csrf" }?.value ?: error("no csrf")
                    client.post(
                        response1.requestUrl, null, mapOf(
                            "login" to "true",
                            "CSRFToken" to csrfToken,
                        )
                    ).followRedirects(client)
                }
                */
                
                // verified
                // WIFI@DB
                // https://www.hotsplots.de/auth/login.php
                "www.hotsplots.de" == locationUrl.host && "/auth/login.php" == locationUrl.encodedPath -> {
                    val response1 = client.get(response.requestUrl, location)
                    val html1 = response1.parseHtml()
                    
                    client.postForm(
                        null, "https://www.hotsplots.de/auth/login.php",
                        buildMap {
                            if (html1.hasInput("hotsplots-colibri-terms")) {
                                set("hotsplots-colibri-terms", "on")
                            } else {
                                set("termsOK", "on")
                                set("termsChkbx", "on")
                                set("haveTerms", html1.getInput("haveTerms"))
                            }
                            
                            set("challenge", html1.getInput("challenge"))
                            set("uamip", html1.getInput("uamip"))
                            set("uamport", html1.getInput("uamport"))
                            set("userurl", html1.getInput("userurl"))
                            set("myLogin", html1.getInput("myLogin"))
                            set("ll", html1.getInput("ll"))
                            set("nasid", html1.getInput("nasid"))
                            set("custom", html1.getInput("custom"))
                        },
                    ).followRedirects(client)
                }
                //</editor-fold>
                
                // Germany, DB Regio BW
                // freeWIFIahead!
                // TODO
                //<editor-fold defaultstate="collapsed">
                // https://wasabi-splashpage.wifi.unwired.at?user_session_id=...
//              "wasabi-splashpage.wifi.unwired.at" == locationUrl.host -> {
//
//              }
                //</editor-fold>
                
                // Germany, DB Regio RR
                // RRX Hotspot
                "portal.iob.de" == locationUrl.host -> {
                    return inner(createDummyResponse().setLocation("http://192.168.44.1/prelogin").build())
                }
                
                // Germany, DB
                // Hotspot S-Bahn Rhein-Ruhr
                //<editor-fold defaultstate="collapsed">
                // http://10.10.10.1:2050/splash.html?redir=...
                "10.10.10.1" == locationUrl.host && 2050 == locationUrl.port && "splash.html" == locationUrl.firstPathSegment -> {
                    val html1 = client.get(locationUrl, null).parseHtml()
                    client.get(
                        locationUrl, "/nodogsplash_auth/", queryParameters = mapOf(
                            "tok" to html1.getInput("tok"),
                            "redir" to html1.getInput("redir"),
                        )
                    ).checkSuccess()
                }
                //</editor-fold>
                
                // Germany, T mobile
                // Airport-Frankfurt
                // AIRPORT-FREE-WIFI
                // verified
                //<editor-fold defaultstate="collapsed">
                // https://hotspot.t-mobile.net/wlan/redirect.do?origurl=_&ts=1754819868968
                "hotspot.t-mobile.net" == locationUrl.host && locationUrl.decodedPath == "/wlan/redirect.do" -> {
                    val response1 = client.postJson(locationUrl, "/wlan/rest/freeLogin", """{}""")
                    val wlanLoginStatus = JSONObject(response1.readText()).getJSONObject("user").get("wlanLoginStatus")
                    check(wlanLoginStatus == "online") { """wlanLoginStatus: "$wlanLoginStatus" != "online"""" }
                }
                //</editor-fold>
                
                // Germany, Kaufland, Rewe
                //<editor-fold defaultstate="collapsed">
                // https://portal-eu-ffm01.conn4.com/ident?client_ip=...&client_mac=...&site_id=15772&signature=...&loggedin=0&remembered_mac=0
                locationUrl.host.endsWith(".conn4.com") && locationUrl.firstPathSegment == "ident" -> {
                    val site_id = locationUrl.queryParameter("site_id") ?: error("no site_id")
                    val response1 = client.get(locationUrl, null)
                    val location1 = response1.getLocation() // https://portal-eu-ffm01.conn4.com/#
                    
                    val response2 = client.get(response1.requestUrl, location1)
                    val token = response2.readText().let { html ->
                        // find and parse
                        // conn4.hotspot.wbsToken = { "token": "...", "urls": { "grant_url": null, "continue_url": null } };
                        val wbsTokenIndex = html.indexOf("hotspot.wbsToken")
                        check(wbsTokenIndex != -1) { "hotspot.wbsToken not found" }
                        val jsObjectStart = html.indexOf("{", wbsTokenIndex)
                        check(jsObjectStart != -1) { "jsObjectStart not found" }
                        val jsObjectEnd = html.indexOf(";", jsObjectStart)
                        check(jsObjectEnd != -1) { "jsObjectEnd not found" }
                        
                        val jsObject = JSONObject(html.substring(jsObjectStart, jsObjectEnd))
                        jsObject.getString("token")
                    }
                    
                    val response3 = client.postForm(
                        locationUrl, "/wbs/api/v1/create-session/",
                        mapOf(
                            "authorization" to "token=$token",
                            "locale" to "de_DE",
                            "locationId" to site_id,
                            "session_id" to "",
                            "with-tariffs" to "1",
                        ),
                    )
                    val session = JSONObject(response3.readText()).getString("session") ?: error("no session")
                    
                    val response4 = client.postForm(
                        locationUrl, "/wbs/api/v1/register/free/",
                        mapOf(
                            "authorization" to "session:$session",
                            "registration_type" to "terms-only",
                            "registration[terms]" to "1",
                        ),
                    )
                    check(JSONObject(response4.readText()).getBoolean("ok"))
                }
                //</editor-fold>
                
                // IKEA
                // "IKEA WiFi"
                //<editor-fold defaultstate="collapsed">
                "yo-wifi.net" == locationUrl.host && "authen" == locationUrl.firstPathSegment -> {
                    val response1 = client.get(response.requestUrl, location)
                    
                    // https://cp7-wifi.net/?device_name=ide223&user_mac=...&device_hostname=yo-wifi.net&ssid=/#/login
                    val url1 = response1.getLocation() ?: error("no location 1")
                    val mac = response1.requestUrl.queryParameter("user_mac") ?: error("no mac")
                    val deviceName = response1.requestUrl.queryParameter("device_name") ?: error("no deviceName")
                    
                    val response2 = client.get(response1.requestUrl, url1)
                    // html with js form
                    
                    val response3 = client.postForm(
                        response2.requestUrl,
                        "/login/tc",
                        mapOf(),
                        queryParameters = mapOf(
                            "client_id" to "1",
                            "nasid" to deviceName,
                            "save_mac" to "false",
                            "user_mac" to mac.replace(":", "-"),
                            "user_type" to "9", // TERMS_AND_CONDITIONS
                        ),
                    )
                    
                    val json = JSONObject(response3.readText())
                    
                    val payload = json.getJSONObject("payload")
                    val realm = payload.getString("realm")
                    val username = payload.getString("username")
                    val password = payload.getString("password")
                    
                    val userid = realm + "1\\" + username + "\\9"
                    
                    val response4 = client.get(
                        response3.requestUrl,
                        "/authen/login/",
                        mapOf(
                            "userid" to userid,
                            "password" to password,
                        ),
                    )
                    val url4 = response4.getLocation() ?: error("no location 4") // https://cp7-wifi.net/success?
                    val url5 = client.get(response4.requestUrl, url4).getLocation()
                        ?: error("no location 5") // https://cp7-wifi.net/?...#/success
                    client.get(response4.requestUrl, url5).checkSuccess()
                }
                //</editor-fold>
                
                // MediaMarkt / Saturn
                // media-kunden
                // verified
                //<editor-fold defaultstate="collapsed">
                // "http://192.0.2.1/fs/customwebauth/login.html?switch_url=http://192.0.2.1/login.html&ap_mac=...&client_mac...&wlan=media-kunden&redirect=am-i-captured.binarynoise.de/"
                "192.0.2.1" == locationUrl.host -> {
                    val switch_url = locationUrl.queryParameter("switch_url") ?: error("no login_url")
                    val redirect_url = locationUrl.queryParameter("redirect") ?: error("no redirect_url")
                    client.postForm(
                        locationUrl, switch_url,
                        mapOf(
                            "del[]" to "on",
                            "redirect_url" to redirect_url,
                            "err_flag" to "0",
                            "buttonClicked" to "4",
                        ),
                    ).checkSuccess()
                }
                // </editor-fold>
                
                // Milaneo Stuttgart
                // "-free Milaneo Stuttgart"
                // https://start.cloudwifi.de/?res=notyet&uamip=...&uamport=...&challenge=...&called=...&ip=...&nasid=...&sessionid=...&userurl=http%3a%2f%2fam-i-captured.binarynoise.de%2f&md=...
                //<editor-fold defaultstate="collapsed">
                "start.cloudwifi.de" == locationUrl.host -> {
                    val response1 = client.get(response.requestUrl, location)
                    val html1 = response1.parseHtml()
                    
                    val response2 = client.postForm(
                        locationUrl, null, mapOf(
                            "FX_lang" to html1.getInput("FX_lang"),
                            "FX_loginTemplate" to html1.getInput("FX_loginTemplate"),
                            "FX_loginType" to html1.getInput("FX_loginType"),
                            "FX_password" to html1.getInput("FX_password"),
                            "FX_username" to html1.getInput("FX_username"),
                            "called" to html1.getInput("called"),
                            "cbQpC" to html1.getInput("cbQpC"),
                            "challenge" to html1.getInput("challenge"),
                            "ip" to html1.getInput("ip"),
                            "mac" to html1.getInput("mac"),
                            "nasid" to html1.getInput("nasid"),
                            "sessionid" to html1.getInput("sessionid"),
                            "uamip" to html1.getInput("uamip"),
                            "uamport" to html1.getInput("uamport"),
                            "userurl" to html1.getInput("userurl"),
                        )
                    )
                    
                    val html2 = response2.readText()
                    val src = html2
                    val start = src.indexOf("window.location.replace('")
                    val end = src.indexOf("')", start)
                    val url2 = src.substring(start + "window.location.replace('".length, end)
                    // http://192.168.182.1:3990/logon?username=...%3D&password=...&userurl=http%3A%2F%2Fam-i-captured.binarynoise.de%2F
                    check(url2.isNotBlank()) { "no url2" }
                    
                    val response3 = client.get(response.requestUrl, url2)
                    val url3 = response3.getLocation() ?: error("no url3")
                    val res = url3.toHttpUrl().queryParameter("res")
                    check(res == "success") { "res=$res" }
                    client.get(response.requestUrl, url3).checkSuccess()
                }
                //</editor-fold>
                
                // Stuttgart official, Vodaphone?
                // https://login.mypowerspot.de
                //<editor-fold defaultstate="collapsed">
                "login.mypowerspot.de" == locationUrl.host -> {
                    val response1 = client.get(locationUrl, "/landingpage/")
                    val response2 = client.get(response1.requestUrl, null, mapOf("acceptTOC" to "1"))
                    val location2 = response2.getLocation() ?: error("no location2")
                    check(location2.endsWith("success"))
                    response2.followRedirects(client).checkSuccess()
                }
                //</editor-fold>
                
                // Commerzbank
                // https://wifiaccess.co/.../portal/
                //<editor-fold defaultstate="collapsed">
                "wifiaccess.co" == locationUrl.host && locationUrl.pathSegments.lastOrNull() == "portal" -> {
                    client.postForm(locationUrl, "/portal_api.php", mapOf("action" to "init")).checkSuccess()
                    client.postForm(
                        locationUrl,
                        "/portal_api.php",
                        mapOf("action" to "subscribe", "type" to "one", "policy_accept" to "true"),
                    ).checkSuccess()
                }
                //</editor-fold>
                
                // Telekom
                // Telekom_free
                // TODO
                //<editor-fold defaultstate="collapsed">
                // https://hotspot.t-mobile.net/wlan/redirect.do?origurl=http%3A%2F%2Fam-i-captured.binarynoise.de%2F&ts=...
                "hotspot.t-mobile.net" == locationUrl.host && "wlan/redirect.do" == locationUrl.decodedPath -> {
                    val response1 = client.get(response.requestUrl, location)
                    val url1 = response1.getLocation() ?: error("no url1")
                    // https://hotspot.t-mobile.net/TCOM/hotspot/.../de_DE/index.html?origurl=http%3A%2F%2Fam-i-captured.binarynoise.de%2F&ts=...
                    
                    val response2 = client.get(response.requestUrl, url1)
                    response2.checkSuccess()
                    
                    TODO("Not implemented yet")
                    
                    val response4 = client.postForm(null, "https://hotspot.t-mobile.net/wlan/rest/freeLogin", mapOf())
                    
                    val json = JSONObject(response4.readText())
                    val loginStatus = json.getJSONObject("user").getString("wlanLoginStatus")
                    check(loginStatus == "online") { "wlanLoginStatus not online: $loginStatus" }
                }
                //</editor-fold>
                
                // fritz.box guest wifi
                // verified
                //<editor-fold defaultstate="collapsed">
                "untrusted_guest.lua" == locationUrl.firstPathSegment -> {
                    val base = location.toHttpUrl()
                    client.get(base, "/trustme.lua?accept=").checkSuccess()
                }
                //</editor-fold>
                
                // PortalProxy
                //<editor-fold defaultstate="collapsed">
                "binarynoise.de" == locationUrl.host -> {
                    val base = location.toHttpUrl()
                    client.postForm(base, "/login", emptyMap()).followRedirects(client).checkSuccess()
                }
                //</editor-fold>
                
                else -> {
                    log("unknown captive portal: $location")
                    // follow redirects and try again
                    // TODO: recursion limit?
                    return inner(client.get(response.requestUrl, location))
                }
            }
            
            return LiberationResult.Success(location)
        } catch (e: Exception) {
            return LiberationResult.Error(response.requestUrl.toString(), e, e.message.orEmpty())
        }
    }
    
    sealed class LiberationResult {
        data object NotCaught : LiberationResult()
        
        data class Success(val url: String) : LiberationResult()
        data class Timeout(val url: String) : LiberationResult()
        data class Error(val url: String, val exception: Throwable, val message: String) : LiberationResult()
        data class UnknownPortal(val url: String) : LiberationResult()
        data class StillCaptured(val url: String) : LiberationResult()
        data class UnsupportedPortal(val url: String) : LiberationResult()
    }
}
