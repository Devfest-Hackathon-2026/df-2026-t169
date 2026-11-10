package com.example.gdgoc_app

import com.example.gdgoc_app.ui.screen.login.credentialPasswordError
import org.junit.Assert.*
import org.junit.Test

class RegistrationPasswordValidationTest {
    @Test fun registrationRejectsSevenCharactersEvenWhenMatching() {
        assertNotNull(credentialPasswordError("1234567", "1234567", true))
    }
    @Test fun registrationAcceptsEightMatchingCharacters() {
        assertNull(credentialPasswordError("12345678", "12345678", true))
    }
    @Test fun registrationRejectsMismatch() {
        assertEquals("確認用パスワードが一致しません。", credentialPasswordError("12345678", "abcdefgh", true))
    }
    @Test fun loginAcceptsLegacyShortPasswordWithoutConfirmation() {
        assertNull(credentialPasswordError("123456", "", false))
    }
    @Test fun emptyPasswordIsRejectedForBothModes() {
        assertNotNull(credentialPasswordError("", "", false))
        assertNotNull(credentialPasswordError("", "", true))
    }
}
