package de.binarynoise.captiveportalautologin

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.work.WorkInfo
import by.kirich1409.viewbindingdelegate.viewBinding
import de.binarynoise.captiveportalautologin.databinding.ActivityWorkBinding

class QueuedWorkActivity : AppCompatActivity() {
    val binding by viewBinding { ActivityWorkBinding.inflate(layoutInflater) }
    val textView: TextView = binding.text
    
    fun WorkInfo.toFriendlyString(): String {
        return this.toString()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        val work = Stats.getScheduledWork()
        textView.text = work.value?.joinToString("\n") { it.toFriendlyString() }
    }
}
