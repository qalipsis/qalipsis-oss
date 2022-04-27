package io.qalipsis.core.head.security.auth0

import java.time.Instant

class Auth0Token(
    val accessToken: String,
    private val expiresIn: Long,
) {
    val expiresAt: Long = Instant.now().epochSecond + expiresIn
}