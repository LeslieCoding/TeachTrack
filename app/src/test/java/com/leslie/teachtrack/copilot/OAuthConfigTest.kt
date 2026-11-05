package com.leslie.teachtrack.copilot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OAuthConfigTest {
    @Test fun normalizedConfigForcesAppRedirectAndDefaultsScope() {
        val config = OAuthConfig(authorizationEndpoint = " https://login.example/authorize ", clientId = " mobile ", scopes = " ").normalized()
        assertEquals("https://login.example/authorize", config.authorizationEndpoint)
        assertEquals("mobile", config.clientId)
        assertEquals("openid profile", config.scopes)
        assertEquals(OAuthManager.REDIRECT_URI, config.redirectUri)
    }

    @Test fun redirectUriUsesAppOwnedScheme() {
        assertTrue(OAuthManager.REDIRECT_URI.startsWith("teachtrack://oauth/"))
    }
}
