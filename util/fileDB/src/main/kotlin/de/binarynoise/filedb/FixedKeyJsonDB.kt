package de.binarynoise.filedb

import java.nio.file.Path
import kotlinx.serialization.json.Json
import de.binarynoise.filedb.JsonDB.Companion.DEFAULT_EXTENSION

class FixedKeyJsonDB(
    val root: Path,
    val key: String,
    val extension: String = DEFAULT_EXTENSION,
    val serializer: Json = Json {
        encodeDefaults = false
        explicitNulls = false
    },
) {
    val jsonDB = JsonDB(root, serializer)
    
    inline fun <reified T : Any> store(value: T) {
        return jsonDB.store(key, value, extension)
    }
    
    inline fun <reified T : Any> storeOrDelete(value: T?) {
        return jsonDB.storeOrDelete(key, value, extension)
    }
    
    inline fun <reified T : Any> load(): T {
        return jsonDB.load(key, extension)
    }
    
    inline fun <reified T : Any> loadOrNull(): T? {
        return jsonDB.loadOrNull(key, extension)
    }
    
    inline fun <reified T : Any> delete() {
        return jsonDB.delete<T>(key, extension)
    }
}
