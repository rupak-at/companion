package com.ambientcompanion.download.network

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadSubmissionClientTest {
    @Test
    fun `shows Supabase msg and error code`() {
        assertEquals(
            "Anonymous sign-ins are disabled (anonymous_provider_disabled)",
            parseApiError(
                """{"code":422,"error_code":"anonymous_provider_disabled","msg":"Anonymous sign-ins are disabled"}""",
                422,
            ),
        )
    }

    @Test
    fun `falls back to HTTP status for a non-JSON response`() {
        assertEquals("Request failed (HTTP 502).", parseApiError("Bad gateway", 502))
    }
}
