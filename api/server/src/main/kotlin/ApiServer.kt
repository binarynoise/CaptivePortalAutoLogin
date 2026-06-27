package de.binarynoise.captiveportalautologin.server

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import de.binarynoise.captiveportalautologin.api.Api
import de.binarynoise.captiveportalautologin.api.json.har.HAR
import de.binarynoise.captiveportalautologin.api.json.har.generateHarFileName
import de.binarynoise.captiveportalautologin.server.database.AppDatabase
import de.binarynoise.captiveportalautologin.server.database.ErrorEntity
import de.binarynoise.captiveportalautologin.server.database.SuccessEntity
import de.binarynoise.filedb.FileDB
import de.binarynoise.logger.Logger.log
import de.binarynoise.util.json.prettyPrinter
import io.ktor.http.Url

class ApiServer(root: Path = Path(".")) : Api {
    
    companion object {
        lateinit var api: ApiServer
    }
    
    val harDB = FileDB(root, "HAR", "har")
    val harDBArchived = FileDB(root, "HAR/archived", "har")
    val harDBError = FileDB(root, "HAR/error", "har")
    
    val database = AppDatabase.createDatabase(root)
    
    init {
        runBlocking {
            database.useConnection(isReadOnly = true) {}
        }
        log("Database initialized at root ${root.toAbsolutePath()}")
    }
    
    override val har: Api.Har = object : Api.Har {
        override fun submitHar(name: String, har: HAR) {
            harDB.store(name, prettyPrinter.encodeToString(har))
            log("stored har $name")
        }
    }
    
    override val liberator: Api.Liberator = object : Api.Liberator {
        override fun getLiberatorVersion(): String {
            TODO("getLiberatorVersion Not yet implemented")
        }
        
        override fun fetchLiberatorUpdate() {
            TODO("fetchLiberatorUpdate Not yet implemented")
        }
        
        override fun reportError(error: Api.Liberator.Error) {
            val timestamp = Instant.fromEpochMilliseconds(error.timestamp)
            
            val har: HAR? = error.har
            val harName = if (har == null) {
                null
            } else with(error) {
                val host = Url(url).host
                val name = generateHarFileName(ssid, host, timestamp)
                harDBError.store(name, prettyPrinter.encodeToString(har))
                name
            }
            
            runBlocking {
                val errorEntity = ErrorEntity(
                    version = error.version,
                    timestamp = timestamp,
                    ssid = error.ssid,
                    url = error.url,
                    message = error.message,
                    solver = error.solver.orEmpty(),
                    stackTrace = error.stackTrace.orEmpty(),
                    harName = harName,
                )
                database.errorDao().insert(errorEntity)
            }
            log("Stored Api.Liberator.Error: $error")
        }
        
        override fun reportSuccess(success: Api.Liberator.Success) {
            runBlocking {
                val successEntity = SuccessEntity(
                    version = success.version,
                    timestamp = Instant.fromEpochMilliseconds(success.timestamp),
                    ssid = success.ssid,
                    url = success.url,
                    solver = success.solver.orEmpty(),
                )
                database.successDao().insert(successEntity)
            }
            log("Stored Api.Liberator.Success: $success")
        }
    }
    
    override suspend fun getSSIDs(
        limit: Int?,
        majorVersion: Int?,
        since: Instant?,
        minimum: Int?,
    ): List<String> {
        return database.SSIDDao().getSSIDs(
            limit = limit ?: 1024,
            majorVersion = majorVersion ?: Int.MAX_VALUE,
            since = since ?: (Clock.System.now() - 365.25.days),
            minimum = minimum ?: 0,
        )
    }
}
