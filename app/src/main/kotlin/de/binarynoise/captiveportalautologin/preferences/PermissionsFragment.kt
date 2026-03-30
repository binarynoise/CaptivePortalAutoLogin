package de.binarynoise.captiveportalautologin.preferences

import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import de.binarynoise.captiveportalautologin.Permissions
import de.binarynoise.captiveportalautologin.util.startActivity

class PermissionsFragment : AutoCleanupPreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val ctx = preferenceManager.context
        preferenceScreen = preferenceManager.createPreferenceScreen(ctx).apply {
            title = "Permissions"
            
            addPreference(PreferenceCategory(ctx)) {
                Permissions.forEach { permission ->
                    addPreference(CheckBoxPreference(ctx)) {
                        title = permission.name
                        summary = permission.description
                        
                        setOnPreferenceChangeListener { _, _ ->
                            permission.request(requireActivity())
                            false
                        }
                        
                        fun update() {
                            isChecked = permission.granted(context)
                            isEnabled = permission.enabled(context)
                        }
                        
                        update()
                        lifecycle.addObserver(object : DefaultLifecycleObserver {
                            override fun onResume(owner: LifecycleOwner) {
                                update()
                            }
                        })
                    }
                }
                
            }
            
            addPreference(Preference(ctx)) {
                title = "Open Settings"
                summary = "Click to open the app settings"
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
