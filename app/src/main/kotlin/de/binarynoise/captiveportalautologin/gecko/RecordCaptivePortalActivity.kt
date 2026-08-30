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
import android.os.Build
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
import de.binarynoise.captiveportalautologin.R
import de.binarynoise.captiveportalautologin.ScheduledApiClient
import de.binarynoise.captiveportalautologin.api.json.har.HAR
import de.binarynoise.captiveportalautologin.databinding.ActivityRecordCaptivePortalBinding
import de.binarynoise.captiveportalautologin.preferences.SharedPreferences
import de.binarynoise.captiveportalautologin.preferences.SystemPortalTestUrl
import de.binarynoise.captiveportalautologin.preferences.SystemPortalUserAgent
import de.binarynoise.captiveportalautologin.util.getHiddenStaticFieldValue
import de.binarynoise.captiveportalautologin.util.invokeHiddenMethod
import de.binarynoise.liberator.PortalTestURL
import de.binarynoise.liberator.tryOrIgnore
import de.binarynoise.logger.Logger.log
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.mozilla.geckoview.GeckoSession

class RecordCaptivePortalActivity : ComponentActivity() {
    @get:UiThread
    private val binding by viewBinding { ActivityRecordCaptivePortalBinding.inflate(layoutInflater) }
    val backgroundHandler = Handler(HandlerThread("background").apply { start() }.looper)
    
    private var portalTestUrl = SharedPreferences.liberator_captive_test_url.get()
    
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
        network = IntentCompat.getParcelableExtra(intent, EXTRA_NETWORK, Network::class.java) ?: run {
            log("$EXTRA_NETWORK is missing")
            finish()
            return
        }
        log("network = $network")
        
        if (SharedPreferences.liberator_user_agent.get() == SystemPortalUserAgent) {
            val userAgent = intent.getStringExtra(
                ConnectivityManager::class.java.getHiddenStaticFieldValue("EXTRA_CAPTIVE_PORTAL_USER_AGENT") as String
            )
            if (userAgent != null) extensionDelegate.session.settings.userAgentOverride = userAgent
        }
        
        if (portalTestUrl == SystemPortalTestUrl) {
            // only use android's provided captivePortalUrl if the user hasn't overridden the url in the settings
            val captivePortalUrl =
                intent.getStringExtra(ConnectivityManager.EXTRA_CAPTIVE_PORTAL_URL)?.toHttpUrlOrNull()
            if (captivePortalUrl != null) portalTestUrl = PortalTestURL(captivePortalUrl)
        }
        
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        
        onBackPressedDispatcher.addCallback(onBackPressedCallback)
        
        extensionDelegate.onCreate(binding.geckoView)
        extensionDelegate.session.progressDelegate = progressDelegate
        binding.swipeRefresh.setOnRefreshListener {
            reevaluateNetwork()
            extensionDelegate.session.reload()
        }
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
        Toast.makeText(this, getString(R.string.exception_occurred) + exception, Toast.LENGTH_LONG).show()
        finishAndRemoveTask()
    }
    
    override fun onDestroy() {
        extensionDelegate.onDestroy(binding.geckoView)
        backgroundHandler.looper.quit()
        tryOrIgnore {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
        super.onDestroy()
    }
    
    fun reevaluateNetwork() {
        if (captivePortal != null //
            && Build.VERSION.SDK_INT <= Build.VERSION_CODES.UPSIDE_DOWN_CAKE // from V onwards this request is silently ignored
            && !Build.ID.startsWith("AP2A") // only QPR3 throws a silent server-side SecurityException
            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R // before R the method didn't exist yet
        ) {
            captivePortal!!.invokeHiddenMethod("reevaluateNetwork")
        } else {
            ConnectivityChangeListenerService.reportNetworkConnectivity(network, true)
        }
    }
    
    var done = false
    
    fun dismiss() {
        done = true
        captivePortal?.reportCaptivePortalDismissed()
        finishAndRemoveTask()
    }
    
    fun success() {
        if (!networkHasPortal) return dismiss()
        if (done) return
        done = true
        AlertDialog.Builder(this)
            .setTitle(R.string.submit_portal)
            .setMessage(getString(R.string.submit_portal_description))
            .setPositiveButton(android.R.string.yes) { _, _ ->
                val (name, har) = createFinalizedHar()
                ScheduledApiClient.har.submitHar(name, har)
                Toast.makeText(this, R.string.submit_portal_confirmed, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.no) { _, _ ->
                Toast.makeText(this, R.string.submit_portal_aborted, Toast.LENGTH_SHORT).show()
            }
            .setCancelable(false)
            .setOnDismissListener {
                dismiss()
            }
            .show()
    }
}
