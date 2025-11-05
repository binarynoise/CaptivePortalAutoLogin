package de.binarynoise.filedb

import java.io.FileNotFoundException
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.json.Json
import de.binarynoise.logger.Logger.log

/**
 * A simple JSON database
 *
 * @property root Path to the root directory of the database.
 * @property serializer JSON serializer.
 */
class JsonDB(
    val root: Path,
    val serializer: Json = Json {
        encodeDefaults = false
        explicitNulls = false
    },
) {
    
    // TODO: prevent path traversal
    inline fun <reified T : Any> file(key: String, extension: String): Path = base<T>().resolve("$key.$extension")
    
    inline fun <reified T : Any> base(): Path = root.resolve(T::class.simpleName ?: T::class.java.simpleName)
    
    inline fun <reified T : Any> store(key: String, value: T, extension: String = DEFAULT_EXTENSION) {
        val json = serializer.encodeToString<T>(value)
        val file = file<T>(key, extension)
        file.createParentDirectories()
        file.writeText(json)
        log("wrote ${T::class.simpleName} with key $key to ${file.absolutePathString()}")
    }
    
    inline fun <reified T : Any> load(key: String, extension: String = DEFAULT_EXTENSION): T {
        val file = file<T>(key, extension)
        if (!file.exists()) {
            throw FileNotFoundException("file ${file.absolutePathString()} for ${T::class.simpleName} with key $key does not exist")
        }
        val json = file.readText()
        val decoded = serializer.decodeFromString<T>(json)
        log("loaded ${T::class.simpleName} with key $key from ${file.absolutePathString()}")
        return decoded
    }
    
    inline fun <reified T : Any> delete(key: String, extension: String = DEFAULT_EXTENSION) {
        val file = file<T>(key, extension)
        file.deleteIfExists()
    }
    
    inline fun <reified T : Any> storeAll(map: Map<String, T>, extension: String = DEFAULT_EXTENSION) {
        map.forEach { store(it.key, it.value, extension) }
    }
    
    inline fun <reified T : Any> loadAll(extension: String = DEFAULT_EXTENSION): Map<String, T> {
        val base = base<T>()
        if (!base.exists()) return emptyMap()
        
        val files = base.listDirectoryEntries("*.$extension")
        return files.asSequence().map { it.nameWithoutExtension }.associateWith { load<T>(it, extension) }
    }
    
    inline fun <reified T : Any> listAll(extension: String = DEFAULT_EXTENSION): List<String> {
        val base = base<T>()
        if (!base.exists()) return emptyList()
        return base.listDirectoryEntries("*.$extension").map { it.nameWithoutExtension }
    }
    
    companion object {
        const val DEFAULT_EXTENSION = "json"
    }
}
