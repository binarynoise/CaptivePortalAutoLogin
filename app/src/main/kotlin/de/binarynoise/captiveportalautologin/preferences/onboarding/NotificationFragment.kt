package de.binarynoise.captiveportalautologin.preferences.onboarding

import android.Manifest.permission.POST_NOTIFICATIONS
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import by.kirich1409.viewbindingdelegate.viewBinding
import de.binarynoise.captiveportalautologin.Permissions
import de.binarynoise.captiveportalautologin.R
import de.binarynoise.captiveportalautologin.databinding.FragmentOnboardingNotificationBinding
import de.binarynoise.captiveportalautologin.preferences.applyCommonConfig

class NotificationFragment : Fragment(R.layout.fragment_onboarding_notification) {
    
    val binding by viewBinding(FragmentOnboardingNotificationBinding::bind)
    
    fun nextPage() {
        parentFragmentManager.commit {
            replace(R.id.fragmentContainerView, LocationFragment())
            applyCommonConfig()
        }
    }
    
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) nextPage()
            else binding.permissionDenied.isVisible = true
        }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.fab.buttonNext.setOnClickListener {
            if (Permissions.notifications.granted(requireContext())) nextPage()
            else requestNotificationPermission.launch(POST_NOTIFICATIONS)
        }
    }
    
    override fun onStart() {
        super.onStart()
        if (Permissions.notifications.granted(requireContext())) nextPage()
    }
}
