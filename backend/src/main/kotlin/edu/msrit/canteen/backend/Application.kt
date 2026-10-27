package edu.msrit.canteen.backend

import edu.msrit.canteen.backend.api.ApiError
import edu.msrit.canteen.backend.api.ApiErrorEnvelope
import edu.msrit.canteen.backend.api.HealthResponse
import edu.msrit.canteen.backend.api.LoginRequest
import edu.msrit.canteen.backend.api.MeResponse
import edu.msrit.canteen.backend.api.RegisterRequest
import edu.msrit.canteen.backend.api.RegisterResponse
import edu.msrit.canteen.backend.api.UpdateProfileRequest
import edu.msrit.canteen.backend.api.requireAuthenticatedUser
import edu.msrit.canteen.backend.api.respondApiError
import edu.msrit.canteen.backend.auth.AccountService
import edu.msrit.canteen.backend.auth.ApiProblem
import edu.msrit.canteen.backend.auth.ProfilePatch
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
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("BackendApplication")
private val requestJson = Json { ignoreUnknownKeys = false; explicitNulls = true }

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
    val accounts = AccountService(context)
    install(ContentNegotiation) {
        json(Json { encodeDefaults = true; explicitNulls = true; ignoreUnknownKeys = false })
    }
    install(StatusPages) {
        exception<ApiProblem> { call, cause -> call.respondApiError(cause.status, cause.code, cause.message) }
        exception<BadRequestException> { call, _ ->
            call.respondApiError(HttpStatusCode.BadRequest, "INVALID_JSON", "A valid JSON request is required")
        }
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
            post("/auth/register") {
                val request = call.readJson<RegisterRequest>()
                call.respond(HttpStatusCode.Created, RegisterResponse(accounts.register(request)))
            }
            post("/auth/login") {
                val request = call.readJson<LoginRequest>()
                val response = accounts.login(request)
                if (response == null) call.respondApiError(HttpStatusCode.Unauthorized,
                    "INVALID_CREDENTIALS", "Email or password is incorrect")
                else call.respond(response)
            }
            post("/auth/logout") {
                val principal = call.requireAuthenticatedUser(context) ?: return@post
                if (accounts.logout(principal)) call.respond(HttpStatusCode.NoContent)
                else call.respondApiError(HttpStatusCode.Unauthorized, "UNAUTHENTICATED", "A valid bearer session is required")
            }
            get("/auth/me") {
                val principal = call.requireAuthenticatedUser(context) ?: return@get
                val user = accounts.me(principal)
                if (user == null) call.respondApiError(HttpStatusCode.Unauthorized, "UNAUTHENTICATED", "A valid bearer session is required")
                else call.respond(MeResponse(user))
            }
            get("/me/profile") {
                val principal = call.requireAuthenticatedUser(context) ?: return@get
                val profile = accounts.profile(principal)
                if (profile == null) call.respondApiError(HttpStatusCode.Unauthorized, "UNAUTHENTICATED", "A valid bearer session is required")
                else call.respond(profile)
            }
            patch("/me/profile") {
                val principal = call.requireAuthenticatedUser(context) ?: return@patch
                val body = call.readJsonObject()
                val request = call.decodeJson<UpdateProfileRequest>(body.toString())
                val profile = accounts.updateProfile(principal, ProfilePatch(
                    body.containsKey("fullName"), request.fullName,
                    body.containsKey("affiliation"), request.affiliation,
                    body.containsKey("department"), request.department))
                if (profile == null) call.respondApiError(HttpStatusCode.Unauthorized, "UNAUTHENTICATED", "A valid bearer session is required")
                else call.respond(profile)
            }
        }
    }
}

private suspend inline fun <reified T> io.ktor.server.application.ApplicationCall.readJson(): T =
    decodeJson(receiveText())

private inline fun <reified T> io.ktor.server.application.ApplicationCall.decodeJson(raw: String): T {
    if (raw.length > 16_384) throw ApiProblem(HttpStatusCode.BadRequest, "INVALID_JSON", "JSON request is too large")
    return try { requestJson.decodeFromString<T>(raw) }
    catch (_: Exception) { throw ApiProblem(HttpStatusCode.BadRequest, "INVALID_JSON", "A valid JSON request is required") }
}

private suspend fun io.ktor.server.application.ApplicationCall.readJsonObject(): JsonObject {
    val raw = receiveText()
    if (raw.length > 16_384) throw ApiProblem(HttpStatusCode.BadRequest, "INVALID_JSON", "JSON request is too large")
    return try { requestJson.parseToJsonElement(raw) as? JsonObject
        ?: throw IllegalArgumentException("JSON object required") }
    catch (_: Exception) { throw ApiProblem(HttpStatusCode.BadRequest, "INVALID_JSON", "A valid JSON object is required") }
}
