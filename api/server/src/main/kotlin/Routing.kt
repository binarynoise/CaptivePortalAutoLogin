package de.binarynoise.captiveportalautologin.server

import de.binarynoise.captiveportalautologin.api.Api
import de.binarynoise.captiveportalautologin.api.json.har.HAR
import de.binarynoise.captiveportalautologin.server.Routing.api
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

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
                        call.respondText("api/har/{name} here, name is " + (call.parameters["name"] ?: error("parameter 'name' not set")))
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
