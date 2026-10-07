package de.binarynoise.captiveportalautologin.preferences.onboarding

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import by.kirich1409.viewbindingdelegate.viewBinding
import de.binarynoise.captiveportalautologin.Permissions
import de.binarynoise.captiveportalautologin.R
import de.binarynoise.captiveportalautologin.databinding.FragmentOnboardingLocationBinding
import de.binarynoise.captiveportalautologin.preferences.PermissionsFragment
import de.binarynoise.captiveportalautologin.preferences.applyCommonConfig

class LocationFragment : Fragment(R.layout.fragment_onboarding_location) {
    
    val binding by viewBinding(FragmentOnboardingLocationBinding::bind)
    
    private fun onStateChanged() {
        val ctx = context ?: return
        if (!Permissions.locationPermissions.all { it.granted(ctx) }) return
        parentFragmentManager.commit {
            replace<DataCollectionFragment>(R.id.fragmentContainerView)
            applyCommonConfig()
        }
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState == null) {
            childFragmentManager.commit {
                replace<PermissionsFragment>(
                    R.id.permissionsFragmentContainerView,
                    args = PermissionsFragment.args(false, Permissions.locationPermissions),
                )
                applyCommonConfig()
            }
        }
    }
    
    override fun onStart() {
        super.onStart()
        onStateChanged()
    }
    
    override fun onResume() {
        super.onResume()
        onStateChanged()
    }
}
