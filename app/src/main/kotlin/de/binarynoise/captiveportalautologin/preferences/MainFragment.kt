package de.binarynoise.captiveportalautologin.preferences

import kotlin.concurrent.read
import android.content.Intent
import android.database.ContentObserver
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.preference.Preference
import androidx.preference.SwitchPreference
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService.Companion.networkListeners
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService.Companion.networkState
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService.Companion.networkStateLock
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService.Companion.serviceListeners
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService.Companion.serviceState
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService.Companion.serviceStateLock
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService.NetworkState
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService.ServiceState
import de.binarynoise.captiveportalautologin.SETTINGS_NON_PERSISTENT_MAC_RANDOMIZATION_FORCE_ENABLED_KEY
import de.binarynoise.captiveportalautologin.gecko.RecordCaptivePortalActivity
import de.binarynoise.captiveportalautologin.isMacRandomizationForceEnabled
import de.binarynoise.captiveportalautologin.isMacRandomizationSupported
import de.binarynoise.captiveportalautologin.isNetworkSuggestion
import de.binarynoise.captiveportalautologin.removeNetworkSuggestions
import de.binarynoise.captiveportalautologin.resetNetworkSuggestionMacAddress
import de.binarynoise.captiveportalautologin.sendNetworkSuggestions
import de.binarynoise.captiveportalautologin.updateNetworkSuggestions
import de.binarynoise.captiveportalautologin.util.mainHandler
import de.binarynoise.captiveportalautologin.wifiManager
import org.mozilla.gecko.util.ThreadUtils.runOnUiThread

class MainFragment : AutoCleanupPreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val ctx = preferenceManager.context
        
        val serviceStateListeners: MutableList<(newState: ServiceState) -> Unit> = mutableListOf()
        val networkStateListeners: MutableList<(newState: NetworkState?) -> Unit> = mutableListOf()
        
        @Suppress("UNUSED_PARAMETER")
        fun updateServiceStatus(oldState: ServiceState?, newState: ServiceState) = runOnUiThread {
            serviceStateListeners.forEach { it(newState) }
        }
        
        @Suppress("UNUSED_PARAMETER")
        fun updateNetworkStatus(oldState: NetworkState?, newState: NetworkState?) = runOnUiThread {
            networkStateListeners.forEach { it(newState) }
        }
        
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                serviceListeners.add(::updateServiceStatus)
                updateServiceStatus(null, serviceStateLock.read { serviceState })
                networkListeners.add(::updateNetworkStatus)
                updateNetworkStatus(null, networkStateLock.read { networkState })
            }
            
            override fun onStop(owner: LifecycleOwner) {
                serviceListeners.remove(::updateServiceStatus)
                networkListeners.remove(::updateNetworkStatus)
            }
        })
        
        preferenceScreen = preferenceManager.createPreferenceScreen(ctx)
        preferenceScreen.apply {
            addPreference(Preference(ctx)) {
                title = "Status"
                
                setOnPreferenceClickListener {
                    ConnectivityChangeListenerService.start(silent = false)
                    true
                }
                
                serviceStateListeners.add {
                    summary = it.toString()
                }
            }
            
            
            addPreference(Preference(ctx)) {
                title = "Capture Captive Portal Login"
                summary = "Log in to a Captive Portal manually and share the capture to improve the Liberator"
                setOnPreferenceClickListener {
                    val networkState = networkStateLock.read { networkState }
                    if (networkState == null) return@setOnPreferenceClickListener false
                    val intent = Intent(ctx, RecordCaptivePortalActivity::class.java)
                    intent.putExtra(ConnectivityManager.EXTRA_NETWORK, networkState.network)
                    startActivity(intent)
                    true
                }
                networkStateListeners.add {
                    isVisible = it != null && it.hasPortal
                }
            }
            
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                addPreference(SwitchPreference(ctx)) {
                    title = "Network Suggestions"
                    summary = "Suggest automatic connection for supported networks to the OS."
                    setOnPreferenceChangeListener { _, _ ->
                        if (isChecked) removeNetworkSuggestions()
                        else sendNetworkSuggestions()
                    }
                    key = SharedPreferences.network_suggestions.sharedPreferencesKey
                    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                        summaryOn =
                            "$summary\nNote: You may experience short disconnections while the suggestions are updated in the background."
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val listener = WifiManager.SuggestionUserApprovalStatusListener { status ->
                            isEnabled = status != WifiManager.STATUS_SUGGESTION_APPROVAL_REJECTED_BY_USER
                            if (!isEnabled) isChecked = false
                        }
                        lifecycle.addObserver(object : DefaultLifecycleObserver {
                            override fun onStart(owner: LifecycleOwner) {
                                wifiManager.addSuggestionUserApprovalStatusListener(ctx.mainExecutor, listener)
                            }
                            
                            override fun onStop(owner: LifecycleOwner) {
                                wifiManager.removeSuggestionUserApprovalStatusListener(listener)
                            }
                        })
                    }
                }
                
                if (isMacRandomizationSupported) {
                    addPreference(SwitchPreference(ctx)) {
                        key = SharedPreferences.network_suggestions_mac_randomization.sharedPreferencesKey
                        title = "Non-persistent MAC randomization"
                        summary = "For suggested networks, the MAC address will be randomized periodically. " + //
                            "This will lead to more anonymity, but also requires liberation for most connection attempts."
                        setOnPreferenceChangeListener { _, _ ->
                            updateNetworkSuggestions()
                        }
                        
                        val observer = object : ContentObserver(mainHandler) {
                            override fun onChange(selfChange: Boolean) {
                                if (isMacRandomizationForceEnabled) {
                                    isEnabled = false
                                    isPersistent = false
                                    isChecked = true
                                } else {
                                    isChecked = SharedPreferences.network_suggestions_mac_randomization.get()
                                    isPersistent = true
                                    isEnabled = true
                                }
                            }
                        }
                        val uri = Settings.Global.getUriFor(SETTINGS_NON_PERSISTENT_MAC_RANDOMIZATION_FORCE_ENABLED_KEY)
                        lifecycle.addObserver(object : DefaultLifecycleObserver {
                            override fun onStart(owner: LifecycleOwner) {
                                ctx.contentResolver.registerContentObserver(uri, false, observer)
                                observer.onChange(true)
                            }
                            
                            override fun onStop(owner: LifecycleOwner) {
                                ctx.contentResolver.unregisterContentObserver(observer)
                            }
                        })
                        observer.onChange(true)
                    }.apply {
                        dependency = SharedPreferences.network_suggestions.sharedPreferencesKey
                    }
                    
                    addPreference(Preference(ctx)) {
                        title = "Change MAC-Address now"
                        summary =
                            "For suggested networks, immediately disconnect and resuggest with a different MAC-Address"
                        setOnPreferenceClickListener {
                            val networkState = networkStateLock.read { networkState }
                            if (networkState == null) return@setOnPreferenceClickListener false
                            resetNetworkSuggestionMacAddress(networkState.ssid)
                            true
                        }
                        networkStateListeners.add {
                            isEnabled = it != null && isNetworkSuggestion(it.ssid)
                        }
                    }
                }
            }
            
            
            addPreference(Preference(ctx)) {
                title = "Advanced Settings"
                fragment = AdvancedFragment::class.qualifiedName
            }
            
            setIconSpaceReservedRecursively(false)
        }
        
        updateServiceStatus(null, serviceStateLock.read { serviceState })
        updateNetworkStatus(null, networkStateLock.read { networkState })
    }
}
