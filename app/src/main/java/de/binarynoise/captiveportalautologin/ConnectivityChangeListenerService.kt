package de.binarynoise.captiveportalautologin

import java.util.*
import java.util.concurrent.atomic.*
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
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import de.binarynoise.liberator.Liberator
import de.binarynoise.logger.Logger.log

class ConnectivityChangeListenerService : Service() {
    
    val backgroundHandler = Handler(HandlerThread("Liberator").apply { start() }.looper)
    
    private var notification: Notification? = null
    private val channelId = "ConnectivityChangeListenerService"
    
    init {
        networkListeners.add { network, available ->
            connectivityManager.bindProcessToNetwork(
                if (available) network else null
            )
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    @MainThread
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log("onStartCommand")
        
        if (intent != null && intent.extras != null && intent.hasExtra("retry") && intent.extras!!.getBoolean("retry")) {
            val network = currentNetwork
            if (network != null) {
                backgroundHandler.post {
                    tryLiberate(network)
                }
            } else {
                Toast.makeText(this, "Not caught in a portal", Toast.LENGTH_SHORT).show()
            }
            return START_STICKY
        }
        
        if (running.getAndSet(true)) return START_STICKY
        
        createNotificationChannel(channelId)
        notification = createNotification(channelId) {
            // start MainActivity on click
            it.setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java).apply { addFlags(FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK) },
                    FLAG_CANCEL_CURRENT or FLAG_IMMUTABLE
                )
            )
            
            // add button to try liberating again
            it.addAction(
                NotificationCompat.Action.Builder(
                    null,
                    "Try again", // TODO
                    PendingIntent.getService(
                        this,
                        0,
                        Intent(this, ConnectivityChangeListenerService::class.java).apply { putExtra("retry", true) },
                        FLAG_CANCEL_CURRENT or FLAG_IMMUTABLE,
                    ),
                ).build()
            )
        }
        
        ServiceCompat.startForeground(
            this, 1, notification!!,
            if (Build.VERSION.SDK_INT >= 29) FOREGROUND_SERVICE_TYPE_MANIFEST else 0,
        )
        
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        
        serviceListeners.forEach { it(true) }
        
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
    
    private fun createNotificationChannel(channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                channelId,
                getString(R.string.foreground_notification_channel_name),
                NotificationManager.IMPORTANCE_MIN,
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        log("onDestroy")
        
        if (!running.getAndSet(false)) return
        
        serviceListeners.forEach { it(false) }
        
        connectivityManager.unregisterNetworkCallback(networkCallback)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        notification?.actions?.forEach { it.actionIntent?.cancel() }
        notification = null
        log("stopped")
    }
    
    private val connectivityManager by lazy { ContextCompat.getSystemService(this, ConnectivityManager::class.java)!! }
    private val networkCallback = NetworkCallback()
    private val networkRequest = NetworkRequest.Builder().apply {
        if (Build.VERSION.SDK_INT >= 31) {
            setIncludeOtherUidNetworks(true)
        }
        addCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
    }.build()
    
    private inner class NetworkCallback : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            log("onAvailable: $network")
            currentNetwork = network
            
            networkListeners.forEach { it(network, true) }
            backgroundHandler.post {
                tryLiberate(network)
            }
        }
        
        override fun onUnavailable() {
            log("onUnavailable: $currentNetwork")
            networkListeners.forEach { it(currentNetwork, false) }
            currentNetwork = null
        }
    }
    
    @WorkerThread
    fun tryLiberate(network: Network) {
        try {
            val newLocation = Liberator { this.socketFactory(network.socketFactory) }.liberate()
            
            if (newLocation != null) {
                log("Failed to liberate: still in portal: $newLocation")
                
                Toast.makeText(applicationContext, "Failed to liberate: still in portal: $newLocation", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(applicationContext, R.string.quote_short, Toast.LENGTH_SHORT).show()
                log("broke out of the portal")
            }
        } catch (e: Exception) {
            log("failed to liberate", e)
            val message = e.message ?: e.localizedMessage ?: "no error message"
            Toast.makeText(applicationContext, "Failed to liberate: ${e::class.simpleName} - $message", Toast.LENGTH_LONG).show()
        }
    }
    
    companion object {
        var currentNetwork: Network? = null
        
        val networkListeners: MutableSet<(network: Network?, available: Boolean) -> Unit> = WeakSynchronizedSet()
        
        var running: AtomicBoolean = AtomicBoolean(false)
        val serviceListeners: MutableSet<(running: Boolean) -> Unit> = WeakSynchronizedSet()
        
        private fun <T> WeakSynchronizedSet(): MutableSet<T> = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))
        
        init {
            networkListeners.add { network, available ->
                log("network: $network, available: $available")
            }
            serviceListeners.add { running ->
                log("running: $running")
            }
        }
    }
}
