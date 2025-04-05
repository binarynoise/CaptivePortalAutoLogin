package de.binarynoise.captiveportalautologin.preferences

import android.os.Bundle
import androidx.preference.PreferenceCategory
import de.binarynoise.captiveportalautologin.R
import de.binarynoise.captiveportalautologin.databinding.ItemLogExportBinding
import de.binarynoise.captiveportalautologin.util.FileUtils
import de.binarynoise.logger.Logger

class LogsFragment : AutoCleanupPreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val ctx = preferenceManager.context
        preferenceScreen = preferenceManager.createPreferenceScreen(ctx).apply {
            addPreference(PreferenceCategory(ctx)) {
                title = "Export Logs"
                
                val logFiles = Logger.Config.folder?.listFiles()?.sortedByDescending { it.name } ?: emptyList()
                logFiles.forEach { file ->
                    addPreference(WidgetPreference(ctx, R.layout.item_log_export) { view ->
                        val binding = ItemLogExportBinding.bind(view)
                        with(binding) {
                            shareButton.setOnClickListener {
                                FileUtils.shareFile(file, title = "Share log")
                            }
                            copyToSdButton.setOnClickListener {
                                FileUtils.copyToSd(view.context, file, "text/plain")
                            }
                        }
                    }) {
                        title = file.name
                    }
                }
            }
            
            setIconSpaceReservedRecursively(false)
        }
    }
}
