package de.binarynoise.captiveportalautologin.integration

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import de.binarynoise.captiveportalautologin.server.ApiServer
import de.binarynoise.captiveportalautologin.server.createServer
import de.binarynoise.captiveportalautologin.server.database.SuccessEntity

/**
 * Runs the api server of this repository inside the test process, with a database of its own.
 *
 * Listening to data is what tells us that the app talked to the api server the test pointed it at.
 */
class ApiServerFixture(private val environment: TestEnvironment) : AutoCloseable {
    
    private val rootDirectory: Path = Files.createTempDirectory("cpal-integration-api-server")
    private val apiServer = ApiServer(rootDirectory)
    private val httpServer = createServer("::", environment.apiServerPort)
    
    fun start() {
        requirePortsAreFree(environment.apiServerPort, owner = "an api server of an earlier test run")
        ApiServer.api = apiServer
        httpServer.start(wait = false)
    }
    
    fun reportedSuccesses(): List<SuccessEntity> = runBlocking { apiServer.database.successDao().getAll() }
    
    override fun close() {
        httpServer.stop()
    }
}
