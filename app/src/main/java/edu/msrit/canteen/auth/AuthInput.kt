package edu.msrit.canteen.auth

object AuthInput {
    private val emailPattern = Regex("^[A-Z0-9._%+\\-]+@[A-Z0-9\\-]+(?:\\.[A-Z0-9\\-]+)+$", RegexOption.IGNORE_CASE)

    fun normalizeEmail(value: String): String = value.trim()

    fun isEmailValid(value: String): Boolean {
        val email = normalizeEmail(value)
        if (email.length > 254 || !emailPattern.matches(email)) return false
        val parts = email.split('@')
        return parts.size == 2 && parts[0].length <= 64 && !parts[0].startsWith('.') &&
            !parts[0].endsWith('.') && ".." !in parts[0] &&
            parts[1].split('.').all { it.isNotEmpty() && it.length <= 63 && !it.startsWith('-') && !it.endsWith('-') }
    }

    fun otpDigits(value: String): String = value.filter { it in '0'..'9' }.take(6)
    fun isOtpValid(value: String): Boolean = value.length == 6 && value.all { it in '0'..'9' }

    fun normalizeName(value: String): String = value.trim().replace(Regex("\\s+"), " ")
    fun isFullNameValid(value: String): Boolean {
        val name = normalizeName(value)
        return name.length in 3..100 && name.split(' ').size >= 2 && name.all { it.isLetter() || it.isWhitespace() || it in "'-" }
    }
}
