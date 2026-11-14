package edu.msrit.canteen.backend.core

import edu.msrit.canteen.backend.auth.AuthorizationService
import java.sql.Connection
import java.time.Clock
import javax.sql.DataSource

/** Shared, server-owned infrastructure passed to route modules. */
data class BackendContext(
    val dataSource: DataSource,
    val config: BackendConfig,
    val clock: Clock = Clock.systemUTC(),
) {
    val authorization: AuthorizationService = AuthorizationService(dataSource, clock)
}

/** Use one DB transaction for authorization and the write it guards. */
fun <T> BackendContext.transaction(block: (Connection) -> T): T = dataSource.connection.use { connection ->
    connection.autoCommit = false
    try {
        val result = block(connection)
        connection.commit()
        result
    } catch (failure: Throwable) {
        connection.rollback()
        throw failure
    }
}
