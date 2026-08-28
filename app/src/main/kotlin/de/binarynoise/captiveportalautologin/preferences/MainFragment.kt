package de.binarynoise.captiveportalautologin.preferences

import kotlin.concurrent.read
import android.content.Intent
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import androidx.core.net.toUri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
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
import de.binarynoise.captiveportalautologin.NetworkSuggestionOnPreferenceChangeListener
import de.binarynoise.captiveportalautologin.R
import de.binarynoise.captiveportalautologin.gecko.RecordCaptivePortalActivity
import de.binarynoise.captiveportalautologin.isMacRandomizationSupported
import de.binarynoise.captiveportalautologin.isNetworkSuggestion
import de.binarynoise.captiveportalautologin.resetNetworkSuggestionMacAddress
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
                titleRes = R.string.status
                
                setOnPreferenceClickListener {
                    ConnectivityChangeListenerService.start(silent = false)
                    true
                }
                
                serviceStateListeners.add {
                    summary = it.toString()
                }
            }
            
            
            addPreference(Preference(ctx)) {
                titleRes = R.string.capture_captive_portal
                summaryRes = R.string.capture_captive_portal_description
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
                    titleRes = R.string.preference_network_suggestions
                    summaryRes = R.string.preference_network_suggestions_description
                    onPreferenceChangeListener = NetworkSuggestionOnPreferenceChangeListener
                    key = SharedPreferences.network_suggestions.sharedPreferencesKey
                    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                        summaryOn =
                            summary.toString() + getString(R.string.preference_network_suggestions_disconnect_on_Q)
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
                    addPreference(Preference(ctx)) {
                        titleRes = R.string.preference_network_suggestions_change_mac_now
                        summaryRes = R.string.preference_network_suggestions_change_mac_now_description
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
                titleRes = R.string.preference_advanced_settings
                fragment = AdvancedFragment::class.qualifiedName
            }
            
            
            addPreference(PreferenceCategory(ctx)) {
                title = "Contact"
                addPreference(Preference(ctx)) {
                    title = "GitHub Repository"
                    intent = Intent(Intent.ACTION_VIEW, "https://github.com/binarynoise/CaptivePortalAutoLogin".toUri())
                }
                addPreference(Preference(ctx)) {
                    title = "Telegram Channel"
                    intent = Intent(Intent.ACTION_VIEW, "https://t.me/+__MmjOzaVOw3MDc6".toUri())
                }
                addPreference(Preference(ctx)) {
                    title = "Telegram Group"
                    intent = Intent(Intent.ACTION_VIEW, "https://t.me/+a5Kj_MA-OGoyN2My".toUri())
                }
            }
            
            setIconSpaceReservedRecursively(false)
        }
        
        updateServiceStatus(null, serviceStateLock.read { serviceState })
        updateNetworkStatus(null, networkStateLock.read { networkState })
    }
}
