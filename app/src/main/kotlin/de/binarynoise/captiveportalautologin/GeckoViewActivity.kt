@file:OptIn(ExperimentalStdlibApi::class, ExperimentalTime::class)

package de.binarynoise.captiveportalautologin

import java.time.format.DateTimeFormatter
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Parcel
import android.view.Menu
import android.view.MenuItem
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import by.kirich1409.viewbindingdelegate.viewBinding
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService.Companion.networkListeners
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService.Companion.networkState
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService.Companion.networkStateLock
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService.Companion.serviceListeners
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService.Companion.serviceState
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService.Companion.serviceStateLock
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService.NetworkState
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService.ServiceState
import de.binarynoise.captiveportalautologin.api.json.har.Browser
import de.binarynoise.captiveportalautologin.api.json.har.Cache
import de.binarynoise.captiveportalautologin.api.json.har.Creator
import de.binarynoise.captiveportalautologin.api.json.har.Entry
import de.binarynoise.captiveportalautologin.api.json.har.HAR
import de.binarynoise.captiveportalautologin.api.json.har.Log
import de.binarynoise.captiveportalautologin.api.json.har.Request
import de.binarynoise.captiveportalautologin.api.json.har.Response
import de.binarynoise.captiveportalautologin.api.json.har.Timings
import de.binarynoise.captiveportalautologin.databinding.ActivityGeckoviewBinding
import de.binarynoise.captiveportalautologin.json.Request
import de.binarynoise.captiveportalautologin.json.Response
import de.binarynoise.captiveportalautologin.json.filter.FilterOnStopDetails
import de.binarynoise.captiveportalautologin.json.handleRequestHeaders
import de.binarynoise.captiveportalautologin.json.handleResponseHeaders
import de.binarynoise.captiveportalautologin.json.setContent
import de.binarynoise.captiveportalautologin.json.toJson
import de.binarynoise.captiveportalautologin.json.webRequest.OnAuthRequiredDetails
import de.binarynoise.captiveportalautologin.json.webRequest.OnBeforeRedirectDetails
import de.binarynoise.captiveportalautologin.json.webRequest.OnBeforeRequestDetails
import de.binarynoise.captiveportalautologin.json.webRequest.OnBeforeSendHeadersDetails
import de.binarynoise.captiveportalautologin.json.webRequest.OnCompletedDetails
import de.binarynoise.captiveportalautologin.json.webRequest.OnErrorOccurredDetails
import de.binarynoise.captiveportalautologin.json.webRequest.OnHeadersReceivedDetails
import de.binarynoise.captiveportalautologin.json.webRequest.OnResponseStartedDetails
import de.binarynoise.captiveportalautologin.json.webRequest.OnSendHeadersDetails
import de.binarynoise.captiveportalautologin.preferences.SharedPreferences
import de.binarynoise.captiveportalautologin.util.FileUtils
import de.binarynoise.captiveportalautologin.util.FileUtils.saveTextToSd
import de.binarynoise.captiveportalautologin.util.applicationContext
import de.binarynoise.captiveportalautologin.util.mainHandler
import de.binarynoise.captiveportalautologin.util.postIfCreated
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
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebExtension

private const val extensionPath = "resource://android/assets/extension/" + "captivePortalAutoLoginTrafficCapture/"
private const val extensionID = "captivePortalAutoLoginTrafficCapture@binarynoise.de"

class GeckoViewActivity : ComponentActivity() {
    @get:UiThread
    private val binding by viewBinding { ActivityGeckoviewBinding.inflate(layoutInflater) }
    
    object ContentDelegate : GeckoSession.ContentDelegate
    
    private val navigationDelegate = object : GeckoSession.NavigationDelegate {
        var location: String? = null
        override fun onLocationChange(
            session: GeckoSession,
            url: String?,
            perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
            hasUserGesture: Boolean,
        ) {
            log("onLocationChange: $url")
            location = url
        }
    }
    
    private val session = GeckoSession(GeckoSessionSettings.Builder().apply {
        usePrivateMode(true)
    }.build()).apply {
        contentDelegate = ContentDelegate
        navigationDelegate = object : GeckoSession.NavigationDelegate {
            var location: String? = null
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                log("onLocationChange: $url")
                location = url
            }
        }
    }
    
    private var extension: WebExtension? = null
    private val portalTestUrl by SharedPreferences.liberator_captive_test_url
    
    @Suppress("unused")
    private fun networkListener(oldState: NetworkState?, newState: NetworkState?) {
        mainHandler.postIfCreated {
            with(binding) {
                if (newState == null) {
                    notConnectedWarning.isVisible = true
                    notInCaptivePortalWifiWarning.isVisible = false
                    geckoView.visibility = INVISIBLE
                    
                    return@postIfCreated
                }
                
                notConnectedWarning.isVisible = false
                notInCaptivePortalWifiWarning.isVisible = !newState.hasPortal
                geckoView.visibility = if (newState.hasPortal || newState.debug) VISIBLE else INVISIBLE
                
                if (newState.hasPortal) {
                    if (session.isOpen && navigationDelegate.location == null || navigationDelegate.location == "about:blank") {
                        session.loadUri(portalTestUrl)
                    }
                } else {
                    if (session.isOpen) {
                        session.loadUri("about:blank")
                    }
                }
            }
        }
    }
    
    @Suppress("unused")
    private fun serviceListener(oldState: ServiceState?, newState: ServiceState) {
        mainHandler.postIfCreated {
            with(binding) {
                serviceNotRunningWarning.isVisible = !newState.running
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        
        serviceListeners.add(::serviceListener)
        serviceListener(null, serviceStateLock.read { serviceState })
        
        clearCache()
        
        session.open(runtime)
        binding.geckoView.setSession(session)
        session.loadUri("about:blank")
        
        fun handleError(it: Throwable) {
            log("Error installing extension", it)
            networkListeners.remove(::networkListener)
            serviceListeners.remove(::serviceListener)
            
            mainHandler.postIfCreated {
                with(binding) {
                    geckoError.isVisible = true
                    geckoView.isGone = true
                }
            }
        }
        
        try {
            val alwaysReload = true
            if (alwaysReload) {
                runtime.webExtensionController.installBuiltIn(extensionPath)
            } else {
                runtime.webExtensionController.ensureBuiltIn(extensionPath, extensionID)
            }.accept({ e ->
                extension = e!!
                log("Extension installed: ${e.id}")
                
                mainHandler.postIfCreated {
                    session.webExtensionController.setMessageDelegate(e, messageDelegate, "browser")
                    e.setMessageDelegate(messageDelegate, "browser")
                    
                    networkListeners.add(::networkListener)
                    networkListener(null, networkStateLock.read { networkState })
                }
            }, {
                handleError(it!!)
            })
        } catch (e: Exception) {
            handleError(e)
        }
    }
    
    private fun clearCache() {
        runtime.storageController.clearData(StorageController.ClearFlags.ALL)
    }
    
    override fun onDestroy() {
        messageDelegate.port?.disconnect()
        messageDelegate.port?.setDelegate(null)
        extension?.let {
            it.setMessageDelegate(null, "browser")
            session.webExtensionController.setMessageDelegate(it, null, "browser")
        }
        
        session.navigationDelegate = null
        session.contentDelegate = null
        
        binding.geckoView.releaseSession()
        session.close()
        
        clearCache()
        
        networkListeners.remove(::networkListener)
        serviceListeners.remove(::serviceListener)
        
        super.onDestroy()
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.gecko, menu)
        if (BuildConfig.DEBUG) {
            fun addLoadSiteMenuEntry(menu: Menu, title: String, uri: String) {
                menu.add(title).also { menuItem ->
                    menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
                    menuItem.setOnMenuItemClickListener {
                        networkStateLock.write {
                            networkState = networkState?.copy(debug = true) ?: run {
                                val connectivityManager =
                                    ContextCompat.getSystemService(this, ConnectivityManager::class.java)!!
                                val network = connectivityManager.activeNetwork ?: Network.CREATOR.createFromParcel(
                                    Parcel.obtain().apply { writeInt(-1) })
                                NetworkState(
                                    network,
                                    "debug",
                                    hasPortal = true,
                                    liberating = false,
                                    liberated = false,
                                    debug = true
                                )
                            }
                        }
                        mainHandler.post {
                            session.loadUri(uri)
                        }
                        true
                    }
                }
            }
            
            addLoadSiteMenuEntry(menu, "about:config", "about:config")
            addLoadSiteMenuEntry(menu, "load form", "http://am-i-captured.binarynoise.de/portal/")
            addLoadSiteMenuEntry(menu, "load form https", "https://am-i-captured.binarynoise.de/portal/")
//            addLoadSiteMenuEntry(menu, "test ws", "http://192.168.0.95:3000/")
            addLoadSiteMenuEntry(menu, "jstest", "http://dev.jeffersonscher.com/jstest.asp")
        }
        return super.onCreateOptionsMenu(menu)
    }
    
    override fun onMenuItemSelected(featureId: Int, item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_restart -> {
            startActivity(Intent(this, this::class.java))
            finish()
            true
        }
        R.id.action_reload_page -> {
            session.reload(GeckoSession.LOAD_FLAGS_BYPASS_CACHE)
            true
        }
        
        R.id.action_save -> {
            backgroundHandler.post {
                val toast = Toast.makeText(this, "Saving...", Toast.LENGTH_SHORT).apply { show() }
                
                prepareHar()
                val fileName = "${getHarName()}.har"
                val json = har.toJson()
                
                
                try {
                    saveTextToSd(json, fileName, "application/har+json", this)
                    
                    toast.cancel()
                    Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        this,
                        e::class.java.simpleName + ": " + e.message + "\n" + "Please try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                    log("Error saving file", e)
                }
            }
            true
        }
        R.id.action_share -> {
            prepareHar()
            val fileName = "${getHarName()}.har"
            val json = har.toJson()
            
            try {
                FileUtils.shareTextAsFile(json, fileName, "Share captured Portal", this, this)
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to share file: ${e.message}", Toast.LENGTH_LONG).show()
                log("Error sharing file $fileName", e)
            }
            true
        }
        R.id.action_upload -> {
            prepareHar()
            Stats.har.submitHar(getHarName(), har)
            Toast.makeText(this, "HAR scheduled for upload", Toast.LENGTH_SHORT).show()
            true
        }
        
        android.R.id.home -> {
            onNavigateUp()
        }
        else -> {
            super.onMenuItemSelected(featureId, item)
        }
    }
    
    private fun prepareHar() {
        val ssid = networkState?.ssid
        har.comment = ssid
    }
    
    private fun getHarName(): String {
        val ssid = networkState?.ssid
        val portalTestHost = portalTestUrl.toHttpUrl().host
        val host =
            har.log.entries.asSequence().map { it.request.url.toHttpUrl().host }.firstOrNull { it != portalTestHost }
                ?: portalTestHost
        val timestamp = java.time.Instant.now().let(DateTimeFormatter.ISO_INSTANT::format)
        return "$ssid $host $timestamp"
    }
    
    override fun shouldUpRecreateTask(targetIntent: Intent?): Boolean {
        return true
    }
    
    private val extensionConfig = mapOf(
        "routeToApp" to true,
        "stringify" to false,
        "blockWs" to true,
    )
    
    private val messageDelegate = object : WebExtension.MessageDelegate {
        var port: WebExtension.Port? = null
        
        override fun onMessage(nativeApp: String, message: Any, sender: WebExtension.MessageSender): GeckoResult<Any>? {
            if (message is JSONObject) {
                backgroundHandler.post { handleMessage(message) }
            } else {
                log("onMessage: else $message")
                message.dump("onMessage")
            }
            return null
        }
        
        override fun onConnect(port: WebExtension.Port) {
            log("onConnect: ${port.hashCode().toHexString(HexFormat.UpperCase)}")
            this.port = port
            port.setDelegate(portDelegate)
            port.postMessage(JSONObject(mapOf("event" to "config", "config" to extensionConfig)))
            log("onConnect: sent config")
        }
    }
    
    private val portDelegate = object : WebExtension.PortDelegate {
        override fun onDisconnect(port: WebExtension.Port) {
            log("onDisconnect: ${port.hashCode().toHexString(HexFormat.UpperCase)}")
        }
        
        override fun onPortMessage(message: Any, port: WebExtension.Port) {
            if (message is JSONObject) {
                backgroundHandler.post { handleMessage(message) }
            } else {
                log("onMessage: else $message")
                message.dump("onPortMessage")
            }
        }
    }
    
    private val creator = Creator("CaptivePortalAutoLogin", BuildConfig.VERSION_NAME)
    private val browser = Browser("Gecko", MOZILLA_VERSION)
    
    private val log = Log("1.2", creator, browser, mutableListOf(), mutableListOf())
    private val har = HAR(log)
    
    private val requestCache: MutableMap<String, Request> = mutableMapOf()
    private val responseCache: MutableMap<String, Response> = mutableMapOf()
    private val contentCache: MutableMap<String, String> = mutableMapOf()
    private val redirectCount: MutableMap<String, Int> = mutableMapOf()
    private val startTimeCache: MutableMap<String, LocalDateTime> = mutableMapOf()
    
    val backgroundHandler = Handler(HandlerThread("background").apply { start() }.looper)
    
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
//                    details.dump("details")
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
//                    responseCache[requestIdWithRedirectCount] = Response(onBeforeRedirectDetails)
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
            Toast.makeText(
                this,
                e::class.java.simpleName + ": " + e.message + "\n" + "Please restart.",
                Toast.LENGTH_SHORT,
            ).show()
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
//        log.dump(requestIdWithRedirectCount)
    }
    
    private fun getRequestIdWithRedirectCount(requestId: String) = "$requestId-${redirectCount[requestId] ?: 0}"
    
    companion object {
        private val geckoRuntimeSettings = GeckoRuntimeSettings.Builder().apply {
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
}
