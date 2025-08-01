package de.binarynoise.captiveportalautologin.preferences

import android.os.Bundle
import android.widget.Toast
import androidx.preference.PreferenceCategory
import de.binarynoise.captiveportalautologin.R
import de.binarynoise.captiveportalautologin.databinding.ItemLogExportBinding
import de.binarynoise.captiveportalautologin.util.FileUtils
import de.binarynoise.captiveportalautologin.util.FileUtils.shareFile
import de.binarynoise.logger.Logger
import de.binarynoise.logger.Logger.log

class LogsFragment : AutoCleanupPreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val ctx = preferenceManager.context
        preferenceScreen = preferenceManager.createPreferenceScreen(ctx).apply {
            addPreference(PreferenceCategory(ctx)) {
                title = "Export Logs"
                
                val logFiles = Logger.Config.folder?.listFiles()?.sortedByDescending { it.name }.orEmpty()
                logFiles.forEach { file ->
                    addPreference(WidgetPreference(ctx, R.layout.item_log_export) { view ->
                        val binding = ItemLogExportBinding.bind(view)
                        with(binding) {
                            shareButton.setOnClickListener {
                                try {
                                    shareFile(file, "Share log")
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        view.context, "Failed to share file: ${e.message}", Toast.LENGTH_LONG
                                    ).show()
                                    log("Error sharing file (${file.name})", e)
                                }
                            }
                            copyToSdButton.setOnClickListener {
                                try {
                                    val toast = Toast.makeText(view.context, "Saving...", Toast.LENGTH_SHORT)
                                    toast.show()
                                    
                                    FileUtils.saveFileToSd(file, "text/plain", view.context)
                                    
                                    toast.cancel()
                                    Toast.makeText(view.context, "Saved", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(
                                        view.context,
                                        e::class.java.simpleName + ": " + e.message + "\n" + "Please try again.",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    log("Error saving file", e)
                                }
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
