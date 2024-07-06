package de.binarynoise.captiveportalautologin

import java.util.*
import java.util.concurrent.locks.*
import kotlin.concurrent.withLock
import android.Manifest.permission
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_CANCEL_CURRENT
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.Service
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
import android.net.ConnectivityManager
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
    
    private fun bindNetworkToProcess(state: NetworkState, available: Boolean) {
        val network = state.network
        val success = connectivityManager.bindProcessToNetwork(if (available) network else null)
        log((if (available) "bound to" else "unbound from") + " network $network: ${if (success) "success" else "failed"}")
    }
    
    private fun updateNofication(state: NetworkState, available: Boolean) {
        // TODO update notification text with current state
    }
    
    init {
        networkListeners.add(::bindNetworkToProcess)
        if (BuildConfig.DEBUG) networkListeners.add(::updateNofication)
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    @MainThread
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log("onStartCommand")
        
        serviceStateLock.withLock {
            if (serviceState.running) {
                if (intent != null && intent.extras != null) {
                    if (intent.hasExtra("retry") && intent.extras!!.getBoolean("retry")) {
                        val network = networkState?.network
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
        
        createNotificationChannel(channelId, getString(R.string.foreground_notification_channel_name))
        notification = createNotification(channelId) {
            // start MainActivity on click
            val launchMainIntent = Intent(this, MainActivity::class.java)
            launchMainIntent.addFlags(FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK)
            it.setContentIntent(PendingIntent.getActivity(this, 0, launchMainIntent, FLAG_CANCEL_CURRENT or FLAG_IMMUTABLE))
            
            // add button to try liberating again
            val retryIntent = Intent(this, this::class.java)
            retryIntent.putExtra("retry", true)
            val pendingRetryIntent = PendingIntent.getService(this, 0, retryIntent, FLAG_CANCEL_CURRENT or FLAG_IMMUTABLE)
            it.addAction(NotificationCompat.Action.Builder(null, "Try again", pendingRetryIntent).build())
        }
        
        ServiceCompat.startForeground(
            this, 1, notification!!,
            if (Build.VERSION.SDK_INT >= 29) FOREGROUND_SERVICE_TYPE_MANIFEST else 0,
        )
        
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        
        notifyServiceListeners()
        
        log("started")
        return START_STICKY
    }
    
    private fun createNotification(channelId: String, initializer: (NotificationCompat.Builder) -> Unit = {}): Notification =
        NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.foreground_notification_title))
            .setContentText(getString(R.string.foreground_notification_content))
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
        
        serviceStateLock.withLock {
            serviceState = serviceState.copy(running = false)
            notifyServiceListeners()
            
            connectivityManager.unregisterNetworkCallback(networkCallback)
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            notification?.actions?.forEach { it.actionIntent?.cancel() }
            notification = null
            log("stopped")
            
            if (serviceState.restart) {
                mainHandler.post {
                    ContextCompat.startForegroundService(applicationContext, Intent(applicationContext, ConnectivityChangeListenerService::class.java))
                }
                log("scheduled restart")
            }
        }
    }
    
    private val connectivityManager by lazy { ContextCompat.getSystemService(this, ConnectivityManager::class.java)!! }
    private val networkCallback = NetworkCallback()
    private val networkRequest = NetworkRequest.Builder().apply {
        if (Build.VERSION.SDK_INT >= 31) {
            setIncludeOtherUidNetworks(true)
        }
        addCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
    }.build()
    
    private inner class NetworkCallback : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
        fun decodeSsid(ssid: String): String {
            if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
                return ssid.substring(1, ssid.length - 1)
            }
            return "0x" + ssid
        }
        
        override fun onAvailable(network: Network) {
            log("onAvailable: $network")
            extracted(network)
        }
        
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            log("onCapabilitiesChanged: $network")
            extracted(network, networkCapabilities)
        }
        
        // TODO: proper name
        private fun extracted(network: Network, networkCapabilities: NetworkCapabilities? = null) {
            val ssid: String? = tryOrNull {
                val capabilities = networkCapabilities ?: connectivityManager.getNetworkCapabilities(network)
                capabilities?.transportInfo?.cast<WifiInfo?>()?.ssid?.takeIf { it != WifiManager.UNKNOWN_SSID }
            }?.let(::decodeSsid)
            
            if (ssid == null) return
            
            networkStateLock.withLock {
                val newState = NetworkState(network, ssid, false, false)
                networkState = newState
                log("SSID: $ssid")
                notifyNetworkListeners(newState, true)
                backgroundHandler.post(::tryLiberate)
            }
        }
        
        override fun onUnavailable() = networkStateLock.withLock {
            val oldState = networkState ?: return
            log("onUnavailable: ${oldState.network}")
            notifyNetworkListeners(oldState, false)
            networkState = null
        }
    }
    
    @WorkerThread
    fun tryLiberate() {
        val network = networkStateLock.withLock {
            val state = networkState ?: return
            if (state.liberated) return
            if (state.liberating) return
            networkState = state.copy(liberating = true)
            state.network
        }
        
        val t = Toast.makeText(applicationContext, "Trying to liberate", Toast.LENGTH_LONG)
        t.show()
        
        try {
            val newLocation = Liberator { this.socketFactory(network.socketFactory) }.liberate()
            
            if (newLocation == null) {
                Toast.makeText(applicationContext, R.string.quote_short, Toast.LENGTH_SHORT).show()
                t.cancel()
                log("broke out of the portal")
                networkStateLock.withLock {
                    networkState = networkState?.copy(liberated = true)
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
            networkStateLock.withLock {
                networkState = networkState?.copy(liberating = false)
            }
        }
    }
    
    companion object {
        
        val serviceListeners: MutableSet<(state: ServiceState) -> Unit> = WeakSynchronizedSet()
        @GuardedBy("serviceStateLock")
        var serviceState: ServiceState = ServiceState(false, false)
            private set
        val serviceStateLock: Lock = ReentrantLock()
        
        @GuardedBy("networkStateLock")
        val networkListeners: MutableSet<(state: NetworkState, available: Boolean) -> Unit> = WeakSynchronizedSet()
        var networkState: NetworkState? = null
        val networkStateLock: Lock = ReentrantLock()
        
        private fun notifyServiceListeners() = serviceStateLock.withLock {
            serviceListeners.forEach { it(serviceState) }
        }
        
        private fun notifyNetworkListeners(state: NetworkState, available: Boolean) = networkStateLock.withLock {
            networkListeners.forEach { it(state, available) }
        }
        
        fun start() {
            serviceStateLock.withLock {
                if (serviceState.running || serviceState.restart) return
                
                if (ContextCompat.checkSelfPermission(applicationContext, permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    log("Permission not granted: ${permission.ACCESS_FINE_LOCATION}")
                    Toast.makeText(
                        applicationContext, "Tried to start service without permission ${permission.ACCESS_FINE_LOCATION}", Toast.LENGTH_LONG
                    ).show()
                    return
                }
                ContextCompat.startForegroundService(applicationContext, Intent(applicationContext, ConnectivityChangeListenerService::class.java))
            }
        }
        
        fun stop() {
            applicationContext.stopService(Intent(applicationContext, ConnectivityChangeListenerService::class.java))
        }
        
        fun restart() {
            val intent = Intent(applicationContext, ConnectivityChangeListenerService::class.java)
            intent.putExtra("restart", true)
            applicationContext.startService(intent)
        }
        
        init {
            networkListeners.add(::logNetwork)
            serviceListeners.add(::logService)
        }
        
        private fun logNetwork(state: NetworkState, available: Boolean) = with(state) {
            log("network: $network, available: $available")
        }
        
        private fun logService(state: ServiceState) = with(state) {
            log("running: $running, restarting: $restart")
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
    
    }
}

fun <T> WeakSynchronizedSet(): MutableSet<T> = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))
