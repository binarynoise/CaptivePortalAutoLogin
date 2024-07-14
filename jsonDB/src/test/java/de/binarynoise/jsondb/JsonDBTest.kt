package de.binarynoise.jsondb

import java.nio.file.Files
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDateTime
import org.junit.jupiter.api.AfterAll

object JsonDBTest {
    
    private val jsonDB = JsonDB(Files.createTempDirectory("jsonDB")).also { println(it) }
    
    @Test
    fun storeString() {
        jsonDB.store("test", "test")
        assertEquals("test", jsonDB.load<String>("test"))
        assertEquals(mapOf("test" to "test"), jsonDB.loadAll<String>())
        jsonDB.delete<String>("test")
        assertEquals(emptyMap(), jsonDB.loadAll<String>())
    }
    
    @Test
    fun storeDate() {
        jsonDB.store("test", LocalDateTime(1970, 1, 1, 12, 0, 0))
    }
    
    @OptIn(ExperimentalPathApi::class)
    @JvmStatic
    @AfterAll
    fun cleanup(): Unit {
        jsonDB.root.deleteRecursively()
    }
}
