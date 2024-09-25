package de.binarynoise.captiveportalautologin

import java.util.*
import java.util.concurrent.locks.*
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.properties.Delegates
import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_CANCEL_CURRENT
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.annotation.GuardedBy
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService.SsidCompat.connectivityManager
import de.binarynoise.captiveportalautologin.preferences.SharedPreferences
import de.binarynoise.captiveportalautologin.util.BackgroundHandler
import de.binarynoise.captiveportalautologin.util.applicationContext
import de.binarynoise.captiveportalautologin.util.mainHandler
import de.binarynoise.liberator.Liberator
import de.binarynoise.liberator.cast
import de.binarynoise.liberator.tryOrNull
import de.binarynoise.logger.Logger.log

class ConnectivityChangeListenerService : Service() {
    
    val backgroundHandler = BackgroundHandler("ConnectivityChangeListenerService")
    
    private var notification: Notification? = null
    private val channelId = "ConnectivityChangeListenerService"
    
    private fun bindNetworkToProcess(oldState: NetworkState?, newState: NetworkState?) {
        if (oldState?.network == newState?.network) return
        
        val success = connectivityManager.bindProcessToNetwork(newState?.network)
        log(buildString {
            append(if (newState?.network != null) "bound to" else "unbound from")
            append(" network ")
            append(newState?.network ?: oldState?.network)
            append(": ")
            append(if (success) "success" else "failed")
        })
    }
    
    @Suppress("UNUSED_PARAMETER")
    private fun updateNofication(oldState: NetworkState?, newState: NetworkState?) {
        NotificationCompat.Builder(applicationContext, notification!!).setContentText(newState?.toString() ?: "Running in background")
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    @MainThread
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log("onStartCommand")
        
        serviceStateLock.write {
            if (serviceState.running) {
                if (intent != null && intent.extras != null) {
                    if (intent.hasExtra("retry") && intent.extras!!.getBoolean("retry")) {
                        val network = networkStateLock.read { networkState?.network }
                        if (network != null) {
                            backgroundHandler.post(::tryLiberate)
                        } else {
                            Toast.makeText(this, "Not caught in a portal", Toast.LENGTH_SHORT).show()
                        }
                    }
                    if (intent.hasExtra("restart") && intent.extras!!.getBoolean("restart")) {
                        serviceState = ServiceState(running = true, restart = true)
                        stopSelf()
                    }
                }
                return START_STICKY
            }
            serviceState = ServiceState(running = true, restart = false)
        }
        
        createNotificationChannel(channelId, "Persistent Notification")
        notification = createNotification(channelId) {
            // start HomeActivity on click
            val launchHomeIntent = Intent().apply { component = ComponentName.createRelative(application.packageName, ".HomeActivity") }
            launchHomeIntent.addFlags(FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK)
            it.setContentIntent(PendingIntent.getActivity(this, 0, launchHomeIntent, FLAG_CANCEL_CURRENT or FLAG_IMMUTABLE))
            
            // add button to try liberating again
            val retryIntent = Intent(this, this::class.java)
            retryIntent.putExtra("retry", true)
            val pendingRetryIntent = PendingIntent.getService(this, 0, retryIntent, FLAG_CANCEL_CURRENT or FLAG_IMMUTABLE)
            it.addAction(NotificationCompat.Action.Builder(null, "Try again", pendingRetryIntent).build())
            
            val captureIntent = Intent(this, GeckoViewActivity::class.java)
            val pendingCaptureIntent = PendingIntent.getActivity(this, 0, captureIntent, FLAG_CANCEL_CURRENT or FLAG_IMMUTABLE)
            it.addAction(NotificationCompat.Action.Builder(null, "Capture portal", pendingCaptureIntent).build())
            
            it.setOnlyAlertOnce(true)
        }
        
        ServiceCompat.startForeground(
            this, 1, notification!!,
            if (Build.VERSION.SDK_INT >= 29) FOREGROUND_SERVICE_TYPE_MANIFEST else 0,
        )
        
        networkListeners.add(::bindNetworkToProcess)
        if (BuildConfig.DEBUG) networkListeners.add(::updateNofication)
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        
        log("started")
        return START_STICKY
    }
    
    private fun createNotification(channelId: String, initializer: (NotificationCompat.Builder) -> Unit = {}): Notification =
        NotificationCompat.Builder(this, channelId)
            .setContentTitle("Captive Portal detection")
            .setContentText("Running in background")
            .setSmallIcon(R.drawable.wifi_lock_open)
            .apply(initializer)
            .build()
    
    private fun createNotificationChannel(channelId: String, name: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_MIN)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        log("onDestroy")
        
        serviceStateLock.write {
            serviceState = serviceState.copy(running = false)
            
            connectivityManager.unregisterNetworkCallback(networkCallback)
            networkState = null
            networkListeners.remove(::bindNetworkToProcess)
            networkListeners.remove(::updateNofication)
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            notification?.actions?.forEach { it.actionIntent?.cancel() }
            notification = null
            
            backgroundHandler.looper.quit()
            backgroundHandler.looper.thread.interrupt()
            
            log("stopped")
            
            if (serviceState.restart) {
                mainHandler.post {
                    ContextCompat.startForegroundService(
                        applicationContext,
                        Intent(applicationContext, ConnectivityChangeListenerService::class.java),
                    )
                }
                log("scheduled restart")
            }
        }
    }
    
    private val networkRequest = NetworkRequest.Builder().apply {
        if (Build.VERSION.SDK_INT >= 31) {
            setIncludeOtherUidNetworks(true)
        }
        addCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
    }.build()
    private val networkCallback = createNetworkCallback()
    
    private fun createNetworkCallback(): ConnectivityManager.NetworkCallback {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                log("onAvailable: $network")
                updateNetworkState(network)
            }
            
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                log("onCapabilitiesChanged: $network")
                updateNetworkState(network, networkCapabilities)
            }
            
            override fun onLost(network: Network) = networkStateLock.write {
                log("onUnavailable: ${network}")
                if (networkState?.network == network) networkState = null
            }
        }
        
        if (Build.VERSION.SDK_INT >= 31) {
            return NetworkCallback31(callback)
        } else return callback
    }
    
    fun updateNetworkState(network: Network, networkCapabilities: NetworkCapabilities? = null) {
        val ssid = SsidCompat.getSsid(network, networkCapabilities)
        if (ssid == null) {
            log("SSID: null")
            return
        }
        
        networkStateLock.write {
            if (networkState?.network == network) return
            
            log("SSID: $ssid")
            networkState = NetworkState(network, ssid, false, false)
        }
        
        val liberateAutomatically: Boolean by SharedPreferences.liberator_automatically_liberate
        if (!liberateAutomatically) {
            log("not liberating automatically")
            return
        }
        
        backgroundHandler.post(::tryLiberate)
    }
    
    @WorkerThread
    fun tryLiberate() {
        val network = networkStateLock.write {
            val state = networkState
            if (state == null) {
                log("no network")
                return
            }
            if (state.liberated) {
                log("already liberated")
                return
            }
            if (state.liberating) {
                log("already liberating")
                return
            }
            networkState = state.copy(liberating = true)
            state.network
        }
        
        val t = Toast.makeText(applicationContext, "Trying to liberate", Toast.LENGTH_LONG)
        t.show()
        
        try {
            val userAgent: String by SharedPreferences.liberator_user_agent
            val portalTestUrl: String by SharedPreferences.liberator_captive_test_url
            
            val (newLocation, tried) = Liberator(
                portalTestUrl,
                userAgent,
                { okhttpClient -> okhttpClient.socketFactory(network.socketFactory) },
            ).liberate()
            
            if (newLocation == null) {
                if (tried) {
                    Toast.makeText(applicationContext, "Free at last!", Toast.LENGTH_SHORT).show()
                    t.cancel()
                    log("broke out of the portal")
                    networkStateLock.write {
                        networkState = networkState?.copy(liberated = true)
                    }
                } else {
                    log("not caught in portal")
                    t.cancel()
                    Toast.makeText(applicationContext, "Failed to liberate: not caught in portal", Toast.LENGTH_LONG).show()
                }
            } else {
                log("Failed to liberate: still in portal: $newLocation")
                t.cancel()
                Toast.makeText(applicationContext, "Failed to liberate: still in portal: $newLocation", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            log("failed to liberate", e)
            val message = e.message ?: e.localizedMessage ?: "no error message"
            t.cancel()
            Toast.makeText(applicationContext, "Failed to liberate: ${e::class.simpleName} - $message", Toast.LENGTH_LONG).show()
        } finally {
            networkStateLock.write {
                networkState = networkState?.copy(liberating = false)
            }
        }
    }
    
    // FLAG_INCLUDE_LOCATION_INFO not avaliable pre API 31
    @TargetApi(31)
    class NetworkCallback31(val wrapped: ConnectivityManager.NetworkCallback) : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
        //<editor-fold defaultstate="collapsed" desc="delegates">
        override fun onAvailable(network: Network) {
            wrapped.onAvailable(network)
        }
        
        override fun onLosing(network: Network, maxMsToLive: Int) {
            wrapped.onLosing(network, maxMsToLive)
        }
        
        override fun onLost(network: Network) {
            wrapped.onLost(network)
        }
        
        override fun onUnavailable() {
            wrapped.onUnavailable()
        }
        
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            wrapped.onCapabilitiesChanged(network, networkCapabilities)
        }
        
        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            wrapped.onLinkPropertiesChanged(network, linkProperties)
        }
        
        override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
            wrapped.onBlockedStatusChanged(network, blocked)
        }
        //</editor-fold>
    }
    
    object SsidCompat {
        val wifiManager by lazy { ContextCompat.getSystemService(applicationContext, WifiManager::class.java)!! }
        val connectivityManager by lazy { ContextCompat.getSystemService(applicationContext, ConnectivityManager::class.java)!! }
        
        const val UNKNOWN_SSID = "<unknown ssid>"
        
        fun getSsid(network: Network, networkCapabilities: NetworkCapabilities?): String? {
            var ssid: String?
            
            // pre API 29, but try anyway
            ssid = tryOrNull { @Suppress("DEPRECATION") wifiManager.connectionInfo.ssid.takeIf { it != UNKNOWN_SSID }?.let(::decodeSsid) }
            if (ssid != null) {
                log("got ssid from wifiManager.connectionInfo.ssid: $ssid")
                return ssid
            }
            
            // API 29+, doesn't always receive ssid on first try
            if (Build.VERSION.SDK_INT >= 29) {
                ssid = tryOrNull {
                    val capabilities = networkCapabilities ?: connectivityManager.getNetworkCapabilities(network)
                    val wifiInfo = capabilities?.transportInfo?.cast<WifiInfo?>()
                    wifiInfo?.ssid?.takeIf { it != UNKNOWN_SSID }?.let(::decodeSsid)
                }
                if (ssid != null) {
                    log("got ssid from networkCapabilities: $ssid")
                    return ssid
                }
            }
            
            return null
        }
        
        fun decodeSsid(ssid: String): String {
            if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
                return ssid.substring(1, ssid.length - 1)
            }
            return "0x" + ssid
        }
    }
    
    companion object {
        val serviceListeners: MutableSet<(oldState: ServiceState, newState: ServiceState) -> Unit> = SynchronizedSet()
        val serviceStateLock = ReentrantReadWriteLock(true)
        
        @delegate:GuardedBy("serviceStateLock")
        var serviceState: ServiceState by Delegates.observable(ServiceState(false, false)) { _, oldState, newState ->
            if (oldState != newState) serviceStateLock.read {
                log("notifying ${serviceListeners.size} serviceListeners...")
                serviceListeners.forEach { it(oldState, newState) }
            }
        }
            private set
        
        val networkListeners: MutableSet<(oldState: NetworkState?, newState: NetworkState?) -> Unit> = SynchronizedSet()
        val networkStateLock = ReentrantReadWriteLock(true)
        
        @delegate:GuardedBy("networkStateLock")
        var networkState: NetworkState? by Delegates.observable<NetworkState?>(null) { _, oldState, newState ->
            if (oldState != newState) {
                log("notifying ${networkListeners.size} networkListeners...")
                networkListeners.forEach { it(oldState, newState) }
            }
        }
        
        fun start() = serviceStateLock.read {
            if (serviceState.running || serviceState.restart) return
            arrayOf(Permissions.fineLocation, Permissions.backgroundLocation).forEach { permission ->
                if (!permission.granted(applicationContext)) {
                    val permissionName = permission.name ?: applicationContext.getString(permission.nameRes ?: 0)
                    log("Permission not granted: $permissionName")
                    Toast.makeText(applicationContext, "Tried to start service without permission $permissionName", Toast.LENGTH_LONG).show()
                    return
                }
            }
            ContextCompat.startForegroundService(applicationContext, Intent(applicationContext, ConnectivityChangeListenerService::class.java))
        }
        
        fun stop() {
            applicationContext.stopService(Intent(applicationContext, ConnectivityChangeListenerService::class.java))
        }
        
        fun restart() = serviceStateLock.read {
            if (serviceState.running) {
                val intent = Intent(applicationContext, ConnectivityChangeListenerService::class.java)
                intent.putExtra("restart", true)
                applicationContext.startService(intent)
            } else start()
        }
        
        init {
            networkListeners.add(::logNetwork)
            serviceListeners.add(::logService)
        }
        
        private fun logNetwork(oldState: NetworkState?, newState: NetworkState?) {
            log("Network changed: $oldState -> $newState")
        }
        
        private fun logService(oldState: ServiceState, newState: ServiceState) {
            log("Service changed: $oldState -> $newState")
        }
    }
    
    data class ServiceState(
        val running: Boolean, val restart: Boolean
    ) {
        override fun toString(): String = buildString {
            append("Service is currently ")
            if (running) {
                append("running")
            } else {
                append("not running")
            }
            if (restart) {
                append(" and will be restarted")
            }
        }
    }
    
    data class NetworkState(
        val network: Network,
        val ssid: String,
        val liberating: Boolean,
        val liberated: Boolean,
    ) {
        override fun toString(): String = buildString {
            append("Network $network with SSID $ssid is currently ")
            if (liberating) {
                append("liberating")
            } else if (liberated) {
                append("liberated")
            } else {
                append("not liberating")
            }
        }
    }
}

fun <T> SynchronizedSet(): MutableSet<T> = Collections.synchronizedSet(mutableSetOf())
