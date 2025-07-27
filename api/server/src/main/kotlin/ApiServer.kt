@file:OptIn(ExperimentalSerializationApi::class, ExperimentalTime::class)

package de.binarynoise.captiveportalautologin.server

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import de.binarynoise.captiveportalautologin.api.Api
import de.binarynoise.captiveportalautologin.api.json.har.HAR
import de.binarynoise.filedb.JsonDB
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.plus
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.dao.id.CompositeIdTable
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import org.sqlite.SQLiteDataSource

class ApiServer(root: Path = Path(".")) : Api {
    
    val jsonDb = JsonDB(root, Json {
        encodeDefaults = false
        explicitNulls = false
        prettyPrint = true
    })
    
    init {
        val ds = SQLiteDataSource().apply {
            url = "jdbc:sqlite:${root.resolve("db.sqlite").absolutePathString()}"
            setEncoding("UTF-8")
        }
        Database.connect(ds, databaseConfig = DatabaseConfig {
            this.sqlLogger = StdOutSqlLogger
        })
        
        transaction {
            SchemaUtils.createDatabase()
            SchemaUtils.create(Tables.Successes, Tables.Errors)
        }
    }
    
    override val har: Api.Har = object : Api.Har {
        override fun submitHar(name: String, har: HAR) {
            jsonDb.store(name, har, "har")
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
            transaction {
                Tables.Errors.insert {
                    it[version] = error.version
                    it[ssid] = error.ssid
                    it[url] = error.url
                    it[message] = error.message
                    it[timestamp] = Instant.fromEpochMilliseconds(error.timestamp)
                }
            }
        }
        
        override fun reportSuccess(success: Api.Liberator.Success) {
            val d = date()
            transaction {
                Tables.Successes.upsert(onUpdate = {
                    it[Tables.Successes.count] = Tables.Successes.count + 1
                }) {
                    it[version] = success.version
                    it[ssid] = success.ssid
                    it[url] = success.url
                    it[year] = d.year
                    it[month] = d.month.number
                    it[count] = 1
                }
            }
        }
    }
}

object Tables {
    object Successes : CompositeIdTable("successes") {
        val ssid = varchar("ssid", 128)
        val url = varchar("url", 1024)
        val year = integer("year")
        val month = integer("month")
        val version = varchar("version", 128)
        
        val count = integer("count")
        
        override val primaryKey: PrimaryKey =
            PrimaryKey(arrayOf(version, ssid, url, year, month), name = "successes_pkey")
    }
    
    object Errors : IntIdTable("errors") {
        val version = varchar("version", 128)
        val timestamp: Column<Instant> = timestamp("timestamp")
        val ssid = varchar("ssid", 128)
        val url = varchar("url", 1024)
        val message = varchar("error", 1024)
    }
}
