package edu.msrit.canteen.backend.api

import edu.msrit.canteen.backend.auth.AuthenticatedPrincipal
import edu.msrit.canteen.backend.core.BackendContext
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.response.respond

private val bearerPattern = Regex("^Bearer ([A-Za-z0-9_-]{32,512})$")

suspend fun ApplicationCall.respondApiError(status: HttpStatusCode, code: String, message: String) {
    respond(status, ApiErrorEnvelope(ApiError(code, message)))
}

/** Returns null after emitting 401. A token is never accepted as a role assertion. */
suspend fun ApplicationCall.requireAuthenticatedUser(context: BackendContext): AuthenticatedPrincipal? {
    val token = request.header(HttpHeaders.Authorization)?.let { bearerPattern.matchEntire(it)?.groupValues?.get(1) }
    val principal = token?.let(context.authorization::authenticateBearer)
    if (principal == null) respondApiError(HttpStatusCode.Unauthorized, "UNAUTHENTICATED", "A valid bearer session is required")
    return principal
}

/** Returns null after 401/403. Seller scope and delegated capability are read live from DB. */
suspend fun ApplicationCall.requireSellerCapability(context: BackendContext, sellerId: String,
                                                     capability: String): AuthenticatedPrincipal? {
    val principal = requireAuthenticatedUser(context) ?: return null
    if (!context.authorization.hasSellerCapability(principal, sellerId, capability)) {
        respondApiError(HttpStatusCode.Forbidden, "SELLER_CAPABILITY_REQUIRED", "This seller action is not permitted")
        return null
    }
    return principal
}

/** Platform administrator is separate from every seller membership. */
suspend fun ApplicationCall.requirePlatformAdmin(context: BackendContext): AuthenticatedPrincipal? {
    val principal = requireAuthenticatedUser(context) ?: return null
    if (!context.authorization.isPlatformAdmin(principal)) {
        respondApiError(HttpStatusCode.Forbidden, "PLATFORM_ADMIN_REQUIRED", "Platform administrator access is required")
        return null
    }
    return principal
}
