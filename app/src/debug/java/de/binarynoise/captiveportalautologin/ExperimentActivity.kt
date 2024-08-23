@file:Suppress("MemberVisibilityCanBePrivate", "unused", "RedundantSuppression")

package de.binarynoise.captiveportalautologin

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import by.kirich1409.viewbindingdelegate.viewBinding
import de.binarynoise.captiveportalautologin.databinding.ActivityExperimentBinding
import de.binarynoise.captiveportalautologin.preferences.NewMainActivity

class ExperimentActivity : ComponentActivity() {
    val binding by viewBinding { ActivityExperimentBinding.inflate(layoutInflater) }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        
        with(binding) {
        
        }
        
        startActivity(Intent(this, NewMainActivity::class.java))
        finish()
    }
}
