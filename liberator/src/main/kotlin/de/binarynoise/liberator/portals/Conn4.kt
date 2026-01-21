package de.binarynoise.liberator.portals

import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.PortalLiberatorConfig
import de.binarynoise.liberator.SSID
import de.binarynoise.liberator.asIterable
import de.binarynoise.liberator.tryOrDefault
import de.binarynoise.liberator.tryOrNull
import de.binarynoise.logger.Logger.log
import de.binarynoise.rhino.RhinoParser
import de.binarynoise.util.okhttp.firstPathSegment
import de.binarynoise.util.okhttp.followRedirects
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.getLocation
import de.binarynoise.util.okhttp.parseHtml
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.readText
import de.binarynoise.util.okhttp.requestUrl
import de.binarynoise.util.okhttp.resolveOrThrow
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.nodes.Element

@Suppress("SpellCheckingInspection", "GrazieInspection", "LocalVariableName", "RedundantSuppression")
@SSID(
    "-REWE gratis WLAN-",
    "movenpick",
    "BBHOTELSGuest",
    "-Kaufland FreeWiFi-",
    "ibisbudget",
)
object Conn4 : PortalLiberator {
    override fun canSolve(locationUrl: HttpUrl, response: Response): Boolean {
        return locationUrl.host.endsWith(".conn4.com") && locationUrl.firstPathSegment == "ident"
    }
    
    override fun solve(locationUrl: HttpUrl, client: OkHttpClient, response: Response, cookies: Set<Cookie>) {
        
        val site_id = locationUrl.queryParameter("site_id") ?: error("no site_id")
        
        if (locationUrl.queryParameter("loggedIn") != "0") log("loggedIn is ${locationUrl.queryParameter("loggedIn")}, expected 0")
        if (locationUrl.queryParameter("remembered_mac") != "0") log("remembered_mac is ${locationUrl.queryParameter("remembered_mac")}, expected 0")
        
        val html = client.get(locationUrl, null).followRedirects(client).parseHtml()
        val scripts = html.getElementsByTag("script").map { it.getScriptData(client) }
        
        when {
            scripts.any { it.contains("conn4.hotspot.wbsToken") } -> solveScene(
                locationUrl,
                client,
                site_id,
                scripts,
            )
            scripts.any { it.contains("accor/i-accor/html-page-scene-wbs") } -> solveAccor(
                locationUrl,
                client,
                site_id,
                scripts,
                cookies,
            )
            else -> error("no conn4 type matched")
        }
    }
    
    fun createSession(
        client: OkHttpClient,
        apiBase: HttpUrl,
        token: String,
        site_id: String,
        session_id: String = "",
        createSessionParams: Map<String, String> = mapOf(),
        checkOk: Boolean = true,
        checkLoggedIn: Boolean = false,
    ): JSONObject {
        val response = client.postForm(
            apiBase, "create-session/",
            mapOf(
                "authorization" to "token=$token",
                "locale" to "de_DE",
                "locationId" to site_id,
                "session_id" to session_id,
                "with-tariffs" to "1",
            ) + createSessionParams,
        )
        val json = JSONObject(response.readText())
        if (checkOk) check(json.getBoolean("ok")) { "createSession not ok" }
        if (checkLoggedIn) check(json.getBoolean("loggedIn")) { "createSession not loggedIn" }
        return json
    }
    
    val JSONObject.session_id: String
        get() {
            return this.getString("session") ?: error("no session")
        }
    
    fun registerFree(
        client: OkHttpClient,
        apiBase: HttpUrl,
        sessionToken: String,
        registerFreeParams: Map<String, String> = mapOf(),
        checkOk: Boolean = true,
    ): JSONObject {
        val response = client.postForm(
            apiBase, "register/free/",
            mapOf(
                "authorization" to "session=$sessionToken",
                "registration_type" to "terms-only",
                "registration[terms]" to "1",
            ) + registerFreeParams,
        )
        val json = JSONObject(response.readText())
        if (checkOk) check(json.getBoolean("ok")) { "registerFree not ok" }
        return json
    }
    
    fun registerFreeTariff(
        client: OkHttpClient,
        apiBase: HttpUrl,
        sessionToken: String,
        tariff: Int,
        registerFreeParams: Map<String, String> = mapOf(),
        checkOk: Boolean = true,
        checkTariffMatch: Boolean = true,
    ): JSONObject {
        val json = registerFree(
            client,
            apiBase,
            sessionToken,
            mapOf("tariff" to tariff.toString()) + registerFreeParams,
            checkOk,
        )
        if (checkTariffMatch) check(json.getInt("tariff") == tariff) { "registerFreeTariff tariff doesn't match" }
        return json
    }
    
    fun tryTariff(
        client: OkHttpClient,
        apiBase: HttpUrl,
        site_id: String,
        token: String,
        sessionToken: String,
        tariff: JSONObject,
        createSessionParams: Map<String, String> = mapOf(),
        registerFreeParams: Map<String, String> = mapOf(),
    ): Result<Unit> {
        return runCatching {
            registerFreeTariff(
                client,
                apiBase,
                sessionToken,
                tariff.getInt("id"),
                registerFreeParams,
            )
            createSession(
                client,
                apiBase,
                token,
                site_id,
                sessionToken,
                createSessionParams,
                checkLoggedIn = true,
            )
        }
    }
    
    fun tryPossibleTariffs(
        client: OkHttpClient,
        apiBase: HttpUrl,
        token: String,
        site_id: String,
        createSessionParams: Map<String, String> = mapOf(),
        registerFreeParams: Map<String, String> = mapOf(),
    ) {
        if (!PortalLiberatorConfig.experimental) return doRegisterTermsOnlyAuthFlow(
            client,
            apiBase,
            token,
            site_id,
            createSessionParams,
            registerFreeParams,
        )
        val session = createSession(client, apiBase, token, site_id)
        val tariffs = getTariffPreference(session)
        for (tariff in tariffs) {
            val res = tryTariff(
                client,
                apiBase,
                site_id,
                token,
                session.session_id,
                tariff,
            )
            if (res.isSuccess) return
        }
        error("no tariff matched")
    }
    
    fun getTariffPreference(session: JSONObject): List<JSONObject> {
        val tariffs = session.getJSONArray("tariffs").filterIsInstance<JSONObject>()
        if (tariffs.hasOneEntry) return tariffs
        fun JSONObject.isBooleanEqualTo(key: String, value: Boolean, default: Boolean): Boolean {
            if (!this.has(key)) return default
            return this.getBoolean(key) == value
        }
        
        fun JSONObject.wanted(key: String): Boolean {
            return this.isBooleanEqualTo(key, true, true)
        }
        
        fun JSONObject.unwanted(key: String): Boolean {
            return this.isBooleanEqualTo(key, false, true)
        }
        
        // get tariff timeout in minutes
        fun JSONObject.getTariffTimeout(): Int {
            return tryOrNull { this.getInt("validity") } ?: tryOrNull { this.getInt("duration") / 60 } ?: 0
        }
        
        fun JSONObject.getTariffBandwidth(): Int {
            val bandWidth = tryOrNull { this.getInt("availableBandwidth") } ?: 1
            return if (bandWidth == 0) Int.MAX_VALUE else bandWidth
        }
        
        val availableTariffs = tariffs.filter {
            tryOrDefault(0) { it.getInt("price") } == 0
        }.filter { tariff ->
            listOf(
                "is_free",
                "is_free_with_limitations",
            ).any { tariff.wanted(it) }
        }.filter { tariff ->
            listOf(
                "is_paid",
                "is_free_seat_number_name", // we haven't seen an auth flow for this one yet
                "is_third_party",
                "is_smp",
                "is_eap",
                "is_paid_subscription",
                "social_media_providers",
            ).all { tariff.unwanted(it) }
        }.sortedWith(
            compareBy(
                { it.getTariffTimeout() },
                { it.getTariffBandwidth() },
                // TODO: include "limitationAfterLimitExploited" into decision
            )
        )
        if (availableTariffs.isEmpty()) error("no tariffs available")
        return availableTariffs
    }
    
    fun doRegisterTermsOnlyAuthFlow(
        client: OkHttpClient,
        apiBase: HttpUrl,
        token: String,
        site_id: String,
        createSessionParams: Map<String, String> = mapOf(),
        registerFreeParams: Map<String, String> = mapOf(),
    ) {
        val session = createSession(client, apiBase, token, site_id, createSessionParams = createSessionParams)
        return doRegisterTermsOnlyAuthFlow(
            client,
            apiBase,
            token,
            site_id,
            createSessionParams,
            registerFreeParams,
            session,
        )
    }
    
    fun doRegisterTermsOnlyAuthFlow(
        client: OkHttpClient,
        apiBase: HttpUrl,
        token: String,
        site_id: String,
        createSessionParams: Map<String, String> = mapOf(),
        registerFreeParams: Map<String, String> = mapOf(),
        session: JSONObject,
    ) {
        registerFree(
            client,
            apiBase,
            session.session_id,
            registerFreeParams = registerFreeParams,
        )
        createSession(
            client,
            apiBase,
            token,
            site_id,
            session.session_id,
            createSessionParams,
            checkLoggedIn = true,
        )
    }
    
    fun solveScene(
        locationUrl: HttpUrl,
        client: OkHttpClient,
        site_id: String,
        scripts: List<String>,
    ) {
        
        val scriptNode =
            scripts.find { it.contains("conn4.hotspot.wbsToken") } ?: error("no script with conn4.hotspot.wbsToken")
        val assignments = RhinoParser().parseAssignments(scriptNode)
        val token = assignments["conn4.hotspot.wbsToken.token"] ?: error("no token")
        
        val schedule = JSONObject(assignments["_.partial.1"] ?: error("no _.partial.1")).getJSONObject("schedule")
            ?: error("no schedule")
        
        val sceneIds = schedule.getJSONArray("events").asIterable().map { event ->
            runCatching {
                event as JSONObject
                val payload = event.getJSONObject("payload") ?: error("no payload")
                if (payload.getString("type") != "scene") error("type != scene")
                val data = payload.getJSONObject("data")
                if (data.has("module") && data.getString("module").contains("offline")) error("offline")
                data.getString("id")
            }
        }.successes().getOrElse {
            throw IllegalStateException("all scenes failed:" + it.message, it)
        }.toSet()
        
        check(sceneIds.isNotEmpty()) { "no scenes" }
        
        val template = schedule.getString("scene_template") ?: error("no scene_template")
        
        val apiBase = sceneIds.asSequence().map { id ->
            runCatching {
                val responseScene = client.get(locationUrl, template.replace("{id}", id))
                val htmlScene = responseScene.parseHtml()
                
                val element =
                    htmlScene.getElementsByAttribute("data-wbs-base-url").getOrNull(0) ?: error("no data-wbs-base-url")
                val wbsBaseUrl =
                    element.attr("data-wbs-base-url").takeIf(String::isNotEmpty) ?: error("no data-wbs-base-url (blank")
                val wbsBaseUrlSlash = wbsBaseUrl.let { baseUrl -> if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/" }
                responseScene.requestUrl.resolve(wbsBaseUrlSlash) ?: error("no data-wbs-base-url (invalid url)")
            }
        }.firstSuccess().getOrElse {
            throw IllegalStateException("no scene with data-wbs-base-url :" + it.message, it)
        }
        
        tryPossibleTariffs(client, apiBase, token, site_id)
    }
    
    fun solveAccor(
        locationUrl: HttpUrl,
        client: OkHttpClient,
        site_id: String,
        scripts: List<String>,
        cookies: Set<Cookie>,
    ) {
        val token = cookies.find { it.name == "wbs-token" }?.value ?: error("no wbs-token cookie")
        val apiBase = locationUrl.resolveOrThrow("/wbs/api/v1") // TODO: properly parse wbs api base from scrips
        tryPossibleTariffs(client, apiBase, token, site_id)
    }
}

fun Element.getScriptData(client: OkHttpClient): String {
    if (this.data().isEmpty() && this.attr("src").isNotEmpty()) {
        val scriptUrl = absUrl("src")
        if (scriptUrl.isEmpty()) return ""
        return client.get(null, scriptUrl).readText()
    }
    return this.data()
}

val <T> List<T>.hasOneEntry: Boolean get() = this.size == 1

fun <T> Sequence<Result<T>>.firstSuccess(): Result<T> {
    val exceptions = mutableListOf<Throwable>()
    for (result in this) {
        result.onSuccess { return result }
        result.onFailure { exceptions.add(it) }
    }
    val exception = NoSuchElementException("no success: " + exceptions.joinToString(", ") { it.message.toString() })
    for (exception in exceptions) {
        exception.addSuppressed(exception)
    }
    return Result.failure(exception)
}

fun <T> List<Result<T>>.successes(): Result<List<T>> {
    val exceptions = mutableListOf<Throwable>()
    val successes = mutableListOf<T>()
    for (result in this) {
        result.onSuccess { successes.add(it) }
        result.onFailure { exceptions.add(it) }
    }
    if (successes.isNotEmpty()) {
        return Result.success(successes)
    }
    
    val exception = NoSuchElementException("no success: " + exceptions.joinToString(", ") { it.message.toString() })
    for (exception in exceptions) {
        exception.addSuppressed(exception)
    }
    return Result.failure(exception)
}
