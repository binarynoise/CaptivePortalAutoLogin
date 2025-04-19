package de.binarynoise.captiveportalautologin.util

import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.atomic.*
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider.getUriForFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import de.binarynoise.liberator.tryOrIgnore
import de.binarynoise.logger.Logger.log

object FileUtils {
    
    fun saveFileToSd(file: File, mimeType: String, context: Context = applicationContext) {
        val (values, fileUri: Uri, outputStream) = prepareFile(file.name, mimeType, context = context)
        file.inputStream().use { input -> outputStream.use { input.copyTo(outputStream) } }
        finishFile(values, fileUri, context)
    }
    
    fun saveTextToSd(text: String, fileName: String, mimeType: String, context: Context = applicationContext) {
        val (values, fileUri: Uri, outputStream) = prepareFile(fileName, mimeType, context = context)
        outputStream.use { it.write(text.toByteArray()) }
        finishFile(values, fileUri, context)
    }
    
    fun shareText(text: String, title: String, context: Context = applicationContext) {
        val intent = Intent()
        intent.action = Intent.ACTION_SEND
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, text)
        
        val chooserIntent = Intent.createChooser(intent, title)
        chooserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(chooserIntent)
    }
    
    fun shareFile(file: File, title: String, context: Context = applicationContext) {
        val authority = "${context.packageName}.fileprovider"
        val fileUri = getUriForFile(context, authority, file)
        
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = getMimeType(file.name)
        intent.putExtra(Intent.EXTRA_STREAM, fileUri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        
        val chooserIntent = Intent.createChooser(intent, title)
        chooserIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(chooserIntent)
    }
    
    fun shareTextAsFile(text: String, fileName: String, title: String, lifecycleOwner: LifecycleOwner, context: Context = applicationContext) {
        val tmpFolder = context.cacheDir.resolve("shareTextAsFile")
        tmpFolder.mkdirs()
        val tmpFile = tmpFolder.resolve(fileName)
        tmpFile.writeText(text)
        
        val added = AtomicBoolean(false)
        fun cleanup() {
            tryOrIgnore {
                tmpFolder.deleteRecursively()
                log("Folder deleted: ${tmpFolder.absolutePath}")
            }
        }
        
        val observer = object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                when (event) {
                    Lifecycle.Event.ON_RESUME, Lifecycle.Event.ON_DESTROY -> {
                        if (added.get()) {
                            cleanup()
                            source.lifecycle.removeObserver(this)
                        }
                    }
                    else -> {}
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        // observer gets all the past events...
        added.set(true)
        
        try {
            shareFile(tmpFile, title)
        } catch (e: Exception) {
            cleanup()
            lifecycleOwner.lifecycle.removeObserver(observer)
            throw e
        }
    }
    
    
    private tailrec fun prepareFile(
        fileName: String,
        mimeType: String,
        counter: Int = 0,
        context: Context,
    ): Triple<ContentValues, Uri, OutputStream> {
        val extVolumeUri: Uri = MediaStore.Files.getContentUri("external")
        
        val fileNameWithIndex =
            if (counter != 0) fileName.substringBeforeLast(".") + "_$counter" + "." + fileName.substringAfterLast(".") else fileName
        val relativePath = Environment.DIRECTORY_DOWNLOADS + "/CaptivePortalAutoLogin/"
        
        log("File: $relativePath$fileNameWithIndex")
        
        val projection =
            arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.RELATIVE_PATH, MediaStore.Files.FileColumns.DISPLAY_NAME)
        val selection = "${MediaColumns.RELATIVE_PATH} = ? AND ${MediaColumns.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf(
            relativePath,
            "$fileNameWithIndex%", // % is a wildcard, so android can do its renaming without annoying us more than needed
        )
        val cursor = context.applicationContext.contentResolver.query(extVolumeUri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (cursor.count > 0) {
                log("File with name $fileName already exists, trying again with counter ${counter + 1}")
                return prepareFile(fileName, mimeType, counter + 1, context)
            }
        } ?: context.run { log("cursor is null, skipping check for existing file") }
        log("File with name $fileName does not exist. Creating new file.")
        
        val values = ContentValues().apply {
            put(MediaColumns.DISPLAY_NAME, fileNameWithIndex)
            put(MediaColumns.MIME_TYPE, mimeType)
            put(MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaColumns.OWNER_PACKAGE_NAME, context.packageName)
            put(MediaColumns.IS_PENDING, 1)
        }
        
        val fileUri: Uri = context.contentResolver.insert(extVolumeUri, values) ?: throw IOException("Failed to insert file $fileNameWithIndex")
        val outputStream = context.contentResolver.openOutputStream(fileUri) ?: throw IOException("Failed to open output stream for file $fileUri")
        return Triple(values, fileUri, outputStream)
    }
    
    private fun finishFile(values: ContentValues, fileUri: Uri, context: Context) {
        values.clear()
        values.put(MediaColumns.IS_PENDING, 0)
        context.contentResolver.update(fileUri, values, null, null)
    }
    
    private fun getMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast(".")
        return when (extension) {
            "txt" -> "text/plain"
            "log" -> "text/plain"
            "json" -> "application/json"
            "har" -> "application/har+json"
            
            else -> {
                log("Unknown file extension: $extension")
                // fallback using MimeTypeMap
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "text/plain"
            }
        }
    }
}
