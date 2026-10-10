package de.binarynoise.captiveportalautologin.preferences.onboarding

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import by.kirich1409.viewbindingdelegate.viewBinding
import de.binarynoise.captiveportalautologin.R
import de.binarynoise.captiveportalautologin.databinding.FragmentOnboardingDataCollectionBinding
import de.binarynoise.captiveportalautologin.preferences.applyCommonConfig

class DataCollectionFragment : Fragment(R.layout.fragment_onboarding_data_collection) {
    
    val binding by viewBinding(FragmentOnboardingDataCollectionBinding::bind)
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.fab.buttonNext.setOnClickListener {
            parentFragmentManager.commit {
                replace<CompletedFragment>(R.id.fragmentContainerView)
                applyCommonConfig()
            }
        }
        
        //TODO: add "send statistics" preference here
    }
}
