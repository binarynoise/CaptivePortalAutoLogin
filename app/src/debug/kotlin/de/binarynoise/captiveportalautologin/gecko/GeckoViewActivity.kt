package de.binarynoise.captiveportalautologin.gecko

import kotlin.concurrent.read
import kotlin.concurrent.write
import android.content.Intent
import android.net.Network
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Parcel
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.UiThread
import androidx.core.view.isVisible
import by.kirich1409.viewbindingdelegate.viewBinding
import de.binarynoise.captiveportalautologin.BuildConfig
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService.Companion.connectivityManager
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService.Companion.networkListeners
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService.Companion.networkState
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService.Companion.networkStateLock
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService.Companion.reportNetworkConnectivity
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService.Companion.serviceListeners
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService.Companion.serviceState
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService.Companion.serviceStateLock
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService.NetworkState
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService.ServiceState
import de.binarynoise.captiveportalautologin.R
import de.binarynoise.captiveportalautologin.Stats
import de.binarynoise.captiveportalautologin.api.json.har.HAR
import de.binarynoise.captiveportalautologin.databinding.ActivityGeckoviewBinding
import de.binarynoise.captiveportalautologin.json.toJson
import de.binarynoise.captiveportalautologin.preferences.SharedPreferences
import de.binarynoise.captiveportalautologin.util.FileUtils
import de.binarynoise.captiveportalautologin.util.FileUtils.saveTextToSd
import de.binarynoise.captiveportalautologin.util.mainHandler
import de.binarynoise.captiveportalautologin.util.postIfCreated
import de.binarynoise.logger.Logger.log
import org.mozilla.geckoview.GeckoSession

class GeckoViewActivity : ComponentActivity() {
    @get:UiThread
    private val binding by viewBinding { ActivityGeckoviewBinding.inflate(layoutInflater) }
    val backgroundHandler = Handler(HandlerThread("background").apply { start() }.looper)
    
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
            
            actionBar?.subtitle = url
            reportNetworkConnectivity()
        }
    }
    private val extensionDelegate =
        ExtensionDelegate(backgroundHandler, this, navigationDelegate, ::onExtensionLoaded, ::onExtensionDelegateError)
    
    val progressDelegate = object : GeckoSession.ProgressDelegate {
        override fun onPageStop(session: GeckoSession, success: Boolean) {
            log("onPageStop")
            binding.progress.isVisible = false
        }
        
        override fun onProgressChange(session: GeckoSession, progress: Int) {
            log("onProgressChange")
            binding.progress.progress = progress
        }
        
        override fun onPageStart(session: GeckoSession, url: String) {
            log("onPageStart")
            binding.progress.isVisible = true
        }
    }
    
    private val portalTestUrl by SharedPreferences.liberator_captive_test_url
    
    @Suppress("unused")
    private fun networkListener(oldState: NetworkState?, newState: NetworkState?) {
        mainHandler.postIfCreated {
            with(binding) {
                if (newState == null) {
                    notConnectedWarning.isVisible = true
                    notInCaptivePortalWifiWarning.isVisible = false
                    
                    return@postIfCreated
                }
                
                notConnectedWarning.isVisible = false
                notInCaptivePortalWifiWarning.isVisible = !newState.hasPortal
                
                if (newState.hasPortal) {
                    if (extensionDelegate.session.isOpen && (navigationDelegate.location == null || navigationDelegate.location == "about:blank")) {
                        extensionDelegate.session.loadUri(portalTestUrl.httpUrl.toString())
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
        
        extensionDelegate.onCreate(binding.geckoView)
        extensionDelegate.session.progressDelegate = progressDelegate
    }
    
    fun onExtensionLoaded() {
        log("onExtensionLoaded")
        networkListeners.add(::networkListener)
        networkListener(null, networkStateLock.read { networkState })
    }
    
    fun onExtensionDelegateError(exception: Throwable?) {
        Toast.makeText(this, "error occured: $exception", Toast.LENGTH_LONG).show()
        finish()
    }
    
    override fun onDestroy() {
        extensionDelegate.session.progressDelegate = null
        extensionDelegate.onDestroy(binding.geckoView)
        
        networkListeners.remove(::networkListener)
        serviceListeners.remove(::serviceListener)
        
        backgroundHandler.looper.quit()
        
        reportNetworkConnectivity()
        
        super.onDestroy()
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.gecko, menu)
        menu.add("Request Re-evaluation").also { menuItem ->
            menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            menuItem.setOnMenuItemClickListener {
                reportNetworkConnectivity()
                true
            }
        }
        if (BuildConfig.DEBUG) {
            fun addLoadSiteMenuEntry(menu: Menu, title: String, uri: String) {
                menu.add(title).also { menuItem ->
                    menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
                    menuItem.setOnMenuItemClickListener {
                        networkStateLock.write {
                            networkState = networkState?.copy(debug = true) ?: run {
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
                            extensionDelegate.session.loadUri(uri)
                        }
                        true
                    }
                }
            }
            
            addLoadSiteMenuEntry(menu, "about:config", "about:config")
            addLoadSiteMenuEntry(menu, "load 404", "http://am-i-captured.binarynoise.de/404")
            addLoadSiteMenuEntry(menu, "load 404 https", "https://am-i-captured.binarynoise.de/404")
            addLoadSiteMenuEntry(menu, "jstest", "http://dev.jeffersonscher.com/jstest.asp")
            addLoadSiteMenuEntry(menu, "Google", "https://google.com/")
        }
        return super.onCreateOptionsMenu(menu)
    }
    
    fun createFinalizedHar(): Pair<String, HAR> {
        return extensionDelegate.createFinalizedHar(
            networkStateLock.read { networkState?.ssid.toString() },
            portalTestUrl,
            true,
        )
    }
    
    override fun onMenuItemSelected(featureId: Int, item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_restart -> {
            startActivity(Intent(this, this::class.java))
            finish()
            true
        }
        R.id.action_reload_page -> {
            extensionDelegate.session.reload(GeckoSession.LOAD_FLAGS_BYPASS_CACHE)
            true
        }
        
        R.id.action_save -> {
            backgroundHandler.post {
                val toast = Toast.makeText(this, "Saving...", Toast.LENGTH_SHORT).apply { show() }
                
                val (name, har) = createFinalizedHar()
                val fileName = "${name}.har"
                
                try {
                    val json = har.toJson()
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
            val (name, har) = createFinalizedHar()
            val fileName = "${name}.har"
            try {
                val json = har.toJson()
                FileUtils.shareTextAsFile(json, fileName, "Share captured Portal", this, this)
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to share file: ${e.message}", Toast.LENGTH_LONG).show()
                log("Error sharing file $fileName", e)
            }
            true
        }
        R.id.action_upload -> {
            try {
                val (name, har) = createFinalizedHar()
                Stats.har.submitHar(name, har)
                Toast.makeText(this, "HAR scheduled for upload", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                log("Error uploading file", e)
                Toast.makeText(
                    this,
                    e::class.java.simpleName + ": " + e.message + "\n" + "Try saving to disk.",
                    Toast.LENGTH_SHORT,
                ).show()
            }
            true
        }
        
        android.R.id.home -> {
            onNavigateUp()
        }
        else -> {
            super.onMenuItemSelected(featureId, item)
        }
    }
    
    
    override fun shouldUpRecreateTask(targetIntent: Intent?): Boolean {
        return true
    }
}
