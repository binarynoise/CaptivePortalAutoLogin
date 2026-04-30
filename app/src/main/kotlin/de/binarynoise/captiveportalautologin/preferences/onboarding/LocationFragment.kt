package de.binarynoise.captiveportalautologin.preferences.onboarding

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import by.kirich1409.viewbindingdelegate.viewBinding
import de.binarynoise.captiveportalautologin.Permissions
import de.binarynoise.captiveportalautologin.R
import de.binarynoise.captiveportalautologin.databinding.FragmentOnboardingLocationBinding
import de.binarynoise.captiveportalautologin.preferences.PermissionsFragment
import de.binarynoise.captiveportalautologin.preferences.fillInAnimation

class LocationFragment : Fragment(R.layout.fragment_onboarding_location) {
    
    val binding by viewBinding(FragmentOnboardingLocationBinding::bind)
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fun onStateChangeListener() {
            if (Permissions.locationPermissions.all { it.granted(requireContext()) }) {
                requireActivity().supportFragmentManager.commit {
                    replace(R.id.fragmentContainerView, DataCollectionFragment())
                    fillInAnimation()
                }
            }
        }
        onStateChangeListener()
        requireActivity().supportFragmentManager.commit {
            replace(
                R.id.permissionsFragmentContainerView, PermissionsFragment(
                    false,
                    Permissions.locationPermissions,
                    ::onStateChangeListener,
                )
            )
            fillInAnimation()
        }
    }
}
