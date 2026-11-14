package edu.msrit.canteen.backend.features.engagement

import edu.msrit.canteen.backend.core.BackendContext
import edu.msrit.canteen.backend.features.platform.registerPlatformRoutes
import io.ktor.server.routing.Route

/**
 * Frozen public seam. The lead mounts it inside `route("/api/v1")`. Without a Commerce adapter, item
 * favorites fail closed (503) and analytics report that no request-count source is connected.
 */
fun Route.registerEngagementRoutes(context: BackendContext) =
    registerEngagementRoutes(context, UnavailableCommerceReadPort)

/** Integration overload: pass the Commerce adapter once Backend 2 is merged. */
fun Route.registerEngagementRoutes(context: BackendContext, commerce: CommerceReadPort) {
    registerCustomerDataRoutes(context, commerce)
    registerSupportRoutes(context)
    registerAnalyticsRoutes(context, commerce)
    registerPlatformRoutes(context, commerce)
}
