package io.qalipsis.core.head.security.auth0

/**
 * Auth0token contains the details of the currently valid token to access to the Auth0 API.
 *
 * @property accessToken the token value to exchange to identify with Auth0
 * @param expiresIn delay of expiration in milliseconds
 */
internal class Auth0Token(
    val accessToken: String,
    val refreshToken: String?,
    expiresIn: Long,
) {

    private val expiresAt: Long = System.currentTimeMillis() + expiresIn

    val isValid = System.currentTimeMillis() < expiresAt
}