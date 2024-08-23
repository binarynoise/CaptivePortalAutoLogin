package de.binarynoise.captiveportalautologin.preferences

import kotlin.concurrent.read
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreference
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService.NetworkState
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService.ServiceState
import de.binarynoise.captiveportalautologin.GeckoViewActivity
import de.binarynoise.captiveportalautologin.Permissions
import de.binarynoise.captiveportalautologin.R
import org.mozilla.gecko.util.ThreadUtils.runOnUiThread

class NewMainActivityFragment : AutoCleanupPreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val ctx = preferenceManager.context
        preferenceScreen = preferenceManager.createPreferenceScreen(ctx).apply {
            addPreference(Preference(ctx)) {
                title = "ABI mismatch"
                summary = "The application's ABI does not match the device's ABI. Install the correct apk!"
                
                val deviceIs64Bit = Build.SUPPORTED_ABIS.any { abi -> abi.contains("64") }
                val librariesAre64Bit = context.applicationInfo.nativeLibraryDir.contains("64")
                isVisible = deviceIs64Bit != librariesAre64Bit
            }
            
            addPreference(SwitchPreference(ctx)) {
                title = "Service Status"
                
                setOnPreferenceChangeListener { _, _ ->
                    if (ConnectivityChangeListenerService.serviceState.running) {
                        ConnectivityChangeListenerService.stop()
                    } else {
                        ConnectivityChangeListenerService.start()
                    }
                    false
                }
                
                @Suppress("UNUSED_PARAMETER")
                fun updateStatusText(oldState: ServiceState?, newState: ServiceState) {
                    runOnUiThread {
                        summary = newState.toString()
                        isChecked = newState.running
                    }
                }
                
                lifecycle.addObserver(object : DefaultLifecycleObserver {
                    override fun onResume(owner: LifecycleOwner) {
                        ConnectivityChangeListenerService.serviceListeners.add(::updateStatusText)
                        updateStatusText(null, ConnectivityChangeListenerService.serviceState)
                    }
                    
                    override fun onDestroy(owner: LifecycleOwner) {
                        ConnectivityChangeListenerService.serviceListeners.remove(::updateStatusText)
                    }
                })
            }
            addPreference(Preference(ctx)) {
                title = "Network Status"
                
                @Suppress("UNUSED_PARAMETER")
                fun updateStatusText(oldState: NetworkState?, newState: NetworkState?) {
                    runOnUiThread {
                        summary = newState.toString()
                    }
                }
                lifecycle.addObserver(object : DefaultLifecycleObserver {
                    override fun onResume(owner: LifecycleOwner) {
                        ConnectivityChangeListenerService.networkListeners.add(::updateStatusText)
                        updateStatusText(null, ConnectivityChangeListenerService.networkStateLock.read {
                            ConnectivityChangeListenerService.networkState
                        })
                    }
                    
                    override fun onDestroy(owner: LifecycleOwner) {
                        ConnectivityChangeListenerService.networkListeners.remove(::updateStatusText)
                    }
                })
            }
            
            addPreference(CheckBoxPreference(ctx)) {
                title = "Permissions"
                fragment = PermissionsFragment::class.qualifiedName
                setOnPreferenceChangeListener { _, _ -> false }
                lifecycle.addObserver(object : DefaultLifecycleObserver {
                    override fun onResume(owner: LifecycleOwner) {
                        isChecked = Permissions.all.all { it.granted(context) }
                    }
                })
                summaryOn = ""
                summaryOff = "Please grant all permissions to use the app."
            }
            
            addPreference(ViewHolderPreference(ctx, R.layout.preference_accent)) {
                title = "Capture Captive Portal Login"
                intent = Intent(ctx, GeckoViewActivity::class.java)
            }
            
            addPreference(Preference(ctx)) {
                title = "Export Logs"
                fragment = LogsFragment::class.qualifiedName
            }
            
            setIconSpaceReservedRecursively(false)
        }
    }
}
