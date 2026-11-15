package edu.msrit.canteen.backend.core

import java.sql.Connection

/** Lifecycle/grant changes serialize against guarded writes. Always acquire before row locks.
 * Shared locks allow ordinary seller transactions to run concurrently. Never upgrade shared to exclusive.
 */
internal fun Connection.managementLock(exclusive: Boolean = false) {
    if (autoCommit) return
    val function = if (exclusive) "pg_advisory_xact_lock" else "pg_advisory_xact_lock_shared"
    prepareStatement("SELECT $function(74821048)").use { it.execute() }
}
