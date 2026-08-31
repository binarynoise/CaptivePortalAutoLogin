package de.binarynoise.captiveportalautologin.preferences

import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.app.AlertDialog
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceCategory
import de.binarynoise.captiveportalautologin.BuildConfig
import de.binarynoise.captiveportalautologin.R
import de.binarynoise.captiveportalautologin.ScheduledApiClient
import de.binarynoise.captiveportalautologin.api.generateLogFileName
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
                titleRes = R.string.preference_export_logs
                
                lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        val logFiles = withContext(Dispatchers.IO) {
                            Logger.Config.folder?.listFiles()?.sortedByDescending { it.name }.orEmpty()
                        }
                        logFiles.forEach { file ->
                            addPreference(WidgetPreference(ctx, R.layout.item_log_export) { view ->
                                val binding = ItemLogExportBinding.bind(view)
                                with(binding) {
                                    shareButton.setOnClickListener {
                                        lifecycleScope.launch {
                                            try {
                                                withContext(Dispatchers.IO) {
                                                    shareFile(file, getString(R.string.share_log))
                                                }
                                            } catch (e: Exception) {
                                                if (e is CancellationException) throw e
                                                Toast.makeText(
                                                    view.context,
                                                    getString(R.string.error_failed_to_share_file) + e.message,
                                                    Toast.LENGTH_LONG
                                                ).show()
                                                log("Error sharing file (${file.name})", e)
                                            }
                                        }
                                    }
                                    copyToSdButton.setOnClickListener {
                                        lifecycleScope.launch {
                                            try {
                                                val toast = Toast.makeText(
                                                    view.context, R.string.saving, Toast.LENGTH_SHORT
                                                )
                                                toast.show()
                                                
                                                withContext(Dispatchers.IO) {
                                                    FileUtils.saveFileToSd(file, "text/plain", view.context)
                                                }
                                                
                                                toast.cancel()
                                                Toast.makeText(
                                                    view.context, getString(R.string.saved), Toast.LENGTH_SHORT
                                                ).show()
                                            } catch (e: Exception) {
                                                if (e is CancellationException) throw e
                                                Toast.makeText(
                                                    view.context,
                                                    e::class.java.simpleName + ": " + e.message + "\n" + getString(R.string.please_try_again),
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                                log("Error saving file", e)
                                            }
                                        }
                                    }
                                    uploadButton.setOnClickListener {
                                        AlertDialog.Builder(ctx)
                                            .setTitle(R.string.submit_log)
                                            .setMessage(getString(R.string.submit_log_description))
                                            .setPositiveButton(android.R.string.yes) { _, _ ->
                                                lifecycleScope.launch {
                                                    try {
                                                        withContext(Dispatchers.IO) {
                                                            val timestamp =
                                                                Instant.fromEpochMilliseconds(file.lastModified())
                                                            val version = BuildConfig.VERSION_NAME
                                                            val content = file.readText()
                                                            val name = generateLogFileName(timestamp, version, content)
                                                            ScheduledApiClient.log.submitLog(name, content)
                                                        }
                                                        Toast.makeText(
                                                            view.context,
                                                            R.string.upload_scheduled,
                                                            Toast.LENGTH_SHORT,
                                                        ).show()
                                                    } catch (e: Exception) {
                                                        Toast.makeText(
                                                            view.context,
                                                            e::class.java.simpleName + ": " + e.message + "\n" + getString(
                                                                R.string.please_try_again
                                                            ),
                                                            Toast.LENGTH_SHORT,
                                                        ).show()
                                                        log("Error scheduling upload", e)
                                                    }
                                                }
                                            }
                                            .setNegativeButton(android.R.string.no) { _, _ -> }
                                            .show()
                                    }
                                }
                            }) {
                                title = file.name
                                isIconSpaceReserved = false
                            }
                        }
                    }
                }
            }
            
            setIconSpaceReservedRecursively(false)
        }
    }
}
