package io.qalipsis.core.head.security.auth0

import assertk.assertThat
import assertk.assertions.isFalse
import com.auth0.json.mgmt.Role
import com.auth0.json.mgmt.users.User
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.qalipsis.core.head.security.RoleName
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@WithMockk
internal class RemoveRoleAuth0PatchTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var auth0Operations: Auth0Operations

    @Test
    internal fun `should unassign the assigned roles only`() = testDispatcherProvider.runTest {
        // given
        val user = mockk<User> { every { id } returns "the-user" }
        val patch = RemoveRoleAuth0Patch(
            "my-tenant",
            listOf(RoleName.TESTER, RoleName.REPORTER, RoleName.BILLING_ADMINISTRATOR),
            auth0Operations
        )
        coEvery {
            auth0Operations.getUserRolesInTenant("the-user", "my-tenant")
        } returns listOf(Role().apply { name = "my-tenant:tester" }, Role().apply { name = "my-tenant:reporter" })
        coEvery {
            auth0Operations.listRolesIds(
                tenant = any(),
                roles = any(),
                createMissingRoles = any()
            )
        } returns listOf("1", "2")

        // when
        val result = patch.apply(user)

        // then
        coVerifyOrder {
            user.id
            auth0Operations.getUserRolesInTenant("the-user", "my-tenant")
            auth0Operations.validateAdministrationRolesRemoval("my-tenant", setOf(RoleName.TESTER, RoleName.REPORTER))
            auth0Operations.listRolesIds("my-tenant", setOf(RoleName.TESTER, RoleName.REPORTER), false)
            user.id
            auth0Operations.unassignRoles("the-user", listOf("1", "2"))
        }
        assertThat(result).isFalse()
        confirmVerified(user, auth0Operations)
    }

}