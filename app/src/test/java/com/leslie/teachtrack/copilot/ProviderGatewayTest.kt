package com.leslie.teachtrack.copilot

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderGatewayTest {
    @Test fun offlineGatewayNeverNeedsNetwork() = runBlocking {
        val result = OfflineProviderGateway().testConnection(ProviderConfig())
        assertTrue(result is ProviderResponse.Success)
        assertEquals("离线 Provider 可用；不会访问网络", (result as ProviderResponse.Success).candidate.explanation)
    }

    @Test fun unconfiguredCloudGatewayFailsSafely() = runBlocking {
        val result = UnconfiguredCloudProviderGateway().testConnection(ProviderConfig(enabled = true, vendor = "Cloud"))
        assertEquals("NOT_CONFIGURED", (result as ProviderResponse.Failure).code)
    }

    @Test fun providerConfigClampsTimeoutAtPersistenceBoundary() {
        assertEquals(5, ProviderConfig(timeoutSeconds = 1).normalized().timeoutSeconds)
        assertEquals(120, ProviderConfig(timeoutSeconds = 999).normalized().timeoutSeconds)
    }
}
