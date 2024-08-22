package de.binarynoise.captiveportalautologin.preferences

import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.preference.CheckBoxPreference
import androidx.preference.PreferenceCategory
import de.binarynoise.captiveportalautologin.Permissions

class PermissionsFragment : AutoCleanupPreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val ctx = preferenceManager.context
        preferenceScreen = preferenceManager.createPreferenceScreen(ctx).apply {
            title = "Permissions"
            
            addPreference(PreferenceCategory(ctx)) {
                Permissions.all.forEach { permission ->
                    addPreference(CheckBoxPreference(ctx)) {
                        title = permission.name
                        summary = permission.description
                        
                        setOnPreferenceChangeListener { _, _ ->
                            permission.request(requireActivity())
                            false
                        }
                        
                        lifecycle.addObserver(object : DefaultLifecycleObserver {
                            override fun onResume(owner: LifecycleOwner) {
                                isChecked = permission.granted(context)
                                isEnabled = permission.enabled(context)
                            }
                        })
                    }
                }
                
            }
            setIconSpaceReservedRecursively(false)
        }
    }
}
