package edu.msrit.canteen.auth

enum class AuthEntryIntent(val routeValue: String, val heading: String, val helper: String) {
    GetStarted("get-started", "Register", "Enter your email to get started."),
    Login("login", "Welcome back", "Sign in with your email to continue.");

    companion object {
        fun fromRoute(value: String?): AuthEntryIntent = entries.firstOrNull { it.routeValue == value } ?: Login
    }
}
