package de.binarynoise.captiveportalautologin

import kotlin.time.Instant
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.work.WorkManager
import by.kirich1409.viewbindingdelegate.viewBinding
import de.binarynoise.captiveportalautologin.databinding.ActivitySsidsBinding

@RequiresApi(Build.VERSION_CODES.Q)
class NetworkSuggestionSSIDActivity : ComponentActivity() {
    val binding by viewBinding { ActivitySsidsBinding.inflate(layoutInflater) }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                binding.counters.text = buildString { 
                    append("Total: ")
                    append(supportedSSIDs.size)
                    appendLine()
                    append("From Database / API: ")
                    append(ssidDb?.size ?: 0)
                    appendLine()
                    append("From Annotations: ")
                    append(supportedSSIDsByAnnotation.size)
                    appendLine()
//                    val workManager = WorkManager.getInstance(this@NetworkSuggestionSSIDActivity)
//                    val workInfo = workManager.getWorkInfosForUniqueWorkFlow(UpdateNetworkSuggestionSSIDsWorkerUniqueWorkName)
//                        .toList().flatMap { it }.singleOrNull()
//                    if (workInfo != null) {
//                        append("Next Scheduled Update: ")
//                        append(Instant.fromEpochMilliseconds(workInfo.nextScheduleTimeMillis))
//                        appendLine()
//                    }
                }
                binding.text.text = buildString {
                    appendLine("From Annotations:")
                    append(supportedSSIDsByAnnotation.sorted().joinToString("\n"))
                    appendLine()
                    appendLine()
                    appendLine()
                    appendLine()
                    appendLine("From Database / API:")
                    append(ssidDb?.sorted()?.joinToString("\n"))
                } 
            }
        }
    }
}
