package de.binarynoise.captiveportalautologin.gecko

import java.time.format.DateTimeFormatter
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import android.os.Handler
import androidx.annotation.WorkerThread
import androidx.lifecycle.LifecycleOwner
import de.binarynoise.captiveportalautologin.BuildConfig
import de.binarynoise.captiveportalautologin.api.json.har.Browser
import de.binarynoise.captiveportalautologin.api.json.har.Cache
import de.binarynoise.captiveportalautologin.api.json.har.Creator
import de.binarynoise.captiveportalautologin.api.json.har.Entry
import de.binarynoise.captiveportalautologin.api.json.har.HAR
import de.binarynoise.captiveportalautologin.api.json.har.Log
import de.binarynoise.captiveportalautologin.api.json.har.Request
import de.binarynoise.captiveportalautologin.api.json.har.Response
import de.binarynoise.captiveportalautologin.api.json.har.Timings
import de.binarynoise.captiveportalautologin.json.Request
import de.binarynoise.captiveportalautologin.json.Response
import de.binarynoise.captiveportalautologin.json.filter.FilterOnStopDetails
import de.binarynoise.captiveportalautologin.json.handleRequestHeaders
import de.binarynoise.captiveportalautologin.json.handleResponseHeaders
import de.binarynoise.captiveportalautologin.json.setContent
import de.binarynoise.captiveportalautologin.json.webRequest.OnAuthRequiredDetails
import de.binarynoise.captiveportalautologin.json.webRequest.OnBeforeRedirectDetails
import de.binarynoise.captiveportalautologin.json.webRequest.OnBeforeRequestDetails
import de.binarynoise.captiveportalautologin.json.webRequest.OnBeforeSendHeadersDetails
import de.binarynoise.captiveportalautologin.json.webRequest.OnCompletedDetails
import de.binarynoise.captiveportalautologin.json.webRequest.OnErrorOccurredDetails
import de.binarynoise.captiveportalautologin.json.webRequest.OnHeadersReceivedDetails
import de.binarynoise.captiveportalautologin.json.webRequest.OnResponseStartedDetails
import de.binarynoise.captiveportalautologin.json.webRequest.OnSendHeadersDetails
import de.binarynoise.captiveportalautologin.util.applicationContext
import de.binarynoise.captiveportalautologin.util.mainHandler
import de.binarynoise.captiveportalautologin.util.postIfCreated
import de.binarynoise.liberator.PortalTestURL
import de.binarynoise.logger.Logger.dump
import de.binarynoise.logger.Logger.log
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.BuildConfig.MOZILLA_VERSION
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebExtension


const val extensionPath = "resource://android/assets/extension/" + "captivePortalAutoLoginTrafficCapture/"
const val extensionID = "captivePortalAutoLoginTrafficCapture@binarynoise.de"

val extensionConfig = mapOf(
    "routeToApp" to true,
    "stringify" to false,
    "blockWs" to true,
)

class ExtensionDelegate(
    val backgroundHandler: Handler,
    val lifecycleOwner: LifecycleOwner,
    val navigationDelegate: GeckoSession.NavigationDelegate,
    val onExtensionLoaded: () -> Unit,
    val onError: (exception: Throwable?) -> Unit,
) : WebExtension.MessageDelegate, WebExtension.PortDelegate {
    var port: WebExtension.Port? = null
    
    // MessageDelegate
    override fun onMessage(nativeApp: String, message: Any, sender: WebExtension.MessageSender): GeckoResult<Any>? {
        if (message is JSONObject) {
            backgroundHandler.post {
                try {
                    handleMessage(message)
                } catch (e: Exception) {
                    log("Failed to handle message", e)
                }
            }
        } else {
            log("onMessage: else $message")
            message.dump("onMessage")
        }
        return null
    }
    
    // PortDelegate
    override fun onConnect(port: WebExtension.Port) {
        log("onConnect: ${port.hashCode().toHexString(HexFormat.UpperCase)}")
        this.port = port
        port.setDelegate(this)
        port.postMessage(JSONObject(mapOf("event" to "config", "config" to extensionConfig)))
        log("onConnect: sent config")
    }
    
    // PortDelegate
    override fun onDisconnect(port: WebExtension.Port) {
        log("onDisconnect: ${port.hashCode().toHexString(HexFormat.UpperCase)}")
        this.port = null
    }
    
    // PortDelegate
    override fun onPortMessage(message: Any, port: WebExtension.Port) {
        if (message is JSONObject) {
            backgroundHandler.post {
                try {
                    handleMessage(message)
                } catch (e: Exception) {
                    log("Failed to handle message", e)
                }
            }
        } else {
            log("onMessage: else $message")
            message.dump("onPortMessage")
        }
    }
    
    private var extension: WebExtension? = null
    
    val session = GeckoSession(GeckoSessionSettings.Builder().apply {
        usePrivateMode(true)
    }.build()).apply {
        contentDelegate = ContentDelegate
        navigationDelegate = this@ExtensionDelegate.navigationDelegate
    }
    
    fun onCreate(geckoView: GeckoView) {
        clearCache()
        
        session.open(runtime)
        geckoView.setSession(session)
        
        try {
            val alwaysReload = true
            if (alwaysReload) {
                runtime.webExtensionController.installBuiltIn(extensionPath)
            } else {
                runtime.webExtensionController.ensureBuiltIn(extensionPath, extensionID)
            }.accept({ e ->
                extension = e!!
                log("Extension installed: ${e.id}")
                
                context(lifecycleOwner) {
                    mainHandler.postIfCreated {
                        session.webExtensionController.setMessageDelegate(e, this, "browser")
                        e.setMessageDelegate(this, "browser")
                        onExtensionLoaded()
                    }
                }
                
            }, { onError(it) })
        } catch (e: Exception) {
            onError(e)
        }
    }
    
    fun onDestroy(geckoView: GeckoView) {
        port?.disconnect()
        port?.setDelegate(null)
        
        extension?.let {
            it.setMessageDelegate(null, "browser")
            session.webExtensionController.setMessageDelegate(it, null, "browser")
        }
        
        session.navigationDelegate = null
        session.contentDelegate = null
        geckoView.releaseSession()
        session.close()
        
        clearCache()
    }
    
    
    // HAR
    
    private val creator = Creator("CaptivePortalAutoLogin", BuildConfig.VERSION_NAME)
    private val browser = Browser("Gecko", MOZILLA_VERSION)
    
    private val log = Log("1.2", creator, browser, mutableListOf(), mutableListOf())
    private val har = HAR(log)
    
    
    private fun getRequestIdWithRedirectCount(requestId: String) = "$requestId-${redirectCount[requestId] ?: 0}"
    
    
    private val requestCache: MutableMap<String, Request> = mutableMapOf()
    private val responseCache: MutableMap<String, Response> = mutableMapOf()
    private val contentCache: MutableMap<String, String> = mutableMapOf()
    private val redirectCount: MutableMap<String, Int> = mutableMapOf()
    private val startTimeCache: MutableMap<String, LocalDateTime> = mutableMapOf()
    
    private var allowEdits: Boolean = true
    
    @WorkerThread
    fun handleMessage(message: JSONObject) {
        try {
            
            val eventType = message.opt("event")
            when (eventType) {
                null, JSONObject.NULL -> {
                    message.dump("message without event")
                    return
                }
                is String -> {
                }
                else -> {
                    eventType.dump("event")
                    return
                }
            }
            
            val details = message.opt("details")
            when (details) {
                null, JSONObject.NULL -> {
                    message.dump("message without details")
                    return
                }
                is JSONObject -> {
                    // details.dump("details")
                }
                is JSONArray -> {
                    if (details.length() == 1 && details.get(0) is String) {
                        log("details: ${details.get(0) as String}")
                    } else {
                        details.dump("details")
                    }
                    return
                }
                is String -> {
                    log("details: $details")
                    return
                }
                else -> {
                    details.dump("details")
                    return
                }
            }
            
            val requestId = details.optString("requestId")
            val requestIdWithRedirectCount = getRequestIdWithRedirectCount(requestId)
            
            log("event: $eventType requestId: $requestIdWithRedirectCount")
            
            if (!allowEdits) {
                log("dropping because allowEdits=false")
            }
            
            when (eventType) {
                "onBeforeRequest" -> {
                    val onBeforeRequestDetails = OnBeforeRequestDetails.fromJson(details)
                    requestCache[requestIdWithRedirectCount] = Request(onBeforeRequestDetails)
                    startTimeCache[requestIdWithRedirectCount] =
                        Instant.fromEpochMilliseconds(onBeforeRequestDetails.timeStamp)
                            .toLocalDateTime(TimeZone.currentSystemDefault())
                }
                "onBeforeSendHeaders" -> {
                    val onBeforeSendHeadersDetails = OnBeforeSendHeadersDetails.fromJson(details)
                    requestCache[requestIdWithRedirectCount]?.handleRequestHeaders(onBeforeSendHeadersDetails.requestHeaders)
                }
                "onSendHeaders" -> {
                    val onSendHeadersDetails = OnSendHeadersDetails.fromJson(details)
                    requestCache[requestIdWithRedirectCount]?.handleRequestHeaders(onSendHeadersDetails.requestHeaders)
                }
                "onHeadersReceived" -> {
                    val onHeadersReceivedDetails = OnHeadersReceivedDetails.fromJson(details)
                    responseCache[requestIdWithRedirectCount] = Response(onHeadersReceivedDetails)
                }
                "onResponseStarted" -> {
                    val onResponseStartedDetails = OnResponseStartedDetails.fromJson(details)
                    responseCache[requestIdWithRedirectCount]?.handleResponseHeaders(onResponseStartedDetails.responseHeaders)
                }
                "onCompleted" -> {
                    val onCompletedDetails = OnCompletedDetails.fromJson(details)
                    responseCache[requestIdWithRedirectCount]?.handleResponseHeaders(onCompletedDetails.responseHeaders)
                    finalizeResponse(requestIdWithRedirectCount)
                }
                "onAuthRequired" -> {
                    val onAuthRequiredDetails = OnAuthRequiredDetails.fromJson(details)
                    responseCache[requestIdWithRedirectCount] = Response(onAuthRequiredDetails)
                    finalizeResponse(requestIdWithRedirectCount, "")
                }
                "onBeforeRedirect" -> {
                    val onBeforeRedirectDetails = OnBeforeRedirectDetails.fromJson(details)
                    // responseCache[requestIdWithRedirectCount] = Response(onBeforeRedirectDetails)
                    responseCache[requestIdWithRedirectCount]?.handleResponseHeaders(onBeforeRedirectDetails.responseHeaders)
                    finalizeResponse(requestIdWithRedirectCount, "")
                    redirectCount[requestId] = (redirectCount[requestId] ?: 0) + 1
                }
                "onErrorOccurred" -> {
                    val onErrorDetails = OnErrorOccurredDetails.fromJson(details)
                    responseCache[requestIdWithRedirectCount] = Response(onErrorDetails)
                    finalizeResponse(requestIdWithRedirectCount, "")
                }
                // "filter.onData" -> {
                // }
                "filter.onStop" -> {
                    val filterOnStopDetails = FilterOnStopDetails.fromJson(details)
                    val response = responseCache[requestIdWithRedirectCount]
                    
                    if (response == null) {
                        contentCache[requestIdWithRedirectCount] = filterOnStopDetails.content
                        return
                    }
                    
                    finalizeResponse(requestIdWithRedirectCount, filterOnStopDetails.content)
                }
                else -> {
                    log("unknown event", NotImplementedError(eventType))
                    message.dump("message")
                    return
                }
            }
        } catch (e: Exception) {
            onError(e)
            log("handleMessage error", e)
        }
    }
    
    private fun finalizeResponse(
        requestIdWithRedirectCount: String,
        content: String? = contentCache[requestIdWithRedirectCount],
    ) {
        if (content == null) {
            log("content is null"); return
        }
        
        val request = requestCache[requestIdWithRedirectCount] ?: run { log("request is null"); return }
        
        val response = responseCache[requestIdWithRedirectCount] ?: run { log("response is null"); return }
        response.setContent(content)
        
        val startedDateTime = startTimeCache[requestIdWithRedirectCount] ?: return
        log.entries.add(
            Entry(
                null,
                startedDateTime,
                request,
                response,
                Cache(),
                Timings(),
                null,
                null,
            )
        )
        requestCache.remove(requestIdWithRedirectCount)
        responseCache.remove(requestIdWithRedirectCount)
        startTimeCache.remove(requestIdWithRedirectCount)
        contentCache.remove(requestIdWithRedirectCount)
        // log.dump(requestIdWithRedirectCount)
    }
    
    fun createFinalizedHar(ssid: String, portalTestURL: PortalTestURL, allowEdits: Boolean = false): Pair<String, HAR> {
        har.comment = ssid
        
        if (!allowEdits) this.allowEdits = false
        
        val portalTestHost = portalTestURL.httpUrl.host
        val host =
            har.log.entries.asSequence().map { it.request.url.toHttpUrl().host }.firstOrNull { it != portalTestHost }
                ?: portalTestHost
        val timestamp = java.time.Instant.now().let(DateTimeFormatter.ISO_INSTANT::format)
        val harName = "$ssid $host $timestamp"
        
        
        return harName to har
    }
    
    
    private fun clearCache() {
        runtime.storageController.clearData(StorageController.ClearFlags.ALL)
    }
    
    companion object {
        val geckoRuntimeSettings = GeckoRuntimeSettings.Builder().apply {
            allowInsecureConnections(GeckoRuntimeSettings.ALLOW_ALL)
            preferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM)
            remoteDebuggingEnabled(BuildConfig.DEBUG)
            consoleOutput(BuildConfig.DEBUG)
            aboutConfigEnabled(BuildConfig.DEBUG)
            globalPrivacyControlEnabled(false)
            contentBlocking(ContentBlocking.Settings.Builder().apply {
                safeBrowsing(ContentBlocking.SafeBrowsing.NONE)
                safeBrowsingProviders(/* none */)
            }.build())
            
            val configFilePath = applicationContext.filesDir.resolve("geckoview-config.yaml")
            applicationContext.assets.open("geckoview-config.yaml").use { inputStream ->
                configFilePath.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            configFilePath(configFilePath.absolutePath)
        }.build()
        
        // TODO: move to onCreate
        val runtime: GeckoRuntime = GeckoRuntime.create(applicationContext, geckoRuntimeSettings)

//        init {
//            runtime.shutdown() // TODO: move to onDestroy
//        }
    }
    
    object ContentDelegate : GeckoSession.ContentDelegate
}
