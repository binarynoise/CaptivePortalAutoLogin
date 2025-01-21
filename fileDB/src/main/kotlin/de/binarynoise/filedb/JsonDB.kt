@file:OptIn(ExperimentalSerializationApi::class)

package de.binarynoise.filedb

import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json


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
    
    inline fun <reified T : Any> file(key: String, extension: String): Path = base<T>().resolve("$key.$extension")
    
    inline fun <reified T : Any> base(): Path = root.resolve(T::class.simpleName ?: T::class.java.simpleName)
    
    inline fun <reified T : Any> store(key: String, value: T, extension: String = "json") {
        val json = serializer.encodeToString<T>(value)
        val file = file<T>(key, extension)
        file.createParentDirectories()
        file.writeText(json)
        println("wrote ${T::class.simpleName} with key $key to ${file.absolutePathString()}")
    }
    
    inline fun <reified T : Any> load(key: String, extension: String = "json"): T? {
        val file = file<T>(key, extension)
        if (!file.exists()) {
            println("file ${file.absolutePathString()} for ${T::class.simpleName} with key $key does not exist")
            return null
        }
        val json = file.readText()
        val decoded = serializer.decodeFromString<T>(json)
        println("loaded ${T::class.simpleName} with key $key from ${file.absolutePathString()}")
        return decoded
    }
    
    inline fun <reified T : Any> delete(key: String, extension: String = "json") {
        val file = file<T>(key, extension)
        file.deleteIfExists()
    }
    
    inline fun <reified T : Any> storeAll(map: Map<String, T>) {
        map.forEach { store(it.key, it.value) }
    }
    
    inline fun <reified T : Any> loadAll(extension: String): Map<String, T> {
        val files = base<T>().listDirectoryEntries("*.$extension")
        return files.asSequence().map { it.nameWithoutExtension }.associateWithNotNull { load<T>(it) }
    }
}

inline fun <K : Any, V : Any> Sequence<K>.associateWithNotNull(valueSelector: (K) -> V?): Map<K, V> {
    val result = LinkedHashMap<K, V>()
    for (element in this) {
        result[element] = valueSelector(element) ?: continue
    }
    return result
}
