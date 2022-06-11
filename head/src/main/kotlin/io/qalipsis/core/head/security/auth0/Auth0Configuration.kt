package io.qalipsis.core.head.security.auth0

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Requires
import io.micronaut.core.bind.annotation.Bindable
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotEmpty

/**
 * Configuration for [Auth0IdentityManagement].
 *
 * @author Palina Bril
 */
@Requires(property = "identity.manager", value = "auth0")
@ConfigurationProperties("identity.auth0")
internal interface Auth0Configuration {

    /**
     * Tenant Auth0 domain. It is generally something like <tenant>.auth0.com or <tenant>.eu.auth0.com if you are
     * not using a custom domain.
     */
    @get:NotBlank
    val domain: String

    /**
     * Configuration of the management API.
     */
    @get:NotBlank
    val management: Auth0ManagementConfiguration

    /**
     * ID of the API representing the QALIPSIS backend in Auth0 if configured.
     *
     * This value can be blank to disable the configuration of the API in regard of QALIPSIS security settings.
     * This is required when machine-to-machine token are generated.
     */
    @get:Bindable(defaultValue = "")
    val apiId: String

    /**
     * Configuration for OAuth2.
     */
    @get:NotEmpty
    val oauth2: Auth0OAuth2Configuration

    @ConfigurationProperties("management")
    interface Auth0ManagementConfiguration {

        /**
         * Client ID of the machine-to-machine application allowed to access the management API.
         */
        @get:NotBlank
        val clientId: String

        /**
         * Client secret of the machine-to-machine application allowed to access the management API.
         */
        @get:NotBlank
        val clientSecret: String

        /**
         * URL of the management API.
         */
        @get:NotBlank
        val apiUrl: String

        /**
         * Authentication database connections to assign to the new users.
         */
        @get:NotBlank
        @get:Bindable(defaultValue = "Username-Password-Authentication")
        val connection: String

    }

    @ConfigurationProperties("oauth2")
    interface Auth0OAuth2Configuration {

        /**
         * Name of the scope containing the array of roles assigned to the user over all the tenants.
         */
        @get:NotBlank
        val roles: String

        /**
         * Name of the scope containing the array of tenants accessible by the user.
         */
        @get:NotBlank
        val tenants: String

        /**
         * Name of the client ID to request the authorize API.
         */
        @get:NotBlank
        val clientId: String

        /**
         * URL to initialize the login workflow, using the authorization code workflow.
         */
        @get:NotBlank
        val authorizationUrl: String

        /**
         * URL to initialize the logout workflow, using the authorization code workflow.
         */
        @get:NotBlank
        val revocationUrl: String
    }
}
