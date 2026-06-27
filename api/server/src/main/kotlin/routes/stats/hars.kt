package de.binarynoise.captiveportalautologin.server.routes.stats

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.moveTo
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import de.binarynoise.captiveportalautologin.api.json.har.HAR
import de.binarynoise.captiveportalautologin.server.ApiServer
import de.binarynoise.captiveportalautologin.server.routes.missingParameter
import de.binarynoise.logger.Logger.log
import io.ktor.http.ContentDisposition
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.mustache.MustacheContent
import io.ktor.server.request.header
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import nl.jacobras.humanreadable.HumanReadable
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
    val archived: Boolean,
    val fileSize: FileSize,
)

internal fun Route.harRoutes() {
    get("hars") {
        call.response.header("Location", "hars/")
        call.respond(HttpStatusCode.MovedPermanently)
    }
    
    route("hars/") {
        get {
            val columnDefinitions: DataFrame<ColumnDefinition> = dataFrameOf(
                ColumnDefinition("timestamp", "Timestamp", Comparators.RegularComparator),
                ColumnDefinition("ssid", "SSID", Comparators.RegularComparator),
                ColumnDefinition("domain", "Domain", Comparators.DomainComparator),
                ColumnDefinition("name", "Name", Comparators.RegularComparator),
                ColumnDefinition("fileSize", "File Size", Comparators.RegularComparator),
                ColumnDefinition("archived", "Archived", Comparators.RegularComparator),
            )
            val actionColumnDefinitions: DataFrame<ActionColumnDefinition> = dataFrameOf(
                ActionColumnDefinition("download", "Download", listOf("name")),
                ActionColumnDefinition("archive", "Archive", listOf("name", "archived")),
                ActionColumnDefinition("delete", "Delete", listOf("name", "archived")),
            )
            val defaultGroups: Set<String> = setOf("domain")
            val defaultSort = "domain-asc"
            
            val preFilterDefinitions: List<PreFilterDefinition> = listOf(
                PreFilterDefinition("all", "All") {
                    loadHarEntries(includeRegular = true, includeArchived = true)
                },
                PreFilterDefinition("regular", "Regular") {
                    loadHarEntries(includeRegular = true, includeArchived = false)
                },
                PreFilterDefinition("archived", "Archived") {
                    loadHarEntries(includeRegular = false, includeArchived = true)
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
            val archived = "archived" in call.request.queryParameters && call.request.queryParameters.get("archived")
                ?.takeIf { it.isNotBlank() }
                ?.toBooleanStrict() ?: true
            
            if (!archived) {
                val path = harBase.resolve(id)
                if (path.exists()) {
                    call.respondPath(path)
                    return@get
                }
            } else {
                val path = harBase.resolve("archived").resolve(id)
                if (path.exists()) {
                    call.respondPath(path)
                    return@get
                }
            }
            
            log("file not found: id=$id, archived=$archived")
            call.respond(HttpStatusCode.NotFound)
        }
        
        post("archive/{id}") {
            val id = call.parameters["id"] ?: missingParameter("id")
            val src = harBase.resolve(id)
            if (!src.exists()) {
                log("archive: file not found: $src")
                call.respond(HttpStatusCode.NotFound)
                return@post
            }
            val archiveDir = harBase.resolve("archived").apply { createDirectories() }
            val dest = archiveDir.resolve(id)
            if (dest.exists()) {
                log("archive: file already exists: $dest")
                call.respond(HttpStatusCode.Conflict, "Archived file already exists")
                return@post
            }
            src.moveTo(dest)
            log("archive: moved: $id")
            call.response.header("Location", call.request.header(HttpHeaders.Referrer) ?: "./")
            call.respond(HttpStatusCode.SeeOther)
        }
        
        post("unarchive/{id}") {
            val id = call.parameters["id"] ?: missingParameter("id")
            val src = harBase.resolve("archived").resolve(id)
            if (!src.exists()) {
                log("unarchive: file not found: $src")
                call.respond(HttpStatusCode.NotFound)
                return@post
            }
            val dest = harBase.resolve(id)
            if (dest.exists()) {
                log("unarchive: file already exists: $dest")
                call.respond(HttpStatusCode.Conflict, "Unarchived file already exists")
                return@post
            }
            src.moveTo(dest)
            log("unarchive: moved: $id")
            call.response.header("Location", call.request.header(HttpHeaders.Referrer) ?: "./")
            call.respond(HttpStatusCode.SeeOther)
        }
        
        post("delete/{id}") {
            val id = call.parameters["id"] ?: missingParameter("id")
            val archived = call.parameters["archived"] == "true"
            val file = harBase.resolve(if (archived) "archived" else "").resolve(id)
            if (!file.exists()) {
                log("delete: file not found: $file")
                call.respond(HttpStatusCode.NotFound)
                return@post
            }
            file.deleteExisting()
            log("delete: $id")
            call.response.header("Location", call.request.header(HttpHeaders.Referrer) ?: "./")
            call.respond(HttpStatusCode.SeeOther)
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

private fun loadHarEntries(includeRegular: Boolean, includeArchived: Boolean): DataFrame<*> {
    val entries = mutableListOf<HarEntry>()
    
    if (includeRegular) {
        val regular = harBase.listDirectoryEntries("*.har")
        entries.addAll(regular.map { parseHarPath(it, archived = false) })
    }
    
    if (includeArchived) {
        val archivedDir = harBase.resolve("archived")
        if (archivedDir.exists()) {
            val archived = archivedDir.listDirectoryEntries("*.har")
            entries.addAll(archived.map { parseHarPath(it, archived = true) })
        }
    }
    
    val dataFrame = entries.toDataFrame() //
        .add("download") {
            ActionColumnAction(
                "Download",
                "download/${it.name}.har${if (it.archived) "?archived" else ""}",
                "get",
            )
        }.add("archive") {
            if (it.archived) ActionColumnAction("Unarchive", "unarchive/${it.name}.har", "post")
            else ActionColumnAction("Archive", "archive/${it.name}.har", "post")
        }.add("delete") {
            ActionColumnAction("Delete", "delete/${it.name}.har?archived=${it.archived}", "post")
        }
    
    return dataFrame
}


class FileSize(val value: Long) : Comparable<FileSize> {
    override fun toString(): String = HumanReadable.fileSize(value, decimals = 1)
    override fun compareTo(other: FileSize): Int = value.compareTo(other.value)
}

private val harBase: Path = ApiServer.api.jsonDb.base<HAR>()
val harFileNameRegex = """^(?:(.+) )?(\S+) ([\d-]+T[\d:]+(?:\.\d+)?Z(?:[\d+:.-]+)?)$""".toRegex()
private fun parseHarPath(path: Path, archived: Boolean): HarEntry {
    val fileSize = FileSize(path.fileSize())
    
    val name = path.nameWithoutExtension
    val match = harFileNameRegex.matchEntire(name.trim()) ?: return HarEntry(name, "", "", "", archived, fileSize)
    val ssid = match.groupValues[1]
    val domain = match.groupValues[2]
    val timestamp = match.groupValues[3]
    
    return HarEntry(name, ssid, domain, timestamp, archived, fileSize)
}

private suspend fun ApplicationCall.respondPath(path: Path) {
    response.header(
        HttpHeaders.ContentDisposition,
        ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, path.name).toString(),
    )
    respondFile(path.toFile())
}
