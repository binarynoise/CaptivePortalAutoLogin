import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.copyTo
import kotlin.io.path.walk
import kotlin.reflect.full.memberProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import de.binarynoise.captiveportalautologin.server.ApiServer
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class DBMigrationTest {
    
    @ParameterizedTest
    @MethodSource("oldDBs")
    suspend fun openDatabases(oldDB: Path) = withContext(Dispatchers.IO) {
        val tempDir = Files.createTempDirectory(this::class.simpleName)
        tempDir.toFile().deleteOnExit()
        oldDB.copyTo(tempDir.resolve("db.sqlite"))
        val apiServer = ApiServer(tempDir)
        println("Successes:")
        apiServer.database.successDao().getAll().forEach {
            println(it.toStringByReflection())
        }
        println("Errors:")
        apiServer.database.errorDao().getAll().forEach {
            println(it.toStringByReflection())
        }
    }
    
    companion object {
        @JvmStatic
        fun oldDBs(): List<Arguments> {
            return this::class.java.getResource("/DBMigrationTest")
                .let { Paths.get(it.toURI()).walk().filter(Files::isRegularFile) }
                .map { Arguments.of(it) }
                .toList()
        }
    }
}


fun Any.toStringByReflection(exclude: List<String> = listOf(), mask: List<String> = listOf()): String {
    val propsString = this::class.memberProperties //
        .filter { !exclude.contains(it.name) } //
        .joinToString(", ") {
            val value = when {
                mask.contains(it.name) -> "****"
                else -> {
                    val value = it.getter.call(this)
                    when (value) {
                        is String -> """"$value""""
                        else -> value.toString()
                    }.take(100)
                }
            }
            "${it.name}=${value}"
        }
    
    return "${this::class.simpleName}(${propsString})"
}
