package de.binarynoise.captiveportalautologin.preferences.onboarding

import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import by.kirich1409.viewbindingdelegate.viewBinding
import de.binarynoise.captiveportalautologin.Permissions
import de.binarynoise.captiveportalautologin.R
import de.binarynoise.captiveportalautologin.databinding.FragmentOnboardingNotificationBinding
import de.binarynoise.captiveportalautologin.preferences.fillInAnimation

class NotificationFragment : Fragment(R.layout.fragment_onboarding_notification) {
    
    val binding by viewBinding(FragmentOnboardingNotificationBinding::bind)
    
    fun nextPage() {
        requireActivity().supportFragmentManager.commit {
            replace(R.id.fragmentContainerView, LocationFragment())
            fillInAnimation()
        }
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (Permissions.notifications.granted(requireContext())) return nextPage()
        binding.fab.buttonNext.setOnClickListener {
            if (Permissions.notifications.granted(requireContext())) nextPage()
            else requestPermissions(arrayOf(POST_NOTIFICATIONS), 0)
        }
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String?>, grantResults: IntArray) {
        if (grantResults.single() == PERMISSION_GRANTED) return nextPage()
        if (grantResults.single() == PERMISSION_DENIED) {
            binding.permissionDenied.isVisible = true
            return
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
}
