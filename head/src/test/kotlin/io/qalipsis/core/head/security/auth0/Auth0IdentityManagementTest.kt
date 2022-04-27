package io.qalipsis.core.head.security.auth0

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.auth0.exception.Auth0Exception
import com.auth0.json.mgmt.users.User
import io.micronaut.http.client.exceptions.EmptyResponseException
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
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
        assertThat(result.id).isNotNull()

        val result2 = identityManagement.get(result.id!!)
        assertThat(result2.username).isEqualTo(result.username)
        assertThat(result2.email).isEqualTo(result.email)
        assertThat(result2.name).isEqualTo(result.name)

        identityManagement.delete(result.id!!)
    }

    @Test
    fun `should update user from auth0`() = testDispatcherProvider.run {
        // when
        val result = identityManagement.save(createUser())
        userPrototype.email = result.email
        userPrototype.username = "new-qalipsis-1"
        identityManagement.update(result.id!!, userPrototype)

        //  then
        val result2 = identityManagement.get(result.id!!)
        assertThat(result2.username).isEqualTo("new-qalipsis-1")

        identityManagement.delete(result.id!!)
    }

    @Test
    fun `should delete user from auth0`() = testDispatcherProvider.run {
        // when
        val result = identityManagement.save(createUser())
        identityManagement.delete(result.id!!)

        //  then
        assertThrows<Auth0Exception> {
            identityManagement.get(result.id!!)
        }
    }

    private fun createUser(): User {
        val user = User()
        user.username = "auth-user${(Random.nextInt(-9999, 99999))}"
        user.name = "test"
        user.email = "foo+${(Random.nextInt(10, 36000))}@bar.com"
        user.setConnection("Username-Password-Authentication")
        user.setVerifyEmail(true)
        user.isEmailVerified = false
        user.setPassword("pass".toCharArray())
        return user
    }
}