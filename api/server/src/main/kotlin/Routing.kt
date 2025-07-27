package de.binarynoise.captiveportalautologin.server

import de.binarynoise.captiveportalautologin.api.Api
import de.binarynoise.captiveportalautologin.api.json.har.HAR
import de.binarynoise.captiveportalautologin.server.Routing.api
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

object Routing {
    lateinit var api: ApiServer
}

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Hello World!")
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
    }
}
