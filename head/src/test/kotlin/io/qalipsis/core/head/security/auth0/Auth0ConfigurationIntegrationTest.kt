package io.qalipsis.core.head.security.auth0

import assertk.all
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.prop
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.PropertySource
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.qalipsis.core.head.security.IdentityManagement
import io.qalipsis.core.head.security.UserManagement
import io.qalipsis.core.head.security.UserManagementImpl
import jakarta.inject.Inject
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

internal class Auth0ConfigurationIntegrationTest {

    @Nested
    @MicronautTest
    inner class WithoutAuth0 {

        @Inject
        private lateinit var applicationContext: ApplicationContext

        @Test
        @Timeout(10)
        fun `should start without the auth0 identity manager`() {
            assertThat(applicationContext.getBeansOfType(IdentityManagement::class.java)).isEmpty()
            assertThat(applicationContext.getBeansOfType(UserManagement::class.java)).isEmpty()
        }
    }

    @Nested
    @MicronautTest(environments = ["auth0"])
    inner class WithDefaultAuth0 {

        @Inject
        private lateinit var applicationContext: ApplicationContext

        @Test
        @Timeout(10)
        fun `should start with the auth0 identity manager`() {
            assertThat(applicationContext.getBean(IdentityManagement::class.java)).isInstanceOf(Auth0IdentityManagement::class)
            assertThat(applicationContext.getBean(UserManagement::class.java)).isInstanceOf(UserManagementImpl::class)
            val auth0Config = applicationContext.getBean(Auth0Configuration::class.java)
            assertThat(auth0Config).all {
                prop(Auth0Configuration::clientId).isEqualTo("-")
                prop(Auth0Configuration::clientSecret).isEqualTo("-")
                prop(Auth0Configuration::apiUrl).isEqualTo("https://my-domain.auth0.com/api/v2/")
                prop(Auth0Configuration::domain).isEqualTo("my-domain.auth0.com")
                prop(Auth0Configuration::connection).isEqualTo("Username-Password-Authentication")
            }
        }
    }

    @Nested
    @MicronautTest(propertySources = ["classpath:application-auth0-test.yml"])
    @PropertySource(
        Property(name = "identity.auth0.client-id", value = "the-client"),
        Property(name = "identity.auth0.client-secret", value = "the-secret"),
        Property(name = "identity.auth0.api-url", value = "https://the-url"),
        Property(name = "identity.auth0.domain", value = "the.domain"),
        Property(name = "identity.auth0.connection", value = "Other-connection"),
    )
    inner class WithConfiguredAuth0 {

        @Inject
        private lateinit var applicationContext: ApplicationContext

        @Test
        @Timeout(10)
        fun `should start with the auth0 identity manager`() {
            assertThat(applicationContext.getBean(IdentityManagement::class.java)).isInstanceOf(Auth0IdentityManagement::class)
            assertThat(applicationContext.getBean(UserManagement::class.java)).isInstanceOf(UserManagementImpl::class)
            val auth0Config = applicationContext.getBean(Auth0Configuration::class.java)
            assertThat(auth0Config).all {
                prop(Auth0Configuration::clientId).isEqualTo("the-client")
                prop(Auth0Configuration::clientSecret).isEqualTo("the-secret")
                prop(Auth0Configuration::apiUrl).isEqualTo("https://the-url")
                prop(Auth0Configuration::domain).isEqualTo("the.domain")
                prop(Auth0Configuration::connection).isEqualTo("Other-connection")
            }
        }
    }
}