package io.qalipsis.core.head.security.auth0

import assertk.all
import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import io.micronaut.context.ApplicationContext
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

internal class Auth0ConfigTest {

    @Nested
    @MicronautTest(propertySources = ["classpath:application.yml"])
    inner class WithAuth0 {

        @Inject
        private lateinit var applicationContext: ApplicationContext

        @Test
        @Timeout(10)
        fun `should start with the auth0 identity manager`() {
            assertThat(applicationContext.getBeansOfType(Auth0IdentityManagement::class.java)).all {
                any { it.isInstanceOf(Auth0IdentityManagement::class) }
            }
            val auth0Config =
                (applicationContext.getBeansOfType(Auth0IdentityManagement::class.java) as ArrayList).get(0).auth0Configuration
            assertThat(auth0Config.connection).isEqualTo("qalipsis")
            assertThat(auth0Config.baseAddress).isEqualTo("https://dev-d7xe49-1.us.auth0.com/api/v2/users")
            assertThat(auth0Config.clientId).isEqualTo("QddokR3NNMzECAu2Ww5yVLzNcqTuWIWV")
            assertThat(auth0Config.clientSecret).isEqualTo("yk4sV_HULJdYCQ4wUvmhP6sTD9ocNXMCeNKhfetEJQYTep_MeXFQrvpfHxCCPYh4")
            assertThat(auth0Config.apiIdentifier).isEqualTo("https://dev-d7xe49-1.us.auth0.com/api/v2/")
        }
    }
}