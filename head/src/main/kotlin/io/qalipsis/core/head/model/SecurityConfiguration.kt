package io.qalipsis.core.head.model

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.qalipsis.core.head.security.auth0.Auth0SecurityConfiguration
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.inject.Singleton
import javax.validation.constraints.NotBlank

/**
 * Model to send the configuration information required for the frontend.
 *
 * @author Palina Bril
 */
@Introspected
@Schema(
    name = "Security configuration",
    title = "Configuration of the security strategy to apply",
    allOf = [
        DisabledSecurityConfiguration::class,
        OAuth2SecurityConfiguration::class,
        Auth0SecurityConfiguration::class
    ]
)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "strategy",
    defaultImpl = DisabledSecurityConfiguration::class
)
@JsonSubTypes(
    JsonSubTypes.Type(value = DisabledSecurityConfiguration::class, name = "DISABLED"),
    JsonSubTypes.Type(value = Auth0SecurityConfiguration::class, name = "AUTH0"),
)
internal interface SecurityConfiguration {

    @get:Schema(description = "Name of the security strategy")
    val strategy: SecurityStrategy

}

@Introspected
internal enum class SecurityStrategy {
    DISABLED,
    AUTH0
}

@Schema(
    name = "Disabled security configuration",
    title = "Configuration of the security strategy to apply, when the security is disabled"
)
@Singleton
@Requires(missingBeans = [SecurityConfiguration::class])
@Introspected
internal class DisabledSecurityConfiguration : SecurityConfiguration {

    @get:Schema(description = "Name of the security strategy")
    override val strategy: SecurityStrategy = SecurityStrategy.DISABLED

}

@Schema(
    name = "Security configuration for OAuth2",
    title = "Configuration of the security strategy to apply, when the security is using Oauth2"
)
@Introspected
internal interface OAuth2SecurityConfiguration : SecurityConfiguration {

    @get:NotBlank
    @get:Schema(description = "URL to initialize a OAuth2 login, using the authorization code workflow")
    val authorizationUrl: String

    @get:NotBlank
    @get:Schema(description = "URL to initialize a OAuth2 logout, using the authorization code workflow")
    val revocationUrl: String

    @get:Schema(description = "Scopes to request in the identity token")
    val scopes: Collection<String>
}