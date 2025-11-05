package de.binarynoise.jsondb

import java.nio.file.Files
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDateTime
import de.binarynoise.filedb.JsonDB
import org.junit.jupiter.api.AfterAll

object JsonDBTest {
    
    private val jsonDB = JsonDB(Files.createTempDirectory("jsonDB")).also { println(it) }
    
    @Test
    fun storeString() {
        jsonDB.store("test", "test")
        assertEquals("test", jsonDB.load<String>("test"))
        assertEquals(mapOf("test" to "test"), jsonDB.loadAll<String>("json"))
        jsonDB.delete<String>("test")
        assertEquals(emptyMap(), jsonDB.loadAll<String>("json"))
    }
    
    @Test
    fun storeDate() {
        jsonDB.store("test", LocalDateTime(1970, 1, 1, 12, 0, 0))
    }
    
    @OptIn(ExperimentalPathApi::class)
    @JvmStatic
    @AfterAll
    fun cleanup() {
        jsonDB.root.deleteRecursively()
    }
}
