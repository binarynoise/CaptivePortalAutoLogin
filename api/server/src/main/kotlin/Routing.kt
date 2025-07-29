package de.binarynoise.captiveportalautologin.server

import kotlin.io.path.exists
import kotlin.time.ExperimentalTime
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.char
import kotlinx.html.*
import de.binarynoise.captiveportalautologin.api.Api
import de.binarynoise.captiveportalautologin.api.json.har.HAR
import de.binarynoise.captiveportalautologin.server.Routing.api
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object Routing {
    lateinit var api: ApiServer
}

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Welcome to Captive Portal Auto Login API")
        }
        route("/api") {
            route("/har") {
                put("/{name}") {
                    val har = call.receive<HAR>()
                    val name = call.parameters["name"] ?: error("parameter 'name' not set")
                    api.har.submitHar(name, har)
                    call.respond(HttpStatusCode.Created)
                }
                
                route("/{name}", HttpMethod("ECHO")) {
                    handle {
                        val name = call.parameters["name"] ?: error("parameter 'name' not set")
                        call.respondText("api/har/{name} here, name is $name")
                    }
                }
            }
            route("/liberator") {
                put<Api.Liberator.Error>("error") {
                    api.liberator.reportError(it)
                    call.respond(HttpStatusCode.Created)
                }
                put<Api.Liberator.Success>("success") {
                    api.liberator.reportSuccess(it)
                    call.respond(HttpStatusCode.Created)
                }
            }
        }
        
        get("/stats") {
            call.response.header("Location", "/stats/")
            call.respond(HttpStatusCode.MovedPermanently)
        }
        route("/stats/", stats())
    }
}

val compoundFormat = DateTimeComponents.Format {
    year(); char('-'); monthNumber(); char('-'); day()
    char(' ')
    hour(); char(':'); minute(); char(':'); second()
}


@OptIn(ExperimentalTime::class)
private fun stats(): Route.() -> Unit = {
    fun HEAD.commonCss() {
        style {
            unsafe {
                +"""
                    @media (prefers-color-scheme: dark) {
                        html {
                            color-scheme: dark;
                        }
                    }
                    
                    html {
                        font-family: sans-serif;
                    }
                    
                    table {
                        border-collapse: collapse;
                    }
                    
                    table, th, td {
                        border: 1px solid currentColor;
                        padding: 2px;
                    }
                """.trimIndent()
            }
        }
    }
    
    get {
        call.respondHtml {
            head {
                title { +"Captive Portal Auto Login - Stats" }
                commonCss()
            }
            body {
                h1 { +"Captive Portal Auto Login - Statistics" }
                p {
                    a(href = "successes") { +"Successes" }
                }
                p {
                    a(href = "errors") { +"Errors" }
                }
                p {
                    a(href = "har") { +"Submitted HAR files" }
                }
            }
        }
    }
    
    get("successes") {
        call.respondHtml {
            head {
                title { +"Captive Portal Auto Login - Successes" }
                commonCss()
            }
            body {
                h1 { +"Captive Portal Auto Login - Successes" }
                p {
                    a(href = "/stats/") { +"back" }
                }
                table {
                    transaction {
                        Tables.Successes.selectAll().orderBy(
                            Tables.Successes.year to SortOrder.DESC,
                            Tables.Successes.month to SortOrder.DESC,
                        ).limit(50).toList()
                    }.forEach {
                        tr {
                            td { +it[Tables.Successes.year] }
                            td { +it[Tables.Successes.month] }
                            td { +it[Tables.Successes.version] }
                            td { +it[Tables.Successes.ssid] }
                            td { +it[Tables.Successes.url] }
                            td { +it[Tables.Successes.count] }
                        }
                    }
                }
            }
        }
    }
    
    get("errors") {
        call.respondHtml {
            head {
                title { +"Captive Portal Auto Login - Errors" }
                commonCss()
            }
            body {
                h1 { +"Captive Portal Auto Login - Errors" }
                p {
                    a(href = "/stats/") { +"back" }
                }
                table {
                    transaction {
                        Tables.Errors.selectAll().orderBy(
                            Tables.Errors.timestamp to SortOrder.DESC
                        ).limit(50).toList()
                    }.forEach {
                        tr {
                            td { +it[Tables.Errors.timestamp].format(compoundFormat) }
                            td { +it[Tables.Errors.version] }
                            td { +it[Tables.Errors.ssid] }
                            td { +it[Tables.Errors.url] }
                            td { +it[Tables.Errors.message] }
                        }
                    }
                }
            }
        }
    }
    
    route("har") {
        get {
            call.respondHtml {
                head {
                    title { +"Captive Portal Auto Login - HAR files" }
                    commonCss()
                }
                body {
                    h1 { +"Captive Portal Auto Login - HAR files" }
                    p {
                        a(href = "/stats/") { +"back" }
                    }
                    val harEntries = api.jsonDb.listAll<HAR>("har")
                    if (harEntries.isEmpty()) {
                        p { +"no entries" }
                    } else {
                        table {
                            harEntries.forEach { k ->
                                tr {
                                    td { +k }
                                    td {
                                        a("download/$k.har") { +"download" }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        get("download/{id}") {
            val id = call.parameters["id"] ?: error("id not set")
            
            val path = api.jsonDb.base<HAR>().resolve(id)
            if (!path.exists()) {
                call.response.status(HttpStatusCode.NotFound)
                return@get
            }
            call.respondPath(path)
        }
    }
}
