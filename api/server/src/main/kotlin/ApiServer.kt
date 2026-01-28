@file:OptIn(ExperimentalSerializationApi::class, ExperimentalTime::class)

package de.binarynoise.captiveportalautologin.server

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import de.binarynoise.captiveportalautologin.api.Api
import de.binarynoise.captiveportalautologin.api.json.har.HAR
import de.binarynoise.captiveportalautologin.server.database.AppDatabase
import de.binarynoise.captiveportalautologin.server.database.ErrorEntity
import de.binarynoise.filedb.JsonDB
import de.binarynoise.logger.Logger.log

class ApiServer(root: Path = Path(".")) : Api {
    
    companion object {
        lateinit var api: ApiServer
    }
    
    val jsonDb = JsonDB(root, Json {
        encodeDefaults = false
        explicitNulls = false
        prettyPrint = true
    })
    
    val database = AppDatabase.createDatabase(root)
    
    init {
        runBlocking {
            database.useConnection(isReadOnly = true) {}
        }
        log("Database initialized")
    }
    
    override val har: Api.Har = object : Api.Har {
        override fun submitHar(name: String, har: HAR) {
            jsonDb.store(name, har, "har")
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
        
        private fun dateTime() = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        private fun date() = dateTime().date
        
        override fun reportError(error: Api.Liberator.Error) {
            runBlocking {
                val errorEntity = ErrorEntity(
                    version = error.version,
                    timestamp = Instant.fromEpochMilliseconds(error.timestamp),
                    ssid = error.ssid,
                    url = error.url,
                    message = error.message,
                    solver = error.solver.orEmpty(),
                    stackTrace = error.stackTrace.orEmpty()
                )
                database.errorDao().insert(errorEntity)
            }
            log("Stored Api.Liberator.Error: $error")
        }
        
        override fun reportSuccess(success: Api.Liberator.Success) {
            val d = date()
            runBlocking {
                database.successDao().insertOrIncrement(
                    version = success.version,
                    year = d.year,
                    month = d.month.number,
                    ssid = success.ssid,
                    url = success.url,
                    solver = success.solver.orEmpty(),
                )
            }
            log("Stored Api.Liberator.Success: $success")
        }
    }
}
