package io.qalipsis.core.head.security.auth0

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isNotEmpty
import io.micronaut.context.ApplicationContext
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

internal class Auth0IdentityManagerTest {

    @Nested
    @MicronautTest(startApplication = false)
    inner class WithoutAuth0 : TestPropertyProvider {

        @Inject
        private lateinit var applicationContext: ApplicationContext

        override fun getProperties(): MutableMap<String, String> {
            return mutableMapOf(
                "identity.manager" to "other",
            )
        }

        @Test
        @Timeout(10)
        internal fun `shouldn't start without auth0 enabled property`() {
            assertThat(applicationContext.getBeansOfType(Auth0IdentityManagement::class.java)).isEmpty()
        }
    }

    @Nested
    @MicronautTest(startApplication = false)
    inner class WithAuth0 : TestPropertyProvider {

        @Inject
        private lateinit var applicationContext: ApplicationContext

        override fun getProperties(): MutableMap<String, String> {
            return mutableMapOf(
                "identity.manager" to "auth0",
            )
        }

        @Test
        @Timeout(10)
        internal fun `should start with auth0 enabled property`() {
            assertThat(applicationContext.getBeansOfType(Auth0IdentityManagement::class.java)).isNotEmpty()
        }
    }
}