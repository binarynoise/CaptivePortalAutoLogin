package de.binarynoise.captiveportalautologin

import java.io.File
import android.app.Activity
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.ComponentActivity

import by.kirich1409.viewbindingdelegate.viewBinding
import de.binarynoise.captiveportalautologin.databinding.ActivityLogExportBinding
import de.binarynoise.captiveportalautologin.databinding.ItemLogExportBinding
import de.binarynoise.captiveportalautologin.util.FileUtils.copyToSd
import de.binarynoise.captiveportalautologin.util.FileUtils.share
import de.binarynoise.logger.Logger
import de.binarynoise.logger.Logger.log

class LogExportActivity : ComponentActivity() {
    
    private val binding by viewBinding { ActivityLogExportBinding.inflate(layoutInflater) }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        
        val logFiles = Logger.Config.folder!!.listFiles()?.sortedByDescending { it.name } ?: emptyList()
        
        log("Found ${logFiles.size} log files")
        
        binding.logList.adapter = LogExportAdapter(this, logFiles)
    }
    
    override fun onMenuItemSelected(featureId: Int, item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            finish()
            true
        }
        else -> {
            super.onMenuItemSelected(featureId, item)
        }
    }
    
    class LogExportAdapter(val activity: Activity, val logFiles: List<File>) : ArrayAdapter<File>(activity, R.layout.item_log_export, logFiles) {
        
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view: View
            val binding: ItemLogExportBinding
            
            if (convertView == null) {
                binding = ItemLogExportBinding.inflate(activity.layoutInflater, parent, false)
                view = binding.root
            } else {
                view = convertView
                binding = ItemLogExportBinding.bind(view)
            }
            
            with(binding) {
                name.text = logFiles[position].name
                
                shareButton.setOnClickListener {
                    share(logFiles[position])
                }
                copyToSdButton.setOnClickListener {
                    copyToSd(context, logFiles[position], "text/plain")
                }
            }
            
            return view
        }
    }
}
