package de.binarynoise.captiveportalautologin

import kotlinx.coroutines.launch
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.work.WorkInfo
import by.kirich1409.viewbindingdelegate.viewBinding
import de.binarynoise.captiveportalautologin.databinding.ActivityWorkBinding

class QueuedWorkActivity : ComponentActivity() {
    val binding by viewBinding { ActivityWorkBinding.inflate(layoutInflater) }
    
    fun WorkInfo.toFriendlyString(): String {
        return this.toString()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        
        binding.scheduleButton.setOnClickListener {
            Stats.scheduleEverythingForUpload()
        }
        
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Stats.getScheduledWork().collect { workInfos ->
                    binding.counters.text = buildString {
                        append("Total: ")
                        append(workInfos.size)
                        
                        WorkInfo.State.entries.forEach { state ->
                            val filtered = workInfos.filter { it.state == state }
                            if (filtered.isNotEmpty()) {
                                appendLine()
                                append("${state.name}: ")
                                append(filtered.size)
                            }
                        }
                    }
                    binding.text.text = workInfos.filter { it.state != WorkInfo.State.SUCCEEDED }
                        .joinToString("\n\n\n") { it.toFriendlyString() }
                    
                }
            }
        }
    }
}
