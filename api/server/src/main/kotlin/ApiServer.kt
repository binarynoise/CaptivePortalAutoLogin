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
import de.binarynoise.logger.Logger.log
import io.ktor.http.*
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.plus
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.core.dao.id.CompositeIdTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
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
        Database.connect(ds)
        
        transaction {
            addLogger(StdOutSqlLogger)
            SchemaUtils.createDatabase()
            val tables = arrayOf(Tables.Successes, Tables.Errors)
            SchemaUtils.create(tables = tables)
            val inconsistencies = SchemaUtils.checkMappingConsistence(tables = tables)
            check(inconsistencies.isEmpty()) {
                log("found inconsistencies:")
                inconsistencies.forEach(::println)
            }
            
            log("Database initialized")
        }
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
            val d = Instant.fromEpochMilliseconds(error.timestamp).toLocalDateTime(TimeZone.UTC)
            
            fun sanitizeUrl(oldUrl: String): String {
                if (oldUrl == "") return ""
                return URLBuilder(oldUrl).apply {
                    val blockListed = listOf(
                        "challenge",
                        "sessionid",
                        "called",
                        "nasid",
                        "ip",
                        "ts",
                        "mac",
                    )
                    parameters.names().intersect(blockListed).forEach { parameters[it] = "_" }
                }.build().toString()
            }
            
            val sanitizedUrl = sanitizeUrl(error.url)
            
            transaction {
                Tables.Errors.upsert(onUpdate = {
                    it[Tables.Errors.count] = Tables.Errors.count + 1
                }) {
                    it[ssid] = error.ssid
                    it[url] = sanitizedUrl
                    it[message] = error.message
                    
                    it[year] = d.year
                    it[month] = d.month.number
                    it[version] = error.version
                    
                    it[count] = 1
                }
            }
            log("Stored Api.Liberator.Error: $error")
        }
        
        override fun reportSuccess(success: Api.Liberator.Success) {
            val d = date()
            transaction {
                Tables.Successes.upsert(onUpdate = {
                    it[Tables.Successes.count] = Tables.Successes.count + 1
                }) {
                    it[ssid] = success.ssid
                    it[url] = success.url
                    
                    it[year] = d.year
                    it[month] = d.month.number
                    it[version] = success.version
                    
                    it[count] = 1
                }
            }
            log("Stored Api.Liberator.Success: $success")
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
    
    object Errors : CompositeIdTable("errors") {
        val ssid = varchar("ssid", 128)
        val url = varchar("url", 1024)
        val message = varchar("error", 1024)
        
        val year = integer("year")
        val month = integer("month")
        val version = varchar("version", 128)
        
        val count = integer("count")
        
        override val primaryKey: PrimaryKey =
            PrimaryKey(arrayOf(ssid, url, message, year, month, version), name = "errors_pkey")
    }
}
