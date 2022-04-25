package io.qalipsis.core.head.security.auth0

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.auth0.exception.Auth0Exception
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.qalipsis.core.head.security.EmailAuth0Patch
import io.qalipsis.core.head.security.NameAuth0Patch
import io.qalipsis.core.head.security.RoleAuth0Patch
import io.qalipsis.core.head.security.entity.BillingAdminitratorRole
import io.qalipsis.core.head.security.entity.TesterRole
import io.qalipsis.core.head.security.entity.UserIdentity
import io.qalipsis.test.coroutines.TestDispatcherProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
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

    @BeforeEach
    fun avoidRequestLimitation() {
        runBlocking {
            delay(2000)
        }
    }

    @Test
    fun `should save and get user from auth0`() = testDispatcherProvider.run {
        // when
        val result = identityManagement.save("qalipsis", createUser())

        //  then
        assertThat(result.user_id).isNotNull()

        val result2 = identityManagement.get("qalipsis", result.user_id)
        assertThat(result2.username).isEqualTo(result.username)
        assertThat(result2.email).isEqualTo(result.email)
        assertThat(result2.name).isEqualTo(result.name)
        assertThat(result2.userRoles.get(0).name).isEqualTo("qalipsis:billing-admin")

        delay(1000)
        identityManagement.delete("qalipsis", result.user_id)
    }

    @Test
    fun `should update user from auth0`() = testDispatcherProvider.run {
        // when
        val result = identityManagement.save("qalipsis", createUser())
        userPrototype.email = "foo+${(Random.nextInt(10, 36000))}@bar.com"
        userPrototype.name = "new-qalipsis"
        userPrototype.userRoles = mutableListOf(TesterRole("qalipsis"))
        identityManagement.update(
            "qalipsis",
            result.user_id,
            userPrototype,
            listOf(RoleAuth0Patch(), NameAuth0Patch("new-qalipsis"), EmailAuth0Patch(userPrototype.email))
        )

        //  then
        val result2 = identityManagement.get("qalipsis", result.user_id)
        assertThat(result2.name).isEqualTo("new-qalipsis")
        assertThat(result2.email).isEqualTo(userPrototype.email)
        assertThat(result2.userRoles).hasSize(1)
        assertThat(result2.userRoles.get(0).name).isEqualTo("qalipsis:tester")

        delay(1500)
        identityManagement.delete("qalipsis", result.user_id)
    }

    @Test
    fun `should delete user from auth0`() = testDispatcherProvider.run {
        // when
        val result = identityManagement.save("qalipsis", createUser())
        identityManagement.delete("qalipsis", result.user_id)

        //  then
        assertThrows<Auth0Exception> {
            identityManagement.get("qalipsis", result.user_id)
        }
    }

    private fun createUser(): UserIdentity {
        return UserIdentity(
            username = "auth-user${(Random.nextInt(-9999, 99999))}",
            name = "test",
            email = "foo+${(Random.nextInt(10, 36000))}@bar.com",
            userRoles = mutableListOf(BillingAdminitratorRole("qalipsis"))
        )
    }
}