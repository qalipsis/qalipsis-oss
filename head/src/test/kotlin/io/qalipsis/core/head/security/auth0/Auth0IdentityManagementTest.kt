package io.qalipsis.core.head.security.auth0

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.auth0.exception.Auth0Exception
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.qalipsis.core.head.security.entity.UserIdentity
import io.qalipsis.test.coroutines.TestDispatcherProvider
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.random.Random

/**
 * @author pbril
 */
@MicronautTest
internal class Auth0IdentityManagementTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    private lateinit var identityManagement: Auth0IdentityManagement

    private val userPrototype = createUser()


    @BeforeAll
    fun setUp() {
        identityManagement = Auth0IdentityManagement(object : Auth0Configuration {
            override val clientId: String = "PkAlTrtbUB3bh0qpcdkW6GkgrwQrTPNW"
            override val clientSecret: String = "ctdLwYjjE2fjvYV4HJDvCIUQPbQX7-zJn_TZihHDeA_6TsZW6V8yMEXN8Q6s5xXJ"
            override val apiIdentifier: String = "https://qalipsis-dev.eu.auth0.com/api/v2/"
            override val domain: String = "qalipsis-dev.eu.auth0.com"
        })
    }

    @Test
    fun `should save and get user from auth0`() = testDispatcherProvider.run {
        // when
        val result = identityManagement.save(createUser())

        //  then
        assertThat(result.user_id).isNotNull()

        val result2 = identityManagement.get(result.user_id!!)
        assertThat(result2.username).isEqualTo(result.username)
        assertThat(result2.email).isEqualTo(result.email)
        assertThat(result2.name).isEqualTo(result.name)

        identityManagement.delete(result.user_id!!)
    }

    @Test
    fun `should update user from auth0`() = testDispatcherProvider.run {
        // when
        val result = identityManagement.save(createUser())
        userPrototype.email = result.email
        userPrototype.username = "new-qalipsis-1"
        identityManagement.update(result.user_id!!, userPrototype)

        //  then
        val result2 = identityManagement.get(result.user_id!!)
        assertThat(result2.username).isEqualTo("new-qalipsis-1")

        identityManagement.delete(result.user_id!!)
    }

    @Test
    fun `should delete user from auth0`() = testDispatcherProvider.run {
        // when
        val result = identityManagement.save(createUser())
        identityManagement.delete(result.user_id!!)

        //  then
        assertThrows<Auth0Exception> {
            identityManagement.get(result.user_id!!)
        }
    }

    private fun createUser(): UserIdentity {
        return UserIdentity(
            username = "auth-user${(Random.nextInt(-9999, 99999))}",
            name = "test",
            email = "foo+${(Random.nextInt(10, 36000))}@bar.com"
        )
    }
}