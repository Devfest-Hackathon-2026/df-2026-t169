package com.example.gdgoc_app.diagnosis

import com.example.gdgoc_app.ui.screen.diagnosis.diagnosisDisplayDate
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test

class DiagnosisDisplayDateTest {
    @Test fun serverTimestampUsesDeviceTimeZoneAndHandlesMicroseconds() {
        val original=TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            assertEquals("2026年11月11日",diagnosisDisplayDate("2026-11-10T20:13:49.643000+00:00"))
            assertEquals("2026年11月10日",diagnosisDisplayDate("2026-11-10T00:13:49Z"))
        } finally {TimeZone.setDefault(original)}
    }
    @Test fun unrecognizedHistoricalTimestampIsKept() {assertEquals("historical",diagnosisDisplayDate("historical"))}
}
