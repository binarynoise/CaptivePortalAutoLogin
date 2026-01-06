package de.binarynoise.captiveportalautologin

import java.util.*
import java.util.concurrent.locks.*
import java.util.function.*
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.properties.Delegates
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_MIN
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_CANCEL_CURRENT
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import androidx.annotation.GuardedBy
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService.SsidCompat.connectivityManager
import de.binarynoise.captiveportalautologin.api.Api.Liberator.Error
import de.binarynoise.captiveportalautologin.api.Api.Liberator.Success
import de.binarynoise.captiveportalautologin.preferences.SharedPreferences
import de.binarynoise.captiveportalautologin.util.BackgroundHandler
import de.binarynoise.captiveportalautologin.util.applicationContext
import de.binarynoise.captiveportalautologin.util.mainHandler
import de.binarynoise.captiveportalautologin.util.startService
import de.binarynoise.captiveportalautologin.xposed.ReevaluationHook
import de.binarynoise.liberator.Liberator
import de.binarynoise.liberator.cast
import de.binarynoise.liberator.tryOrNull
import de.binarynoise.logger.Logger.log

class ConnectivityChangeListenerService : Service() {
    
    val backgroundHandler = BackgroundHandler("ConnectivityChangeListenerService")
    
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }
    private var notification: Notification? = null
    private val notificationId = 1
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
    
    @Suppress("unused")
    @SuppressLint("MissingPermission")
    private fun updateNotification(oldState: NetworkState?, newState: NetworkState?) {
        val oldNotification = notification ?: return
        val text = newState?.toString().orEmpty()
        log("updateNotification: $text")
        val newNotification = NotificationCompat.Builder(this, oldNotification).setContentText(text).build()
        NotificationManagerCompat.from(this).notify(notificationId, newNotification)
        notification = newNotification
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    @MainThread
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log("onStartCommand")
        
        if (intent != null && intent.getBooleanExtra("stop", false)) {
            stopSelf()
            return START_NOT_STICKY
        }
        
        serviceStateLock.write {
            if (serviceState.running) {
                if (intent != null) when {
                    intent.getBooleanExtra("restart", false) -> {
                        serviceState = ServiceState(running = true, restart = true)
                        stopSelf()
                    }
                    intent.getBooleanExtra("retry", false) -> {
                        retryLiberate()
                    }
                }
                return START_STICKY
            }
            serviceState = ServiceState(running = true, restart = false)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(channelId, "Persistent Notification", IMPORTANCE_MIN)
            notificationManager.createNotificationChannel(serviceChannel)
        }
        notification = NotificationCompat.Builder(this, channelId).let { builder ->
            builder.setContentTitle("Captive Portal detection")
            builder.setContentText("Running in background")
            builder.setSmallIcon(R.drawable.wifi_lock_open)
            builder.setStyle(NotificationCompat.BigTextStyle())
            
            // start HomeActivity on click
            val launchHomeIntent =
                Intent().apply { component = ComponentName.createRelative(application.packageName, ".HomeActivity") }
            launchHomeIntent.addFlags(FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TASK)
            builder.setContentIntent(
                PendingIntent.getActivity(
                    this, 0, launchHomeIntent, FLAG_CANCEL_CURRENT or FLAG_IMMUTABLE
                )
            )
            
            // add button to try liberating again
            val retryIntent = Intent(this, this::class.java)
            retryIntent.putExtra("retry", true)
            val pendingRetryIntent =
                PendingIntent.getService(this, 0, retryIntent, FLAG_CANCEL_CURRENT or FLAG_IMMUTABLE)
            builder.addAction(NotificationCompat.Action.Builder(null, "Liberate now", pendingRetryIntent).build())
            
            val captureIntent = Intent(this, GeckoViewActivity::class.java)
            val pendingCaptureIntent =
                PendingIntent.getActivity(this, 0, captureIntent, FLAG_CANCEL_CURRENT or FLAG_IMMUTABLE)
            builder.addAction(NotificationCompat.Action.Builder(null, "Capture portal", pendingCaptureIntent).build())
            
            builder.setOnlyAlertOnce(true)
        }.build()
        
        try {
            ServiceCompat.startForeground(
                this, notificationId, notification!!,
                if (Build.VERSION.SDK_INT >= 29) FOREGROUND_SERVICE_TYPE_MANIFEST else 0,
            )
        } catch (e: IllegalStateException) {
            log("Failed to start $this as foreground service", e)
            stopSelf()
            return START_NOT_STICKY
        }
        
        networkListeners.add(::bindNetworkToProcess)
        networkListeners.add(::updateNotification)
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        
        log("started")
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        log("onDestroy")
        
        serviceStateLock.write {
            serviceState = serviceState.copy(running = false)
            
            connectivityManager.unregisterNetworkCallback(networkCallback)
            networkState = null
            networkListeners.remove(::bindNetworkToProcess)
            networkListeners.remove(::updateNotification)
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            
            val n = notification
            if (n != null) {
                n.actions?.forEach { it.actionIntent?.cancel() }
                NotificationManagerCompat.from(this).cancel(notificationId)
                notification = null
            }
            
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
        addTransportType(TRANSPORT_WIFI)
        if (Build.VERSION.SDK_INT >= 31) {
            setIncludeOtherUidNetworks(true)
        }
    }.build()
    private val networkCallback = createNetworkCallback()
    
    private fun createNetworkCallback(): ConnectivityManager.NetworkCallback {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                log("onAvailable: $network")
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    // Starting with Build.VERSION_CODES.O onCapabilitiesChanged is guaranteed to be called immediately after onAvailable.
                    
                    val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return
                    updateNetworkState(network, networkCapabilities)
                }
            }
            
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                updateNetworkState(network, networkCapabilities)
            }
            
            override fun onLost(network: Network) = networkStateLock.write {
                log("onUnavailable: $network")
                val oldState = networkState
                if (oldState?.network == network && !oldState.debug) networkState = null
            }
        }
        
        return when {
            Build.VERSION.SDK_INT < 31 -> callback
            else -> NetworkCallback31(callback)
        }
    }
    
    fun updateNetworkState(network: Network, networkCapabilities: NetworkCapabilities) {
        val hasPortal = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
        
        val oldState = networkStateLock.read { networkState }
        if (oldState == null) {
            val ssid = SsidCompat.getSsid(network, networkCapabilities)
            log("SSID: $ssid")
            if (ssid == null) {
                return
            }
            
            networkStateLock.write {
                networkState = NetworkState(network, ssid, hasPortal, liberating = false, liberated = false)
            }
        } else {
            networkStateLock.write {
                networkState = oldState.copy(hasPortal = hasPortal)
            }
        }
        if (!hasPortal) return
        
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
                Toast.makeText(applicationContext, "Not connected to network", Toast.LENGTH_SHORT).show()
                return
            }
            if (!state.hasPortal) {
                log("no portal")
                Toast.makeText(applicationContext, "Not in captive portal", Toast.LENGTH_SHORT).show()
                return
            }
            if (state.liberated) {
                log("already liberated")
                Toast.makeText(applicationContext, "Already liberated", Toast.LENGTH_SHORT).show()
                return
            }
            if (state.liberating) {
                log("already liberating")
                Toast.makeText(applicationContext, "Already liberating", Toast.LENGTH_SHORT).show()
                return
            }
            networkState = state.copy(liberating = true)
            state.network
        }
        
        val t = Toast.makeText(applicationContext, "Trying to liberate", Toast.LENGTH_SHORT)
        t.show()
        
        try {
            val userAgent: String by SharedPreferences.liberator_user_agent
            val portalTestUrl: String by SharedPreferences.liberator_captive_test_url
            
            val res = Liberator(
                { okhttpClient -> okhttpClient.socketFactory(network.socketFactory) },
                portalTestUrl,
                userAgent,
            ).liberate()
            
            t.cancel()
            
            when (res) {
                Liberator.LiberationResult.NotCaught -> {
                    log("not caught in portal")
                    Toast.makeText(applicationContext, "Failed to liberate: not caught in portal", Toast.LENGTH_SHORT)
                        .show()
                    // no report
                }
                is Liberator.LiberationResult.Success -> {
                    log("broke out of the portal")
                    Toast.makeText(applicationContext, "Free at last!", Toast.LENGTH_SHORT).show()
                    Stats.liberator.reportSuccess(
                        Success(
                            BuildConfig.VERSION_NAME,
                            System.currentTimeMillis(),
                            networkStateLock.read { networkState?.ssid.toString() },
                            res.url,
                        )
                    )
                }
                is Liberator.LiberationResult.Error -> {
                    log("failed to liberate: ${res.message}", res.exception)
                    Toast.makeText(
                        applicationContext,
                        "Failed to liberate: ${res.exception::class.simpleName} - ${res.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    Stats.liberator.reportError(
                        Error(
                            BuildConfig.VERSION_NAME,
                            System.currentTimeMillis(),
                            networkState?.ssid.toString(),
                            res.url,
                            res.message,
                        )
                    )
                }
                is Liberator.LiberationResult.Timeout -> {
                    log("failed to liberate: timeout")
                    Toast.makeText(applicationContext, "Failed to liberate: timeout", Toast.LENGTH_SHORT).show()
                    // no timeout report
                }
                is Liberator.LiberationResult.UnknownPortal -> {
                    log("failed to liberate: unknown portal")
                    Toast.makeText(
                        applicationContext, "Failed to liberate: unknown portal ${res.url}", Toast.LENGTH_SHORT
                    ).show()
                    Stats.liberator.reportError(
                        Error(
                            BuildConfig.VERSION_NAME,
                            System.currentTimeMillis(),
                            networkState?.ssid.toString(),
                            res.url,
                            "unknown portal",
                        )
                    )
                }
                is Liberator.LiberationResult.StillCaptured -> {
                    log("failed to liberate: still captured")
                    Toast.makeText(
                        applicationContext,
                        "Failed to liberate: still captured ${res.url}",
                        Toast.LENGTH_SHORT,
                    ).show()
                    Stats.liberator.reportError(
                        Error(
                            BuildConfig.VERSION_NAME,
                            System.currentTimeMillis(),
                            networkState?.ssid.toString(),
                            res.url,
                            "still captured",
                        )
                    )
                }
                is Liberator.LiberationResult.UnsupportedPortal -> {
                    log("Failed to liberate: Portal will not be supported")
                    Toast.makeText(
                        applicationContext,
                        "Failed to liberate: Portal will not be supported ${res.url}",
                        Toast.LENGTH_SHORT,
                    ).show()
                    // no report
                    /*
                    Stats.liberator.reportError(
                        Error(
                            BuildConfig.VERSION_NAME,
                            System.currentTimeMillis(),
                            networkState?.ssid.toString(),
                            res.url,
                            "Portal will not be supported",
                        )
                    )
                    */
                }
            }
        } catch (e: Exception) {
            t.cancel()
            log("failed to liberate", e)
            val message = e.localizedMessage ?: e.message ?: "no error message"
            Toast.makeText(
                applicationContext,
                "Failed to liberate: ${e::class.simpleName} - $message",
                Toast.LENGTH_LONG,
            ).show()
            Stats.liberator.reportError(
                Error(
                    BuildConfig.VERSION_NAME,
                    System.currentTimeMillis(),
                    networkState?.ssid.toString(),
                    "",
                    message,
                )
            )
        } finally {
            forceReevaluation()
            
            networkStateLock.write {
                networkState = networkState?.copy(liberating = false, liberated = true)
            }
        }
    }
    
    
    private fun retryLiberate() {
        val network = networkStateLock.read { networkState?.network }
        if (network != null) {
            networkStateLock.write {
                networkState = networkState?.copy(liberated = false)
            }
            backgroundHandler.post(::tryLiberate)
        } else {
            Toast.makeText(this, "Not connected to network", Toast.LENGTH_SHORT).show()
        }
    }
    
    // FLAG_INCLUDE_LOCATION_INFO not available pre API 31
    @RequiresApi(31)
    class NetworkCallback31(val wrapped: ConnectivityManager.NetworkCallback) :
        ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
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
        val connectivityManager by lazy {
            ContextCompat.getSystemService(applicationContext, ConnectivityManager::class.java)!!
        }
        
        const val UNKNOWN_SSID = "<unknown ssid>"
        
        fun getSsid(network: Network, networkCapabilities: NetworkCapabilities?): String? {
            var ssid: String?
            
            // pre API 29, but try anyway
            ssid = tryOrNull {
                @Suppress("DEPRECATION") //
                wifiManager.connectionInfo.ssid.takeIf { it != UNKNOWN_SSID }?.let(::decodeSsid)
            }
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
        var serviceState: ServiceState by Delegates.observable(
            ServiceState(running = false, restart = false)
        ) { _, oldState, newState ->
            if (oldState != newState) serviceStateLock.read {
                log("notifying ${serviceListeners.size} serviceListeners...")
                serviceListeners.javaForEach { it(oldState, newState) }
            }
        }
            private set
        
        val networkListeners: MutableSet<(oldState: NetworkState?, newState: NetworkState?) -> Unit> = SynchronizedSet()
        val networkStateLock = ReentrantReadWriteLock(true)
        
        @delegate:GuardedBy("networkStateLock")
        var networkState: NetworkState? by Delegates.observable(null) { _, oldState, newState ->
            if (oldState != newState) {
                log("notifying ${networkListeners.size} networkListeners...")
                networkListeners.javaForEach { it(oldState, newState) }
            }
        }
        
        fun start(silent: Boolean = false) = serviceStateLock.read {
            if (serviceState.running || serviceState.restart) return
            val missingPermissions = Permissions.filterNot { it.granted(applicationContext) }
                .map { permission -> permission.name ?: applicationContext.getString(permission.nameRes ?: 0) }
            if (missingPermissions.isNotEmpty()) {
                if (!silent) {
                    val text = "Tried to start service with missing permission: ${missingPermissions.joinToString()}"
                    Toast.makeText(applicationContext, text, Toast.LENGTH_LONG).show()
                }
                return
            }
            ContextCompat.startForegroundService(
                applicationContext,
                Intent(applicationContext, ConnectivityChangeListenerService::class.java),
            )
        }
        
        fun stop() {
            applicationContext.startService<ConnectivityChangeListenerService> {
                putExtra("stop", true)
            }
        }
        
        fun restart() = serviceStateLock.read {
            if (serviceState.running) {
                applicationContext.startService<ConnectivityChangeListenerService> {
                    putExtra("restart", true)
                }
            } else start()
        }
        
        fun retry() {
            applicationContext.startService<ConnectivityChangeListenerService> {
                putExtra("retry", true)
            }
        }
        
        context(context: Context)
        fun forceReevaluation() {
            context.sendBroadcast(Intent(ReevaluationHook.ACTION))
            log("sent broadcast ${ReevaluationHook.ACTION}")
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
        val running: Boolean, val restart: Boolean,
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
        val hasPortal: Boolean,
        val liberating: Boolean,
        val liberated: Boolean,
        val debug: Boolean = false,
    ) {
        override fun toString(): String = buildString {
            append("Network $network with SSID $ssid")
            
            append(" is ")
            if (!hasPortal) {
                append("not ")
            }
            append("caught in Portal")
            append(" and is currently ")
            if (liberating) {
                append("liberating")
            } else if (liberated) {
                append("liberated")
            } else {
                append("not liberating")
            }
            if (debug) {
                append(" and in debug mode")
            }
        }
    }
}

fun <T> SynchronizedSet(): MutableSet<T> = Collections.synchronizedSet<T>(mutableSetOf<T>())

fun <T> Set<T>.javaForEach(consumer: Consumer<T>) = forEach(consumer)
