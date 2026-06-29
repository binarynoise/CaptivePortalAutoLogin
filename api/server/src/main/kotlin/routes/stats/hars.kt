package de.binarynoise.captiveportalautologin.server.routes.stats

import java.nio.file.Path
import kotlin.io.path.fileSize
import kotlin.io.path.nameWithoutExtension
import de.binarynoise.captiveportalautologin.api.json.har.parseHarFileName
import de.binarynoise.captiveportalautologin.server.ApiServer
import de.binarynoise.captiveportalautologin.server.routes.FileSize
import de.binarynoise.captiveportalautologin.server.routes.missingParameter
import de.binarynoise.captiveportalautologin.server.routes.respondPathWithContentDisposition
import de.binarynoise.captiveportalautologin.server.routes.respondStatus
import de.binarynoise.captiveportalautologin.server.routes.stats.HarType.ARCHIVED
import de.binarynoise.captiveportalautologin.server.routes.stats.HarType.ERROR
import de.binarynoise.captiveportalautologin.server.routes.stats.HarType.REGULAR
import de.binarynoise.filedb.FileDB
import de.binarynoise.logger.Logger.log
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.mustache.MustacheContent
import io.ktor.server.request.header
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.annotations.DataSchema
import org.jetbrains.kotlinx.dataframe.api.add
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.api.toDataFrame

@DataSchema
data class HarEntry(
    val name: String,
    val ssid: String,
    val domain: String,
    val timestamp: String,
    val type: HarType,
    val fileSize: FileSize,
)

enum class HarType {
    REGULAR, ARCHIVED, ERROR;
    
    val db: FileDB
        get() = when (this) {
            REGULAR -> ApiServer.api.harDB
            ARCHIVED -> ApiServer.api.harDBArchived
            ERROR -> ApiServer.api.harDBError
        }
    
    override fun toString(): String = this.name.lowercase()
    
    companion object {
        fun fromString(type: String): HarType {
            return valueOf(type.uppercase())
        }
    }
}

private fun parseHarPath(path: Path, type: HarType): HarEntry {
    val fileSize = FileSize(path.fileSize())
    val name = path.nameWithoutExtension
    val (ssid, domain, timestamp) = parseHarFileName(name) ?: return HarEntry(name, "", "", "", type, fileSize)
    return HarEntry(name, ssid, domain, timestamp, type, fileSize)
}


internal fun Route.harRoutes() {
    get("hars") {
        call.response.header("Location", "hars/")
        call.respondStatus(HttpStatusCode.MovedPermanently)
    }
    
    route("hars/") {
        get {
            val columnDefinitions: DataFrame<ColumnDefinition> = dataFrameOf(
                ColumnDefinition("timestamp", "Timestamp", Comparators.RegularComparator),
                ColumnDefinition("ssid", "SSID", Comparators.RegularComparator),
                ColumnDefinition("domain", "Domain", Comparators.DomainComparator),
                ColumnDefinition("name", "Name", Comparators.RegularComparator),
                ColumnDefinition("fileSize", "File Size", Comparators.RegularComparator),
                ColumnDefinition("type", "Type", Comparators.RegularComparator),
            )
            val actionColumnDefinitions: DataFrame<ActionColumnDefinition> = dataFrameOf(
                ActionColumnDefinition("download", "Download", listOf("name", "type")),
                ActionColumnDefinition("archive", "Archive", listOf("name", "type")),
                ActionColumnDefinition("delete", "Delete", listOf("name", "type")),
            )
            val defaultGroups: Set<String> = setOf("domain")
            val defaultSort = "domain-asc"
            
            val preFilterDefinitions: List<PreFilterDefinition> = listOf(
                PreFilterDefinition("all", "All") {
                    loadHarEntries(includeRegular = true, includeArchived = true, includeError = true)
                },
                PreFilterDefinition("regular", "Regular") {
                    loadHarEntries(includeRegular = true)
                },
                PreFilterDefinition("archived", "Archived") {
                    loadHarEntries(includeArchived = true)
                },
                PreFilterDefinition("error", "Error") {
                    loadHarEntries(includeError = true)
                },
            )
            
            val tableData = generateTableData(
                call,
                columnDefinitions,
                preFilterDefinitions,
                defaultGroups,
                defaultSort = defaultSort,
                actionColumnDefinitions = actionColumnDefinitions,
                defaultPreFilter = "regular"
            )
            
            call.respond(
                MustacheContent(
                    "hars.mustache", mapOf(
                        "title" to "HAR Files",
                        "backLink" to "../",
                    ) + tableData.toMap()
                )
            )
        }
        
        get("download/{id}") {
            val id = call.parameters["id"] ?: missingParameter("id")
            val type = HarType.fromString(call.parameters["type"] ?: missingParameter("type"))
            val db = type.db
            
            if (db.exists(id)) {
                call.respondPathWithContentDisposition(db.file(id))
                return@get
            }
            
            log("file not found: id=$id, type=$type")
            call.respondStatus(HttpStatusCode.NotFound)
        }
        
        post("archive/{id}") {
            val id = call.parameters["id"] ?: missingParameter("id")
            
            if (!REGULAR.db.exists(id)) {
                log("archive: file not found: $id")
                call.respondStatus(HttpStatusCode.NotFound)
                return@post
            }
            
            if (ARCHIVED.db.exists(id)) {
                log("archive: file already exists: $id")
                call.respond(HttpStatusCode.Conflict, "Archived file already exists")
                return@post
            }
            
            REGULAR.db.moveTo(ARCHIVED.db, id)
            log("archive: moved: $id")
            call.response.header("Location", call.request.header(HttpHeaders.Referrer) ?: "./")
            call.respondStatus(HttpStatusCode.SeeOther)
        }
        
        post("unarchive/{id}") {
            val id = call.parameters["id"] ?: missingParameter("id")
            
            if (!ARCHIVED.db.exists(id)) {
                log("unarchive: file not found: $id")
                call.respondStatus(HttpStatusCode.NotFound)
                return@post
            }
            
            if (REGULAR.db.exists(id)) {
                log("unarchive: file already exists: $id")
                call.respond(HttpStatusCode.Conflict, "Unarchived file already exists")
                return@post
            }
            
            ARCHIVED.db.moveTo(REGULAR.db, id)
            log("unarchive: moved: $id")
            call.response.header("Location", call.request.header(HttpHeaders.Referrer) ?: "./")
            call.respondStatus(HttpStatusCode.SeeOther)
        }
        
        post("delete/{id}") {
            val id = call.parameters["id"] ?: missingParameter("id")
            val type = HarType.fromString(call.parameters["type"] ?: missingParameter("type"))
            val db = type.db
            
            if (!db.exists(id)) {
                log("delete: file not found: $id")
                call.respondStatus(HttpStatusCode.NotFound)
                return@post
            }
            
            db.delete(id)
            log("delete: $id")
            call.response.header("Location", call.request.header(HttpHeaders.Referrer) ?: "./")
            call.respondStatus(HttpStatusCode.SeeOther)
        }
        
        get("har-upload") {
            call.respond(
                MustacheContent(
                    "har-upload.mustache", mapOf(
                        "title" to "Upload HAR File",
                        "backLink" to "./",
                    )
                )
            )
        }
    }
}

private fun loadHarEntries(
    includeRegular: Boolean = false,
    includeArchived: Boolean = false,
    includeError: Boolean = false,
): DataFrame<*> {
    val entries = mutableListOf<HarEntry>()
    
    if (includeRegular) {
        val regular = REGULAR.db.listAllFiles()
        entries.addAll(regular.map { parseHarPath(it, REGULAR) })
    }
    
    if (includeArchived) {
        val archived = ARCHIVED.db.listAllFiles()
        entries.addAll(archived.map { parseHarPath(it, ARCHIVED) })
    }
    
    if (includeError) {
        val error = ERROR.db.listAllFiles()
        entries.addAll(error.map { parseHarPath(it, ERROR) })
    }
    
    val dataFrame = entries.toDataFrame() //
        .add("download") {
            ActionColumnAction(
                "Download",
                "download/${it.name}?type=${it.type}",
                "get",
            )
        }.add("archive") {
            when (it.type) {
                REGULAR -> ActionColumnAction("Archive", "archive/${it.name}", "post")
                ARCHIVED -> ActionColumnAction("Unarchive", "unarchive/${it.name}", "post")
                else -> null
            }
        }.add("delete") {
            ActionColumnAction("Delete", "delete/${it.name}?type=${it.type}", "post")
        }
    
    return dataFrame
}
