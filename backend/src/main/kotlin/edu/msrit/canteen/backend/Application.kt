package edu.msrit.canteen.backend

import edu.msrit.canteen.backend.api.ApiError
import edu.msrit.canteen.backend.api.ApiErrorEnvelope
import edu.msrit.canteen.backend.api.HealthResponse
import edu.msrit.canteen.backend.api.requireAuthenticatedUser
import edu.msrit.canteen.backend.api.respondApiError
import edu.msrit.canteen.backend.core.BackendBootstrap
import edu.msrit.canteen.backend.core.BackendConfig
import edu.msrit.canteen.backend.core.BackendContext
import edu.msrit.canteen.backend.core.close
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("BackendApplication")

fun main() {
    val config = BackendConfig.fromEnvironment()
    val context = BackendBootstrap.open(config)
    Runtime.getRuntime().addShutdownHook(Thread { context.close() })
    embeddedServer(Netty, host = config.httpHost, port = config.httpPort) {
        backendModule(context)
    }.start(wait = true)
}

/** The lead registers contributor route modules here only after their branches merge. */
fun Application.backendModule(context: BackendContext) {
    install(ContentNegotiation) {
        json(Json { encodeDefaults = true; explicitNulls = true; ignoreUnknownKeys = false })
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            logger.error("Request failed", cause)
            call.respond(HttpStatusCode.InternalServerError,
                ApiErrorEnvelope(ApiError("INTERNAL_ERROR", "The request could not be completed")))
        }
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respond(HttpStatusCode.NotFound,
                ApiErrorEnvelope(ApiError("NOT_FOUND", "The requested resource was not found")))
        }
    }
    routing {
        get("/health") { call.respond(HealthResponse("ok")) }
        get("/ready") {
            if (BackendBootstrap.isReady(context.dataSource)) call.respond(HealthResponse("ready"))
            else call.respondApiError(HttpStatusCode.ServiceUnavailable, "DATABASE_NOT_READY", "Database is unavailable")
        }
        route("/api/v1") {
            // Phase A reserves only core/auth paths. Phase B supplies working handlers without changing DTOs.
            post("/auth/register") { call.authNotReady() }
            post("/auth/login") { call.authNotReady() }
            post("/auth/logout") { if (call.requireAuthenticatedUser(context) != null) call.authNotReady() }
            get("/auth/me") { if (call.requireAuthenticatedUser(context) != null) call.authNotReady() }
            get("/me/profile") { if (call.requireAuthenticatedUser(context) != null) call.authNotReady() }
            patch("/me/profile") { if (call.requireAuthenticatedUser(context) != null) call.authNotReady() }
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.authNotReady() =
    respondApiError(HttpStatusCode.ServiceUnavailable, "AUTH_NOT_READY", "Authentication is not active at this foundation checkpoint")
