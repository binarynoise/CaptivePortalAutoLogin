package de.binarynoise.filedb

import java.nio.file.Path
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.minutes

class CachedCsvDB(
    val root: Path,
    val dbName: String,
    val delimiter: String = ",",
) {
    private val wrapped = CsvDB(root, dbName, delimiter)
    private var cache: MutableList<List<String>> = mutableListOf()
    
    fun load() {
        cache = wrapped.loadAll().toMutableList()
    }
    
    operator fun get(key: List<String>): List<String>? {
        val found = cache.find { it.subList(0, key.size) == key }
        
        if (found == null) {
            println("key $key not found in $dbName")
        }
        
        return found
    }
    
    operator fun get(key: String): String? = get(listOf(key))?.firstOrNull()
    operator fun get(key: Pair<String, String>): String? = get(listOf(key.first, key.second))?.firstOrNull()
    
    fun store() {
        wrapped.storeAll(cache)
    }
    
    operator fun set(key: List<String>, value: List<String>) {
        val index = cache.indexOfFirst { it.subList(0, key.size) == key }
        if (index == -1) {
            cache.add(key + value)
        } else {
            cache[index] = key + value
        }
    }
    
    operator fun set(key: String, value: String) = set(listOf(key), listOf(value))
    operator fun set(key: Pair<String, String>, value: String) = set(listOf(key.first, key.second), listOf(value))
    
    private var backgroundThread: Thread? = null
    
    @Volatile
    private var terminate: Boolean = false
    
    var delay = 10.minutes.inWholeMilliseconds
    
    fun startBackgroundSave() {
        if (backgroundThread == null) {
            backgroundThread = thread {
                while (!terminate) {
                    Thread.sleep(delay)
                    store()
                }
            }
        }
    }
    
    fun stopBackgroundSave() {
        terminate = true
        backgroundThread?.interrupt()
        backgroundThread?.join()
        backgroundThread = null
    }
}
