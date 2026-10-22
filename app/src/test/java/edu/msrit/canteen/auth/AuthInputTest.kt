package edu.msrit.canteen.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthInputTest {
    @Test fun emailTrimmingAndValidation() {
        assertEquals("person@example.org", AuthInput.normalizeEmail("  person@example.org  "))
        assertTrue(AuthInput.isEmailValid("  person+tag@example.org  "))
        assertFalse(AuthInput.isEmailValid("personexample.org"))
        assertFalse(AuthInput.isEmailValid("a..b@example.org"))
        assertFalse(AuthInput.isEmailValid("a@-example.org"))
    }

    @Test fun sixDigitInputSupportsPasteAndBackspaceState() {
        assertEquals("123456", AuthInput.otpDigits("12 34-56 7"))
        assertTrue(AuthInput.isOtpValid("123456"))
        assertFalse(AuthInput.isOtpValid("12345"))
        assertEquals("12345", AuthInput.otpDigits("12345"))
        assertFalse(AuthInput.isOtpValid("١٢٣٤٥٦"))
    }

    @Test fun profileRequiresAReasonableFullName() {
        assertEquals("Ada Lovelace", AuthInput.normalizeName(" Ada   Lovelace "))
        assertTrue(AuthInput.isFullNameValid("Ada Lovelace"))
        assertFalse(AuthInput.isFullNameValid("Ada"))
        assertFalse(AuthInput.isFullNameValid("A 1"))
    }
}
