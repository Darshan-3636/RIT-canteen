package edu.msrit.canteen.auth

enum class AuthEntryIntent(val routeValue: String, val heading: String, val helper: String) {
    GetStarted("get-started", "Student and Faculty registration", "Enter your email to begin customer registration. We will verify it with a one-time code before profile setup."),
    Login("login", "Login", "Existing users sign in with their email. Your authorized roles will determine where you go after verification.");

    companion object {
        fun fromRoute(value: String?): AuthEntryIntent = entries.firstOrNull { it.routeValue == value } ?: Login
    }
}
