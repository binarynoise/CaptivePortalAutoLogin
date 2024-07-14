package de.binarynoise.jsondb

import java.nio.file.Path
import kotlin.io.path.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
val serializer = Json {
    this.encodeDefaults = false
    this.explicitNulls = false
}

@JvmInline
value class JsonDB(val root: Path) {
    
    inline fun <reified T : Any> file(key: String): Path = base<T>().resolve("$key.json")
    
    inline fun <reified T : Any> base(): Path = root.resolve(T::class.simpleName ?: T::class.java.simpleName)
    
    inline fun <reified T : Any> store(key: String, value: T) {
        val json = serializer.encodeToString<T>(value)
        val file = file<T>(key)
        file.createParentDirectories()
        file.writeText(json)
    }
    
    inline fun <reified T : Any> load(key: String): T? {
        val file = file<T>(key)
        if (!file.exists()) {
            return null
        }
        val json = file.readText()
        return serializer.decodeFromString<T>(json)
    }
    
    inline fun <reified T : Any> delete(key: String) {
        val file = file<T>(key)
        file.deleteIfExists()
    }
    
    inline fun <reified T : Any> storeAll(map: Map<String, T>) {
        map.forEach { store(it.key, it.value) }
    }
    
    inline fun <reified T : Any> loadAll(): Map<String, T> {
        val files = base<T>().listDirectoryEntries("*.json")
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
