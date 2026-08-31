package de.binarynoise.captiveportalautologin.preferences

import kotlin.concurrent.read
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Intent
import android.database.ContentObserver
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.net.toUri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreference
import de.binarynoise.captiveportalautologin.BuildConfig
import de.binarynoise.captiveportalautologin.BuildConfig.API_BASE
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
import de.binarynoise.captiveportalautologin.Permissions
import de.binarynoise.captiveportalautologin.R
import de.binarynoise.captiveportalautologin.SETTINGS_NON_PERSISTENT_MAC_RANDOMIZATION_FORCE_ENABLED_KEY
import de.binarynoise.captiveportalautologin.client.ApiClient
import de.binarynoise.captiveportalautologin.gecko.GeckoViewActivity
import de.binarynoise.captiveportalautologin.gecko.RecordCaptivePortalActivity
import de.binarynoise.captiveportalautologin.isMacRandomizationForceEnabled
import de.binarynoise.captiveportalautologin.isMacRandomizationSupported
import de.binarynoise.captiveportalautologin.isNetworkSuggestion
import de.binarynoise.captiveportalautologin.resetNetworkSuggestionMacAddress
import de.binarynoise.captiveportalautologin.updateNetworkSuggestions
import de.binarynoise.captiveportalautologin.util.applicationContext
import de.binarynoise.captiveportalautologin.util.getSignaturePublicKey
import de.binarynoise.captiveportalautologin.util.mainHandler
import de.binarynoise.captiveportalautologin.wifiManager
import de.binarynoise.logger.Logger.log
import de.binarynoise.util.okhttp.get
import de.binarynoise.util.okhttp.readText
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.mozilla.gecko.util.ThreadUtils.runOnUiThread

class AdvancedFragment : AutoCleanupPreferenceFragment() {
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
            // TODO: add switch to disable service auto-start (and then disable manual start/stop)
            addPreference(SwitchPreference(ctx)) {
                titleRes = R.string.service_status
                
                setOnPreferenceChangeListener { _, _ ->
                    if (serviceStateLock.read { serviceState.running }) {
                        ConnectivityChangeListenerService.stop()
                    } else {
                        ConnectivityChangeListenerService.start()
                    }
                    false
                }
                
                serviceStateListeners.add {
                    summary = it.toString()
                    isChecked = it.running
                }
            }
            
            addPreference(Preference(ctx)) {
                titleRes = R.string.network_status
                isSelectable = false
                serviceStateListeners.add {
                    isEnabled = it.running
                }
                networkStateListeners.add {
                    summary = it?.toString() ?: getString(R.string.not_connected_to_network)
                }
            }
            
            addPreference(SwitchPreference(ctx)) {
                key = SharedPreferences.liberator_automatically_liberate.sharedPreferencesKey
                titleRes = R.string.liberator_status
                setSummaryOn(R.string.preferences_automatically_liberating_captive_portals_description_on)
                setSummaryOff(R.string.preferences_automatically_liberating_captive_portals_description_off)
                setDefaultValue(SharedPreferences.liberator_automatically_liberate.defaultValue)
            }
            
            addPreference(Preference(ctx)) {
                titleRes = R.string.liberate_now
                summaryRes = R.string.liberate_now_description
                
                setOnPreferenceClickListener {
                    ConnectivityChangeListenerService.retry()
                    true
                }
                
                serviceStateListeners.add {
                    isEnabled = it.running
                }
            }
            
            addPreference(Preference(ctx)) {
                titleRes = R.string.request_reevaluation
                summaryRes = R.string.request_reevaluation_description
                setOnPreferenceClickListener {
                    ConnectivityChangeListenerService.reportNetworkConnectivity()
                    true
                }
                serviceStateListeners.add {
                    isEnabled = it.running
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
                    isEnabled = it != null && it.hasPortal
                }
            }
            
            if (BuildConfig.DEBUG) {
                addPreference(Preference(ctx)) {
                    titleRes = R.string.capture_captive_portal_dev
                    summaryRes = R.string.capture_captive_portal_dev_description
                    intent = Intent(ctx, GeckoViewActivity::class.java)
                }
            }
            
            addPreference(CheckBoxPreference(ctx)) {
                titleRes = R.string.preference_permissions
                fragment = PermissionsFragment::class.qualifiedName
                setOnPreferenceChangeListener { _, _ -> false }
                lifecycle.addObserver(object : DefaultLifecycleObserver {
                    override fun onResume(owner: LifecycleOwner) {
                        isChecked = Permissions.all { it.granted(context) }
                    }
                })
                setSummaryOn(R.string.preference_permissions_description_granted)
                setSummaryOff(R.string.preference_permissions_description_not_granted)
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
                    addPreference(SwitchPreference(ctx)) {
                        key = SharedPreferences.network_suggestions_mac_randomization.sharedPreferencesKey
                        titleRes = R.string.preference_network_suggestions_mac_randomization
                        summaryRes = R.string.preference_network_suggestions_mac_randomization_description
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
            
            addPreference(SwitchPreference(ctx)) {
                if (!BuildConfig.DEBUG) key = SharedPreferences.liberator_experimental_enabled_sharedPreferencesKey
                titleRes = R.string.preference_enable_experimental_portalliberators
                if (BuildConfig.DEBUG) {
                    isEnabled = false
                    isChecked = true
                    summaryRes = R.string.preference_enable_experimental_portalliberators_description
                    setSummaryOn(R.string.preference_enable_experimental_portalliberators_description_always_enabled_on_debugging_builds)
                }
            }
            
            addPreference(DropDownPreference(ctx, SharedPreferences.liberator_captive_test_url)) {
                titleRes = R.string.preference_captive_test_url
            }
            
            addPreference(DropDownPreference(ctx, SharedPreferences.liberator_user_agent)) {
                titleRes = R.string.preference_user_agent
            }
            
            addPreference(SwitchPreference(ctx)) {
                key = SharedPreferences.liberator_send_stats.sharedPreferencesKey
                titleRes = R.string.preference_send_statistics
                summaryRes = R.string.preference_send_statistics_description
                summaryOff = getString(R.string.preference_send_statistics_description_off)
                isChecked = true
                isEnabled = false
            }
            
            if (BuildConfig.DEBUG) {
                addPreference(
                    EditTextPreference(
                        ctx,
                        defaultValue = "",
                        hint = API_BASE,
                    ) { editText, s ->
                        if (s.isBlank()) {
                            SharedPreferences.api_base_url.set(null)
                            editText.error = null
                        } else try {
                            val url = s.trim().toHttpUrl()
                            require(url.pathSegments.takeLast(2) == listOf("api", "")) {
                                getString(R.string.preference_api_base_url_url_must_end_with_api)
                            }
                            SharedPreferences.api_base_url.set(url)
                            editText.error = null
                        } catch (e: IllegalArgumentException) {
                            editText.error = e.localizedMessage ?: e.message ?: getString(R.string.invalid_url)
                        }
                    },
                ) {
                    key = SharedPreferences.api_base_url.sharedPreferencesKey
                    titleRes = R.string.preference_api_base
                }
                
                addPreference(Preference(ctx)) {
                    title = getString(R.string.preference_api_base_connection_test)
                    onPreferenceClickListener = {
                        lifecycleScope.launch {
                            summary = getString(R.string.preference_api_base_connection_test_testing)
                            summary = withContext(Dispatchers.IO) {
                                val client = OkHttpClient()
                                try {
                                    val apiBaseUrl = SharedPreferences.api_base_url.get()
                                    if (apiBaseUrl == null) getString(R.string.error_api_base_url_not_set)
                                    else client.get(apiBaseUrl, null).readText()
                                } catch (e: Exception) {
                                    e.message
                                }
                            }
                        }
                        true
                    }
                }
            }
            
            addPreference(Preference(ctx)) {
                title = "Check for Updates"
                onPreferenceClickListener = {
                    lifecycleScope.launch {
                        summary = getString(R.string.preference_update_check_checking)
                        isEnabled = false
                        summary = coroutineScope {
                            val deferred = async(Dispatchers.IO) {
                                val apiBaseUrl = SharedPreferences.api_base_url.get()
                                    ?: return@async getString(R.string.error_api_base_url_not_set)
                                
                                val update = try {
                                    val apiClient = ApiClient(apiBaseUrl, applicationContext.getSignaturePublicKey())
                                    apiClient.checkUpdate(BuildConfig.VERSION_NAME, true)
                                } catch (e: Exception) {
                                    log("update check failed", e)
                                    return@async getString(R.string.preference_update_check_update_check_failed)
                                }
                                if (update != null) {
                                    onPreferenceClickListener = {
                                        val intent = Intent(Intent.ACTION_VIEW, update.url.toUri())
                                        startActivity(intent)
                                        true
                                    }
                                    getString(R.string.preference_update_check_update_available, update.version)
                                } else {
                                    getString(R.string.preference_update_check_no_update_available)
                                }
                            }
                            delay(2.seconds)
                            deferred.await()
                        }
                        isEnabled = true
                    }
                    true
                }
            }
            
            addPreference(Preference(ctx)) {
                titleRes = R.string.preference_export_logs
                fragment = LogsFragment::class.qualifiedName
            }
            
            if (BuildConfig.DEBUG) {
                addPreference(Preference(ctx)) {
                    titleRes = R.string.preference_debug_activities
                    fragment = DebugShortcutsFragment::class.qualifiedName
                }
            }
            setIconSpaceReservedRecursively(false)
        }
        
        updateServiceStatus(null, serviceStateLock.read { serviceState })
        updateNetworkStatus(null, networkStateLock.read { networkState })
    }
}
