package edu.msrit.canteen.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AuthEntryIntentTest {
    @Test fun welcomeActionsKeepDistinctIntentButShareEmailOtpBoundary() {
        assertNotEquals(AuthEntryIntent.GetStarted.routeValue, AuthEntryIntent.Login.routeValue)
        assertEquals("Register", AuthEntryIntent.fromRoute("get-started").heading)
        assertEquals("Welcome back", AuthEntryIntent.fromRoute("login").heading)
        assertEquals(AuthEntryIntent.Login, AuthEntryIntent.fromRoute("unexpected"))
    }
}
