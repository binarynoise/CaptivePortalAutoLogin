package de.binarynoise.captiveportalautologin.preferences.onboarding

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import by.kirich1409.viewbindingdelegate.viewBinding
import de.binarynoise.captiveportalautologin.R
import de.binarynoise.captiveportalautologin.databinding.FragmentOnboardingWelcomeBinding
import de.binarynoise.captiveportalautologin.preferences.fillInAnimation

class WelcomeFragment : Fragment(R.layout.fragment_onboarding_welcome) {
    
    val binding by viewBinding(FragmentOnboardingWelcomeBinding::bind)
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.fab.buttonNext.setOnClickListener {
            requireActivity().supportFragmentManager.commit {
                replace(R.id.fragmentContainerView, NotificationFragment())
                fillInAnimation()
            }
        }
    }
}
