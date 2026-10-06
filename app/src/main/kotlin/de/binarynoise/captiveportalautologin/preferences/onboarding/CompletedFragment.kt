package de.binarynoise.captiveportalautologin.preferences.onboarding

import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService
import de.binarynoise.captiveportalautologin.R
import de.binarynoise.captiveportalautologin.preferences.MainFragment
import de.binarynoise.captiveportalautologin.preferences.applyCommonConfig

/**
 * stub fragment after onboarding to start [ConnectivityChangeListenerService] before going to [MainFragment]
 */
class CompletedFragment : Fragment(R.layout.fragment_onboarding_welcome) {
    override fun onStart() {
        super.onStart()
        ConnectivityChangeListenerService.start()
        parentFragmentManager.commit {
            replace(R.id.fragmentContainerView, MainFragment())
            applyCommonConfig()
        }
    }
}
