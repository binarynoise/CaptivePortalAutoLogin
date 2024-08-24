@file:Suppress("MemberVisibilityCanBePrivate", "unused", "RedundantSuppression")

package de.binarynoise.captiveportalautologin

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import by.kirich1409.viewbindingdelegate.viewBinding
import de.binarynoise.captiveportalautologin.databinding.ActivityExperimentBinding

class ExperimentActivity : ComponentActivity() {
    val binding by viewBinding { ActivityExperimentBinding.inflate(layoutInflater) }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        
        with(binding) {
        
        }
    }
}
