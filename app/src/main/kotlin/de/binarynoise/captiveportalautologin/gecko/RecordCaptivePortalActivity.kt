package de.binarynoise.captiveportalautologin.gecko

import kotlin.concurrent.read
import android.app.AlertDialog
import android.net.CaptivePortal
import android.net.ConnectivityManager
import android.net.ConnectivityManager.EXTRA_CAPTIVE_PORTAL
import android.net.ConnectivityManager.EXTRA_NETWORK
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.annotation.UiThread
import androidx.core.content.IntentCompat
import androidx.core.view.isVisible
import by.kirich1409.viewbindingdelegate.viewBinding
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService.Companion.connectivityManager
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService.Companion.networkRequest
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService.Companion.networkState
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService.Companion.networkStateLock
import de.binarynoise.captiveportalautologin.Stats
import de.binarynoise.captiveportalautologin.api.json.har.HAR
import de.binarynoise.captiveportalautologin.databinding.ActivityRecordCaptivePortalBinding
import de.binarynoise.captiveportalautologin.preferences.SharedPreferences
import de.binarynoise.captiveportalautologin.util.invokeSystemApiFunction
import de.binarynoise.logger.Logger.log
import org.mozilla.geckoview.GeckoSession

class RecordCaptivePortalActivity : ComponentActivity() {
    @get:UiThread
    private val binding by viewBinding { ActivityRecordCaptivePortalBinding.inflate(layoutInflater) }
    val backgroundHandler = Handler(HandlerThread("background").apply { start() }.looper)
    
    private val portalTestUrl by SharedPreferences.liberator_captive_test_url
    
    var captivePortal: CaptivePortal? = null
    lateinit var network: Network
    var networkHasPortal = false
    
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
            actionBar?.subtitle = url.takeUnless { it == "about:blank" }
            reevaluateNetwork()
        }
        
        override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
            onBackPressedCallback.isEnabled = canGoBack
        }
    }
    
    val extensionDelegate =
        ExtensionDelegate(backgroundHandler, this, navigationDelegate, ::onExtensionLoaded, ::onExtensionDelegateError)
    
    val progressDelegate = object : GeckoSession.ProgressDelegate {
        override fun onPageStop(session: GeckoSession, success: Boolean) {
            log("onPageStop")
            binding.swipeRefresh.isRefreshing = false
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
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.swipeRefresh.isEnabled = false
        
        captivePortal = IntentCompat.getParcelableExtra(intent, EXTRA_CAPTIVE_PORTAL, CaptivePortal::class.java)
        log("captivePortal = $captivePortal")
        network = IntentCompat.getParcelableExtra(intent, EXTRA_NETWORK, Network::class.java)!!
        log("network = $network")
        
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        
        onBackPressedDispatcher.addCallback(onBackPressedCallback)
        
        extensionDelegate.onCreate(binding.geckoView)
        extensionDelegate.session.progressDelegate = progressDelegate
        binding.swipeRefresh.setOnRefreshListener { extensionDelegate.session.reload() }
    }
    
    fun createFinalizedHar(): Pair<String, HAR> {
        return extensionDelegate.createFinalizedHar(
            networkStateLock.read { networkState?.ssid.toString() },
            portalTestUrl,
        )
    }
    
    val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(lostNetwork: Network) {
            if (lostNetwork != network) return
            finishAndRemoveTask()
        }
        
        override fun onCapabilitiesChanged(changedNetwork: Network, networkCapabilities: NetworkCapabilities) {
            if (changedNetwork != network) return
            if (networkCapabilities.hasCapability(NET_CAPABILITY_CAPTIVE_PORTAL)) networkHasPortal = true
            if (networkCapabilities.hasCapability(NET_CAPABILITY_VALIDATED)) return success()
        }
    }
    
    val onBackPressedCallback: OnBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            extensionDelegate.session.goBack()
        }
    }
    
    fun onExtensionLoaded() {
        extensionDelegate.session.loadUri(portalTestUrl.httpUrl.toString())
        binding.swipeRefresh.isEnabled = true
    }
    
    fun onExtensionDelegateError(exception: Throwable?) {
        Toast.makeText(this, "Exception occured: $exception", Toast.LENGTH_LONG).show()
        finishAndRemoveTask()
    }
    
    override fun onDestroy() {
        extensionDelegate.onDestroy(binding.geckoView)
        backgroundHandler.looper.quit()
        connectivityManager.unregisterNetworkCallback(networkCallback)
        super.onDestroy()
    }
    
    fun reevaluateNetwork() {
        if (captivePortal != null) {
            invokeSystemApiFunction(CaptivePortal::class.java, captivePortal, "reevaluateNetwork")
        } else {
            ConnectivityChangeListenerService.reportNetworkConnectivity(network, true)
        }
    }
    
    fun dismiss() {
        captivePortal?.reportCaptivePortalDismissed()
        finishAndRemoveTask()
    }
    
    fun success() {
        if (!networkHasPortal) return dismiss()
        AlertDialog.Builder(this).setTitle("Submit recording?").setMessage(
            "Do you want to submit the recording of this captive portal? \n" + "Please do not share this network if you entered any kind of personal data or passwords."
        ).setPositiveButton("Yes") { _, _ ->
            val (name, har) = createFinalizedHar()
            Stats.har.submitHar(name, har)
            Toast.makeText(this, "Thanks for contributing :)", Toast.LENGTH_SHORT).show()
        }.setNegativeButton("No") { _, _ ->
            Toast.makeText(this, "Discarded recorded data :(", Toast.LENGTH_SHORT).show()
        }.setCancelable(false).setOnDismissListener {
            dismiss()
        }.show()
    }
}
