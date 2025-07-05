@file:OptIn(ExperimentalSerializationApi::class, ExperimentalTime::class)

package de.binarynoise.captiveportalautologin.server

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import de.binarynoise.captiveportalautologin.api.Api
import de.binarynoise.captiveportalautologin.api.json.har.HAR
import de.binarynoise.filedb.JsonDB
import org.jetbrains.exposed.dao.id.CompositeIdTable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert
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
                    it[ssid] = error.ssid
                    it[host] = error.host
                    it[url] = error.url
                    it[message] = error.message
                    it[date] = dateTime()
                }
            }
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
        
        val count = integer("count")
        
        override val primaryKey: PrimaryKey = PrimaryKey(arrayOf(ssid, url, year, month), name = "successes_pkey")
    }
    
    object Errors : IntIdTable("errors") {
        val ssid = varchar("ssid", 128)
        val host = varchar("host", 128)
        val url = varchar("url", 1024)
        val message = varchar("error", 1024)
        
        val date = datetime("date")
    }
}
