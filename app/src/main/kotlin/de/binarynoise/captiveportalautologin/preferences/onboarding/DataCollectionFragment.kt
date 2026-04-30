package de.binarynoise.captiveportalautologin.preferences.onboarding

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import by.kirich1409.viewbindingdelegate.viewBinding
import de.binarynoise.captiveportalautologin.R
import de.binarynoise.captiveportalautologin.databinding.FragmentOnboardingDataCollectionBinding
import de.binarynoise.captiveportalautologin.preferences.fillInAnimation

class DataCollectionFragment : Fragment(R.layout.fragment_onboarding_data_collection) {
    
    val binding by viewBinding(FragmentOnboardingDataCollectionBinding::bind)
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.fab.buttonNext.setOnClickListener {
            requireActivity().supportFragmentManager.commit {
                replace(R.id.fragmentContainerView, CompletedFragment())
                fillInAnimation()
            }
        }
        
        //TODO: add "send statistics" preference here
    }
}
