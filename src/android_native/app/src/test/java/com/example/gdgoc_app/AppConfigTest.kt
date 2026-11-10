package com.example.gdgoc_app

import com.example.gdgoc_app.config.AppConfig
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppConfigTest {

    @Test
    fun defaultMockConfigIsValid() {
        val config = AppConfig(apiBaseUrl = "", useMockData = true)
        assertNull(config.validate())
    }

    @Test
    fun validApiConfigIsValid() {
        val config = AppConfig(
            apiBaseUrl = "https://my-cloud-run-service.a.run.app",
            useMockData = false
        )
        assertNull(config.validate())
    }

    @Test
    fun emptyUrlInApiModeReturnsError() {
        val config = AppConfig(apiBaseUrl = "", useMockData = false)
        val error = config.validate()
        assertNotNull(error)
        assertTrue(error!!.contains("Cloud Run"))
    }

    @Test
    fun invalidSchemeReturnsError() {
        val config = AppConfig(apiBaseUrl = "ftp://invalid-url.com", useMockData = false)
        val error = config.validate()
        assertNotNull(error)
        assertTrue(error!!.contains("http:// または https://"))
    }
}
