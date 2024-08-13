package de.binarynoise.filedb

import java.nio.file.Path
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.minutes


/**
 * A wrapper around [CsvDB] that keeps a cache of the database contents in memory.
 *
 * It needs explicit calls to [load] and [store] to update the cache.
 *
 * Except for load and store, the user doesn't see the underlying database and doesn't need to know about it.
 *
 * @property root Path to the root directory of the database.
 * @property dbName Name of the database file (without extension).
 * @property delimiter Delimiter used in the CSV file.
 */
class CachedCsvDB(
    val root: Path,
    val dbName: String,
    val delimiter: String = ",",
) {
    private val wrapped = CsvDB(root, dbName, delimiter)
    private var cache: MutableList<List<String>> = mutableListOf()
    
    /**
     * Loads all records from the wrapped CSV database into the cache.
     *
     * This function updates the in-memory database with the contents of the CSV file.
     */
    fun load() {
        cache = wrapped.loadAll().toMutableList()
    }
    
    /**
     * Retrieves a record from the DB.
     *
     * @param key The first columns of the record.
     * @return The full record or null if not found.
     */
    operator fun get(key: List<String>): List<String>? {
        val found = cache.find { it.subList(0, key.size) == key }
        
        if (found == null) {
            println("key $key not found in $dbName")
        }
        
        return found
    }
    
    /**
     * Retrieves a record from the DB.
     *
     * @param key The first column of the record.
     * @return The full record or null if not found.
     */
    operator fun get(key: String): List<String>? = get(listOf(key))
    
    /**
     * Retrieves a record from the DB.
     *
     * @param key The first and second columns of the record.
     * @return The full record or null if not found.
     */
    operator fun get(key: Pair<String, String>): List<String>? = get(listOf(key.first, key.second))
    
    /**
     * Stores all records in the cache to the wrapped CSV database.
     *
     * This function updates the CSV file with the contents of the in-memory cache.
     */
    fun store() {
        wrapped.storeAll(cache)
    }
    
    /**
     * Stores a record in the cache.
     *
     * @param key The first columns of the record.
     * @param value The columns of the record to store.
     */
    operator fun set(key: List<String>, value: List<String>) {
        val index = cache.indexOfFirst { it.subList(0, key.size) == key }
        if (index == -1) {
            cache.add(key + value)
        } else {
            cache[index] = key + value
        }
    }
    
    /**
     * Stores a record in the cache.
     *
     * @param key The first column of the record.
     * @param value The value of the record to store.
     */
    operator fun set(key: String, value: String) = set(listOf(key), listOf(value))
    
    /**
     * Stores a record in the cache.
     *
     * @param key The first and second columns of the record.
     * @param value The value of the record to store.
     */
    operator fun set(key: Pair<String, String>, value: String) = set(listOf(key.first, key.second), listOf(value))
    
    private var backgroundThread: Thread? = null
    
    @Volatile
    private var terminate: Boolean = false
    
    /**
     * The delay between cache updates in milliseconds.
     *
     * The default is 10 minutes.
     */
    var delay = 10.minutes.inWholeMilliseconds
    
    /**
     * Starts background saving of the cache to the wrapped CSV database.
     *
     * This function updates the CSV file with the contents of the in-memory cache in regular intervals.
     * The delay can be adjusted with [delay] and defaults to 10 minutes.
     */
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
    
    /**
     * Stops background saving of the cache to the wrapped CSV database.
     */
    fun stopBackgroundSave() {
        terminate = true
        backgroundThread?.interrupt()
        backgroundThread?.join()
        backgroundThread = null
    }
}
