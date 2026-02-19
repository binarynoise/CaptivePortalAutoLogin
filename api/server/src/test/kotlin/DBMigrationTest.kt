import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.copyTo
import kotlin.io.path.walk
import de.binarynoise.captiveportalautologin.server.ApiServer
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class DBMigrationTest {
    
    @ParameterizedTest
    @MethodSource("oldDBs")
    fun openDatabases(oldDB: Path) {
        val tempDir = Files.createTempDirectory(this::class.simpleName)
        tempDir.toFile().deleteOnExit()
        oldDB.copyTo(tempDir.resolve("db.sqlite"))
        ApiServer(tempDir)
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
