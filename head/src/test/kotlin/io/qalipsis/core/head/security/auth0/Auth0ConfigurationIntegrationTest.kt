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
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.qalipsis.core.head.security.IdentityManagement
import io.qalipsis.core.head.security.UserManagement
import io.qalipsis.core.head.security.UserManagementImpl
import io.qalipsis.core.head.security.auth0.Auth0Configuration.Auth0ManagementConfiguration
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
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

    @WithMockk
    @Nested
    @MicronautTest(environments = ["auth0"])
    inner class WithDefaultAuth0 {

        @Inject
        private lateinit var applicationContext: ApplicationContext

        @MockBean(bean = Auth0Operations::class)
        fun auth0Operations(): Auth0Operations = relaxedMockk()

        @Test
        @Timeout(10)
        fun `should start with the auth0 identity manager`() {
            assertThat(applicationContext.getBean(IdentityManagement::class.java)).isInstanceOf(Auth0IdentityManagement::class)
            assertThat(applicationContext.getBean(UserManagement::class.java)).isInstanceOf(UserManagementImpl::class)
            val auth0Config = applicationContext.getBean(Auth0Configuration::class.java)
            assertThat(auth0Config).all {
                prop(Auth0Configuration::domain).isEqualTo("my-domain.auth0.com")
                prop(Auth0Configuration::management).all {
                    prop(Auth0ManagementConfiguration::connection).isEqualTo("Username-Password-Authentication")
                    prop(Auth0ManagementConfiguration::clientId).isEqualTo("-")
                    prop(Auth0ManagementConfiguration::clientSecret).isEqualTo("-")
                    prop(Auth0ManagementConfiguration::apiUrl).isEqualTo("https://my-domain.auth0.com/api/v2/")
                }
            }
        }
    }

    @Nested
    @MicronautTest(environments = ["auth0"], propertySources = ["classpath:application-auth0-test.yml"])
    @PropertySource(
        Property(name = "identity.auth0.management.client-id", value = "the-client"),
        Property(name = "identity.auth0.management.client-secret", value = "the-secret"),
        Property(name = "identity.auth0.management.api-url", value = "https://the-url"),
        Property(name = "identity.auth0.domain", value = "the.domain"),
        Property(name = "identity.auth0.management.connection", value = "Other-connection"),
    )
    inner class WithConfiguredAuth0 {

        @Inject
        private lateinit var applicationContext: ApplicationContext

        @MockBean(bean = Auth0Operations::class)
        fun auth0Operations(): Auth0Operations = relaxedMockk()

        @Test
        @Timeout(10)
        fun `should start with the auth0 identity manager`() {
            assertThat(applicationContext.getBean(IdentityManagement::class.java)).isInstanceOf(Auth0IdentityManagement::class)
            assertThat(applicationContext.getBean(UserManagement::class.java)).isInstanceOf(UserManagementImpl::class)
            val auth0Config = applicationContext.getBean(Auth0Configuration::class.java)
            assertThat(auth0Config).all {
                prop(Auth0Configuration::domain).isEqualTo("the.domain")
                prop(Auth0Configuration::management).all {
                    prop(Auth0ManagementConfiguration::clientId).isEqualTo("the-client")
                    prop(Auth0ManagementConfiguration::clientSecret).isEqualTo("the-secret")
                    prop(Auth0ManagementConfiguration::connection).isEqualTo("Other-connection")
                    prop(Auth0ManagementConfiguration::apiUrl).isEqualTo("https://the-url")
                }
            }
        }
    }
}