package de.binarynoise.captiveportalautologin.server

import kotlin.io.path.Path
import de.binarynoise.captiveportalautologin.api.json.har.HAR
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing

var api = ApiImpl(Path("."))

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Hello World!")
        }
        route("/api") {
            route("/har") {
                
                post("/{name}") {
//                    call.respondText("api/har/{name} here, name is " + call.parameters["name"])
                    val har = call.receive<HAR>()
                    val name = call.parameters["name"] ?: error("parameter 'name' not set")
                    api.har.submitHar(name, har)
                    call.respond(200)
                }
                route("/{name}", HttpMethod("echo")) { handle { call.respondText("api/har/{name} here, name is " + call.parameters["name"]) } }
//                route("/{name}", HttpMethod.Post) { handle { call.respondText("api/har/{name} here, name is " + call.parameters["name"]) } }
            
            }
            route("/liberator") {
            
            }
        }
        
        trace { routingResolveTrace ->
            println("receiving call with path segments: " + routingResolveTrace.segments)
        }
    }
}
