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
import de.binarynoise.logger.Logger

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
            Logger.log("Error saving file", e)
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
    
    fun share(file: File, context: Context = applicationContext) {
        val intent = Intent()
        intent.action = Intent.ACTION_SEND
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, file.readText())
        
        val chooserIntent = Intent.createChooser(intent, "Share log")
        chooserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(chooserIntent)
    }
}