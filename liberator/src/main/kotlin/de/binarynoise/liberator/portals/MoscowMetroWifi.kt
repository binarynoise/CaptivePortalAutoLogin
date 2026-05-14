package de.binarynoise.liberator.portals

import de.binarynoise.liberator.Experimental
import de.binarynoise.liberator.LiberatorExtras
import de.binarynoise.liberator.PortalLiberator
import de.binarynoise.liberator.SSID
import de.binarynoise.logger.Logger.log
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.postForm
import de.binarynoise.util.okhttp.readText
import de.binarynoise.util.okhttp.requestUrl
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

/**
 * Moscow Metro / Maxima Telecom captive portal (auth.wi-fi.ru)
 *
 * Auth flow: GET /gapi/auth/start (establishes session with device MAC from captive portal redirect)
 *         -> POST /gapi/auth/init or /gapi/auth/init_smart (triggers MAC-based auth, no ads required)
 *         -> GET /gapi/auth/check (poll until auth_status == "success")
 */
@Experimental
@SSID("MT_FREE", "_MosMetro_Free", "MosMetro_Free", "MTC_FREE")
object MoscowMetroWifi : PortalLiberator {

    private val json = Json { ignoreUnknownKeys = true }

    override fun canSolve(response: Response): Boolean {
        return response.requestUrl.host == "auth.wi-fi.ru"
    }

    override fun solve(client: OkHttpClient, response: Response, extras: LiberatorExtras) {
        log("start: requestUrl=${response.requestUrl}")

        val startResponse = makeStartRequest(client, response)

        val initResult = makeInitRequest(client, response, startResponse.data)
        check(initResult.authStatus != "fail") { "Auth init failed: ${initResult.authErrorCode}" }

        for (attempt in 1..10) {
            val result = checkAuthResult(client, response.requestUrl, startResponse.data)
            when (result) {
                true -> break
                false -> log("Failed to check auth result in $attempt")
                null -> Thread.sleep(1_000)
            }
        }
        error("Auth timed out after 10 attempts")
    }

    private fun makeStartRequest(
        client: OkHttpClient,
        response: Response,
    ): StartResponse {
        val request = client.get(response.requestUrl, "/gapi/auth/start").readText()
        val result = json.decodeFromString<StartResponse>(request)

        val segment = result.data.segment
        val mac = result.data.userParams?.mac
        val stage = result.data.authProcessParams?.stage
        val smartClatterEnabled = result.data.segmentParams.auth.smartClatterEnabled
        log("start: segment=$segment mac=$mac stage=$stage smartClatterEnabled=$smartClatterEnabled")

        return result
    }

    private fun makeInitRequest(
        client: OkHttpClient,
        response: Response,
        data: StartData,
    ): AuthResponse {
        val initPath = if (data.segmentParams.auth.smartClatterEnabled) {
            "/gapi/auth/init_smart"
        } else {
            "/gapi/auth/init"
        }
        val formParams = mapOf(
            "mode" to "0",
            "segment" to data.segment,
        )
        log("init: POST $initPath segment=${data.segment}")

        val request = client.postForm(
            base = response.requestUrl,
            url = initPath,
            form = formParams,
        ).readText()
        val result = json.decodeFromString<AuthResponse>(request)

        log("init: auth_status=${result.authStatus} auth_error_code=${result.authErrorCode} user_mac=${result.userMac}")

        return result
    }

    private fun checkAuthResult(client: OkHttpClient, responseUrl: HttpUrl, data: StartData): Boolean? {
        val checkResult = json.decodeFromString<AuthResponse>(
            client.get(
                base = responseUrl,
                url = "/gapi/auth/check",
                queryParameters = mapOf("segment" to data.segment),
            ).readText(),
        )
        return when (checkResult.authStatus) {
            "success" -> true
            "fail" -> false
            else -> null
        }
    }

    @Serializable
    private data class StartResponse(val data: StartData)

    @Serializable
    private data class StartData(
        val segment: String,
        val userParams: UserParams? = null,
        val authProcessParams: AuthProcessParams? = null,
        val segmentParams: SegmentParams,
    )

    @Serializable
    private data class UserParams(val mac: String? = null)

    @Serializable
    private data class AuthProcessParams(val stage: String? = null)

    @Serializable
    private data class SegmentParams(val auth: AuthParams)

    @Serializable
    private data class AuthParams(val smartClatterEnabled: Boolean = false)

    @Serializable
    private data class AuthResponse(
        @SerialName("auth_status") val authStatus: String? = null,
        @SerialName("auth_error_code") val authErrorCode: String? = null,
        @SerialName("user_mac") val userMac: String? = null,
    )
}
