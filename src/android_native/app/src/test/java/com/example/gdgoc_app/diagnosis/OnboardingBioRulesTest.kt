package com.example.gdgoc_app.diagnosis

import com.example.gdgoc_app.data.diagnosis.OnboardingBioRules
import org.junit.Assert.*
import org.junit.Test

class OnboardingBioRulesTest {
    @Test fun ordinaryBioAndInternalWhitespaceArePreserved() {
        val bio = "自己紹介\n2行目\tの内容"
        assertEquals(bio, OnboardingBioRules.normalize(bio))
        assertNull(OnboardingBioRules.error(bio))
    }

    @Test fun stripsExactlyTheWhitespaceRecognizedByPython() {
        val whitespace = ((0x09..0x0D) + (0x1C..0x20) + listOf(0x85, 0xA0, 0x1680) +
            (0x2000..0x200A) + listOf(0x2028, 0x2029, 0x202F, 0x205F, 0x3000))
        whitespace.forEach { code ->
            val edge = code.toChar().toString()
            assertEquals("U+${code.toString(16)}", "自己紹介", OnboardingBioRules.normalize(edge + "自己紹介" + edge))
        }
        for (edge in listOf("\u200B", "\uFEFF", "\u0000")) {
            assertEquals(edge + "紹介" + edge, OnboardingBioRules.normalize(edge + "紹介" + edge))
        }
    }

    @Test fun emptyAndWhitespaceOnlyBioAreOptionalEvenWhenRawInputIsLong() {
        for (bio in listOf("", " \t\r\n　\u0085\u00A0", " ".repeat(121))) {
            assertEquals("", OnboardingBioRules.normalize(bio))
            assertNull(OnboardingBioRules.error(bio))
        }
    }

    @Test fun boundaryUsesNormalizedUnicodeCharacterCount() {
        for (character in listOf("あ", "😀")) {
            assertNull(OnboardingBioRules.error("　 " + character.repeat(120) + " \n"))
            assertNotNull(OnboardingBioRules.error("　 " + character.repeat(121) + " \n"))
        }
    }
}
