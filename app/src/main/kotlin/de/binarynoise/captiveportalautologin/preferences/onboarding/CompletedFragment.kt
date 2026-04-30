package de.binarynoise.captiveportalautologin.preferences.onboarding

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import de.binarynoise.captiveportalautologin.ConnectivityChangeListenerService
import de.binarynoise.captiveportalautologin.R
import de.binarynoise.captiveportalautologin.preferences.MainFragment
import de.binarynoise.captiveportalautologin.preferences.fillInAnimation

/**
 * stub fragment after onboarding to start [ConnectivityChangeListenerService] before going to [MainFragment]
 */
class CompletedFragment : Fragment(R.layout.fragment_onboarding_welcome) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ConnectivityChangeListenerService.start()
        requireActivity().supportFragmentManager.commit {
            replace(R.id.fragmentContainerView, MainFragment())
            fillInAnimation()
        }
    }
}
