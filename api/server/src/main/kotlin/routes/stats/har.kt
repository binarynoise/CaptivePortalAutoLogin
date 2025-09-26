package de.binarynoise.captiveportalautologin.server.routes.stats

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.moveTo
import kotlin.io.path.name
import de.binarynoise.captiveportalautologin.api.json.har.HAR
import de.binarynoise.captiveportalautologin.server.ApiServer
import de.binarynoise.captiveportalautologin.server.routes.missingParameter
import de.binarynoise.logger.Logger
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.mustache.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

internal fun Route.harRoutes() {
    get("har") {
        call.response.header("Location", "har/")
        call.respond(HttpStatusCode.MovedPermanently)
    }
    
    route("har/") {
        get {
            val unlimited = call.parameters["unlimited"] == "true"
            val names = ApiServer.api.jsonDb.listAll<HAR>("har")
            val groups = names.map { name ->
                val parts = name.trim().split(' ').filter { it.isNotBlank() }
                val timestamp = parts.lastOrNull() ?: ""
                val domain = parts.dropLast(1).lastOrNull() ?: "unknown"
                Triple(domain, name, timestamp)
            }.groupBy({ it.first }, { it.second to it.third }).map { (domain, items) ->
                mapOf(
                    "domain" to domain,
                    "isIP" to (domain.split('.', ':').all { split -> split.all { char -> char.isDigit() } }),
                    "count" to items.size,
                )
            }.sortedWith(
                compareBy<Map<String, Comparable<*>>> { entry -> entry["isIP"] }.then(
                    Comparator { a, b ->
                        val domainA = a["domain"] as String
                        val isIPA = a["isIP"] as Boolean
                        val domainB = b["domain"] as String
                        val isIPB = b["isIP"] as Boolean
                        
                        val partsA = domainA.split('.').let { if (isIPA) it else it.reversed().drop(1) }
                        val partsB = domainB.split('.').let { if (isIPB) it else it.reversed().drop(1) }
                        
                        partsA.zip(partsB).firstOrNull { it.first != it.second }?.let { it.first.compareTo(it.second) }
                            ?: 0
                    })
            ).let { if (unlimited) it else it.take(100) }
            
            call.respond(
                MustacheContent(
                    "hars.mustache", mapOf(
                        "title" to "HAR Files",
                        "backLink" to "../",
                        "groups" to groups,
                    )
                )
            )
        }
        
        get("download/{id}") {
            val id = call.parameters["id"] ?: error("id not set")
            
            val path = ApiServer.api.jsonDb.base<HAR>().resolve(id)
            if (!path.exists()) {
                Logger.log("file not found: $path")
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            call.respondPath(path)
        }
        
        post("archive/{id}") {
            val id = call.parameters["id"] ?: error("id not set")
            val base = ApiServer.api.jsonDb.base<HAR>()
            val src = base.resolve(id)
            if (!src.exists()) {
                Logger.log("file not found: $src")
                call.respond(HttpStatusCode.NotFound)
                return@post
            }
            val archiveDir = base.resolve("archived").apply { createDirectories() }
            var dest = archiveDir.resolve(id)
            if (dest.exists()) {
                val alt = "$id-archived-${System.currentTimeMillis()}"
                dest = archiveDir.resolve(alt)
            }
            src.moveTo(dest)
            call.response.header("Location", "../")
            call.respond(HttpStatusCode.SeeOther)
        }
        
        get("group/{domain}/") {
            val domainParam = call.parameters["domain"] ?: missingParameter("domain")
            val names = ApiServer.api.jsonDb.listAll<HAR>("har")
            val entries = names.mapNotNull { name ->
                val parts = name.trim().split(' ').filter { it.isNotBlank() }
                val timestamp = parts.lastOrNull() ?: ""
                val domain = parts.dropLast(1).lastOrNull() ?: "unknown"
                if (domain == domainParam) {
                    mapOf(
                        "name" to name,
                        "timestamp" to timestamp,
                        "download" to "../../download/$name.har",
                        "archiveAction" to "../../archive/$name.har",
                    )
                } else null
            }.sortedByDescending { it["timestamp"] as String }
            
            call.respond(
                MustacheContent(
                    "har-group.mustache", mapOf(
                        "title" to "HAR Files - $domainParam",
                        "backLink" to "../../",
                        "domain" to domainParam,
                        "entries" to entries,
                    )
                )
            )
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

private suspend fun ApplicationCall.respondPath(path: Path) {
    response.header(
        HttpHeaders.ContentDisposition,
        ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, path.name).toString(),
    )
    respondFile(path.toFile())
}
