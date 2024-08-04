package de.binarynoise.filedb

import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.writeText

class CsvDB(
    val root: Path,
    val dbName: String,
    val delimiter: String = ",",
) {
    fun load(key: List<String>): List<String>? {
        val file = root.resolve(dbName)
        if (!file.exists()) {
            println("file ${file.absolutePathString()} for ${dbName::class.simpleName} with key $key does not exist")
            return null
        }
        
        val array2d = file.readLines().filter { it.isNotEmpty() }.map { it.split(delimiter) }
        val line = array2d.find { it.subList(0, key.size) == key }
        if (line == null) {
            println("key $key not found in ${file.absolutePathString()}")
            return null
        }
        return line.drop(key.size)
    }
    
    fun loadAll(): List<List<String>> {
        val file = root.resolve(dbName)
        if (!file.exists()) {
            println("file ${file.absolutePathString()} for ${dbName::class.simpleName} does not exist")
            return emptyList()
        }
        return file.readLines().filter { it.isNotEmpty() }.map { it.split(delimiter) }
    }
    
    fun store(key: List<String>, value: List<String>) {
        val file = root.resolve(dbName)
        file.writeText(key.joinToString(delimiter) + "\n" + value.joinToString(delimiter))
        println("wrote ${dbName::class.simpleName} with key $key to ${file.absolutePathString()}")
    }
    
    fun storeAll(values: List<List<String>>) {
        val csv = values.joinToString("\n") { it.joinToString(delimiter) }
        val file = root.resolve(dbName)
        file.writeText(csv)
        println("wrote DB $dbName to ${file.absolutePathString()}")
    }
    
    fun storeAll(keys: List<List<String>>, values: List<List<String>>) {
        val joined = keys.zip(values).map { (k, v) -> k + v }
        storeAll(joined)
    }
}
