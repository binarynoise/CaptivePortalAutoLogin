package de.binarynoise.captiveportalautologin.preferences

import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import de.binarynoise.captiveportalautologin.Permission
import de.binarynoise.captiveportalautologin.Permissions
import de.binarynoise.captiveportalautologin.R
import de.binarynoise.captiveportalautologin.util.startActivity

class PermissionsFragment(
    val includeOpenSettingsLink: Boolean = true,
    val permissions: Set<Permission> = Permissions,
    val onStateChangeCallback: () -> Unit = {},
) : AutoCleanupPreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val ctx = preferenceManager.context
        preferenceScreen = preferenceManager.createPreferenceScreen(ctx).apply {
            permissions.forEach { permission ->
                addPreference(CheckBoxPreference(ctx), lifecycle) {
                    titleRes = permission.nameRes
                    summaryRes = permission.descriptionRes
                    
                    setOnPreferenceChangeListener { _, _ ->
                        val activity = getActivity()
                        if (activity != null) {
                            permission.request(activity)
                        }
                        false
                    }
                    
                    fun update() {
                        isChecked = permission.granted(context)
                        isEnabled = permission.enabled(context)
                        onStateChangeCallback()
                    }
                    
                    update()
                    lifecycle.addObserver(object : LifecycleEventObserver {
                        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                            when (event) {
                                Lifecycle.Event.ON_RESUME, Lifecycle.Event.ON_START -> {
                                    update()
                                }
                                else -> {}
                            }
                        }
                    })
                }
            }
            
            if (includeOpenSettingsLink) addPreference(Preference(ctx), lifecycle) {
                titleRes = R.string.preference_open_app_info
                summaryRes = R.string.preference_open_app_info_description
                setOnPreferenceClickListener { _ ->
                    ctx.startActivity {
                        action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        data = Uri.fromParts("package", ctx.packageName, null)
                    }
                    true
                }
            }
            
            setIconSpaceReservedRecursively(false)
        }
    }
}
