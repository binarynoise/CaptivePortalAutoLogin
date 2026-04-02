package de.binarynoise.captiveportalautologin

import android.net.CaptivePortal
import android.net.ConnectivityManager
import android.net.ConnectivityManager.EXTRA_CAPTIVE_PORTAL
import android.net.ConnectivityManager.EXTRA_NETWORK
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService.Companion.connectivityManager
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService.Companion.networkRequest
import de.binarynoise.captiveportalautologin.util.invokeSystemApiFunction
import de.binarynoise.logger.Logger.log

class RecordCaptivePortalActivity : ComponentActivity() {
    var captivePortal: CaptivePortal? = null
    lateinit var network: Network
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_record_captive_portal)
        
        captivePortal = IntentCompat.getParcelableExtra(intent, EXTRA_CAPTIVE_PORTAL, CaptivePortal::class.java)
        log("captivePortal = $captivePortal")
        network = IntentCompat.getParcelableExtra(intent, EXTRA_NETWORK, Network::class.java)!!
        log("network = $network")
        
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
    }
    
    val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(lostNetwork: Network) {
            if (lostNetwork != network) return
            finishAndRemoveTask()
        }
        
        override fun onCapabilitiesChanged(changedNetwork: Network, networkCapabilities: NetworkCapabilities) {
            if (changedNetwork != network) return
            if (networkCapabilities.hasCapability(NET_CAPABILITY_VALIDATED)) return success()
        }
    }
    
    override fun onBackPressed() {
        super.onBackPressed()
        TODO("handle back press")
    }
    
    fun reevaluateNetwork() {
        if (captivePortal == null) return
        invokeSystemApiFunction(CaptivePortal::class.java, captivePortal, "reevaluateNetwork")
    }
    
    fun dismiss() {
        captivePortal?.reportCaptivePortalDismissed()
        finishAndRemoveTask()
    }
    
    fun success() {
        TODO("implement user routine for asking whether to submit")
    }
}
