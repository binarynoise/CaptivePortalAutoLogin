package de.binarynoise.filedb

import java.io.FileNotFoundException
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.moveTo
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.writeText
import de.binarynoise.logger.Logger.log

class FileDB(
    root: Path,
    subPath: String? = null,
    val defaultExtension: String,
) {
    private val resolvedPath: Path = if (subPath.isNullOrEmpty()) root else root.resolve(subPath)
    
    fun file(key: String, extension: String = defaultExtension): Path = resolvedPath.resolve("$key.$extension")
    
    fun store(key: String, value: String, extension: String = defaultExtension) {
        val file = file(key, extension)
        file.createParentDirectories()
        file.writeText(value)
        log("wrote $key to ${file.absolutePathString()}")
    }
    
    fun load(key: String, extension: String = defaultExtension): String {
        val file = file(key, extension)
        if (!file.exists()) {
            throw FileNotFoundException("file ${file.absolutePathString()} with key $key does not exist")
        }
        val value = file.readText()
        log("loaded $key from ${file.absolutePathString()}")
        return value
    }
    
    fun loadOrNull(key: String, extension: String = defaultExtension): String? {
        val file = file(key, extension)
        if (!file.exists()) {
            return null
        }
        val value = file.readText()
        log("loaded $key from ${file.absolutePathString()}")
        return value
    }
    
    fun exists(key: String, extension: String = defaultExtension): Boolean {
        val file = file(key, extension)
        return file.exists()
    }
    
    fun delete(key: String, extension: String = defaultExtension) {
        val file = file(key, extension)
        file.deleteIfExists()
    }
    
    fun moveTo(other: FileDB, key: String, extension: String = defaultExtension) {
        val file = file(key, extension)
        val otherFile = other.file(key, extension)
        otherFile.createParentDirectories()
        file.moveTo(otherFile)
    }
    
    fun storeAll(map: Map<String, String>, extension: String = defaultExtension) {
        map.forEach { store(it.key, it.value, extension) }
    }
    
    fun loadAll(extension: String = defaultExtension): Map<String, String> {
        val base = resolvedPath
        if (!base.exists()) return emptyMap()
        
        val files = base.listDirectoryEntries("*.$extension")
        return files.asSequence().map { it.nameWithoutExtension }.associateWith { load(it, extension) }
    }
    
    fun listAll(extension: String = defaultExtension): List<String> {
        val base = resolvedPath
        if (!base.exists()) return emptyList()
        return base.listDirectoryEntries("*.$extension").map { it.nameWithoutExtension }
    }
    
    fun listAllFiles(extension: String = defaultExtension): List<Path> {
        val base = resolvedPath
        if (!base.exists()) return emptyList()
        return base.listDirectoryEntries("*.$extension")
    }
}
