package io.qalipsis.core.head.model

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.inject.Singleton
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotEmpty

/**
 * Model to send the configuration information required for the frontend.
 *
 * @author Palina Bril
 */
@Introspected
@Schema(
    name = "SecurityConfiguration",
    title = "Details of the SecurityConfiguration necessary for the frontend"
)
@Singleton
internal interface SecurityConfiguration {
    @get:Schema(description = "The name of the strategy of security configuration")
    @get:NotEmpty
    val strategyName: String

    @get:Schema(description = "The URL to open when the user is not authenticated yet")
    val loginEndpoint: String

    @get:Schema(description = "The URL to open to invalidate the authenticated user")
    val logoutEndpoint: String
}

@Requirements(
    Requires(property = "micronaut.security.enabled", value = "true"),
    Requires(property = "micronaut.security.token.jwt.enabled", value = "true")
)
@ConfigurationProperties("micronaut.security.open-id")
@Singleton
@Introspected
internal class OpenIdSecurityConfiguration : SecurityConfiguration {
    @field:NotBlank
    override val strategyName: String = "openId"

    override val loginEndpoint: String = "https://qalipsis-dev.eu.auth0.com/v2/login"

    override val logoutEndpoint: String = "https://qalipsis-dev.eu.auth0.com/v2/logout"
}

@Requires(missingBeans = [OpenIdSecurityConfiguration::class])
@ConfigurationProperties("micronaut.security.disabled")
@Singleton
@Introspected
internal class DisabledSecurityConfiguration : SecurityConfiguration {
    @field:NotBlank
    override val strategyName: String = "disabled"

    override val loginEndpoint: String = ""

    override val logoutEndpoint: String = ""
}