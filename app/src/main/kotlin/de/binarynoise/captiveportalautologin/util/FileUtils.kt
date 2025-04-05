package de.binarynoise.captiveportalautologin.util

import java.io.File
import java.io.IOException
import java.io.OutputStream
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider.getUriForFile
import de.binarynoise.logger.Logger.log

object FileUtils {
    
    fun copyToSd(context: Context, file: File, mimeType: String) = with(context) {
        val toast = Toast.makeText(this, "Saving...", Toast.LENGTH_SHORT).apply { show() }
        
        try {
            val (values, fileUri: Uri, outputStream) = prepareFile(file.name, mimeType)
            file.inputStream().use { input -> outputStream.use { input.copyTo(outputStream) } }
            finishFile(values, fileUri)
            
            toast.cancel()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, e::class.java.simpleName + ": " + e.message + "\n" + "Please try again.", Toast.LENGTH_SHORT).show()
            log("Error saving file", e)
        }
    }
    
    fun copyToSd(context: Context, text: String, fileName: String, mimeType: String) = with(context) {
        val (values, fileUri: Uri, outputStream) = prepareFile(fileName, mimeType)
        outputStream.use { it.write(text.toByteArray()) }
        finishFile(values, fileUri)
    }
    
    private fun Context.prepareFile(fileName: String, mimeType: String): Triple<ContentValues, Uri, OutputStream> {
        // TODO check for file already exists and increase file name
        
        val values = ContentValues()
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/CaptivePortalAutoLogin/")
        values.put(MediaStore.MediaColumns.IS_PENDING, 1)
        val extVolumeUri: Uri = MediaStore.Files.getContentUri("external")
        val fileUri: Uri = contentResolver.insert(extVolumeUri, values) ?: throw IOException("Failed to insert file.")
        val outputStream = contentResolver.openOutputStream(fileUri) ?: throw IOException("Failed to open output stream.")
        return Triple(values, fileUri, outputStream)
    }
    
    private fun Context.finishFile(values: ContentValues, fileUri: Uri) {
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        contentResolver.update(fileUri, values, null, null)
    }
    
    fun shareText(text: String, context: Context = applicationContext, title: String) {
        try {
            val intent = Intent()
            intent.action = Intent.ACTION_SEND
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_TEXT, text)
            
            val chooserIntent = Intent.createChooser(intent, title)
            chooserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(chooserIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to share text: ${e.message}", Toast.LENGTH_LONG).show()
            log("Error sharing text (${text.take(20)})", e)
        }
    }
    
    fun shareFile(file: File, context: Context = applicationContext, title: String) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val fileUri = getUriForFile(context, authority, file)
            
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = getMimeType(file.name)
            intent.putExtra(Intent.EXTRA_STREAM, fileUri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            
            val chooserIntent = Intent.createChooser(intent, title)
            chooserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(chooserIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to share file: ${e.message}", Toast.LENGTH_LONG).show()
            log("Error sharing file (${file.name}$)", e)
        }
    }
    
    private fun getMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast(".")
        return when (extension) {
            ".txt" -> "text/plain"
            ".log" -> "text/plain"
            ".json" -> "application/json"
            ".html" -> "text/html"
            ".pdf" -> "application/pdf"
            
            else -> {
                log("Unknown file extension: $extension")
                "text/plain"
            }
        }
    }
}
