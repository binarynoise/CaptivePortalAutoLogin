@file:OptIn(ExperimentalSerializationApi::class)

package de.binarynoise.captiveportalautologin.server

import java.nio.file.Path
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.io.path.Path
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import de.binarynoise.captiveportalautologin.api.Api
import de.binarynoise.captiveportalautologin.api.json.har.HAR
import de.binarynoise.filedb.CachedCsvDB
import de.binarynoise.filedb.JsonDB
import java.time.LocalDateTime as JavaLocalDateTime

class ApiImpl(root: Path = Path(".")) : Api {
    
    val db = JsonDB(root, Json {
        encodeDefaults = false
        explicitNulls = false
        prettyPrint = true
    })
    
    val successDB = CachedCsvDB(root, "success.csv").apply {
        load()
        startBackgroundSave()
    }
    
    override val har: Api.Har = object : Api.Har {
        override fun submitHar(name: String, har: HAR) {
            db.store(name, har, "har")
        }
    }
    
    override val liberator: Api.Liberator = object : Api.Liberator {
        override fun getLiberatorVersion(): String {
            TODO("getLiberatorVersion Not yet implemented")
        }
        
        override fun fetchLiberatorUpdate() {
            TODO("fetchLiberatorUpdate Not yet implemented")
        }
        
        private fun date(): String = JavaLocalDateTime.now(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_INSTANT)
        
        override fun reportError(error: Api.Liberator.Error) = with(error) {
            val key = "${host}_${ssid}_${date()}"
            db.store(key, error)
        }
        
        // TODO: use integers instead of strings
        override fun reportSuccess(success: Api.Liberator.Success) = with(success) {
            successDB[ssid to url] = (successDB[ssid to url]?.toInt() ?: 0).plus(1).toString()
        }
    }
}
