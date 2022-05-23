package io.qalipsis.core.head.security.auth0

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEmpty
import assertk.assertions.isTrue
import assertk.assertions.prop
import com.auth0.json.mgmt.Role
import com.auth0.json.mgmt.users.User
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.core.head.jdbc.entity.UserEntity
import io.qalipsis.core.head.jdbc.repository.UserRepository
import io.qalipsis.core.head.security.RoleName
import io.qalipsis.core.head.security.UserIdentity
import io.qalipsis.core.head.security.UserPatch
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant

@WithMockk
internal class Auth0IdentityManagementTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var patchConverter: Auth0PatchConverter

    @RelaxedMockK
    private lateinit var operations: Auth0Operations

    @RelaxedMockK
    private lateinit var userRepository: UserRepository

    @InjectMockKs
    private lateinit var identityManagement: Auth0IdentityManagement

    @Test
    fun `should create a new identity without role`() = testDispatcherProvider.run {
        // given
        val now = Instant.now()
        val user = relaxedMockk<UserEntity> {
            every { id } returns 617265L
            every { version } returns now
        }
        val identity = UserIdentity(
            username = "the user name",
            displayName = "the display name",
            email = "the email",
            roles = mutableSetOf()
        )
        coEvery { operations.createUser(any()) } returns relaxedMockk {
            every { id } returns "the-user-id"
        }
        coEvery { operations.listRolesIds("my-tenant", any(), true) } returns listOf("a")

        // when
        val result = identityManagement.create("my-tenant", user, identity)

        //  then
        assertThat(result).all {
            prop(UserIdentity::id).isEqualTo("the-user-id")
            prop(UserIdentity::username).isEqualTo("the user name")
            prop(UserIdentity::displayName).isEqualTo("the display name")
            prop(UserIdentity::email).isEqualTo("the email")
            prop(UserIdentity::emailVerified).isFalse()
            prop(UserIdentity::roles).isEmpty()
        }
        coVerifyOrder {
            operations.createUser(withArg {
                assertThat(it).all {
                    typedProp<String>("email").isEqualTo("the email")
                    typedProp<String>("username").isEqualTo("the user name")
                    typedProp<String>("name").isEqualTo("the display name")
                    typedProp<CharArray>("password").isNotEmpty()
                    typedProp<Boolean>("verifyEmail").isTrue()
                }
            })
            userRepository.updateIdentityId(617265L, refEq(now), "the-user-id")
            operations.listRolesIds("my-tenant", setOf(RoleName.USER), true)
            operations.assignRoles("the-user-id", listOf("a"))
        }
        confirmVerified(operations, userRepository)
    }

    @Test
    fun `should create a new identity with roles in the tenant`() = testDispatcherProvider.run {
        // given
        val now = Instant.now()
        val user = relaxedMockk<UserEntity> {
            every { id } returns 617265L
            every { version } returns now
        }
        val identity = UserIdentity(
            username = "the user name",
            displayName = "the display name",
            email = "the email",
            roles = mutableSetOf(RoleName.TESTER, RoleName.REPORTER)
        )
        coEvery { operations.createUser(any()) } returns relaxedMockk {
            every { id } returns "the-user-id"
        }
        coEvery { operations.listRolesIds("my-tenant", any(), true) } returns listOf("a", "b")

        // when
        val result = identityManagement.create("my-tenant", user, identity)

        //  then
        assertThat(result).all {
            prop(UserIdentity::id).isEqualTo("the-user-id")
            prop(UserIdentity::username).isEqualTo("the user name")
            prop(UserIdentity::displayName).isEqualTo("the display name")
            prop(UserIdentity::email).isEqualTo("the email")
            prop(UserIdentity::emailVerified).isFalse()
            prop(UserIdentity::roles).containsOnly(RoleName.TESTER, RoleName.REPORTER)
        }
        coVerifyOrder {
            operations.createUser(withArg {
                assertThat(it).all {
                    typedProp<String>("email").isEqualTo("the email")
                    typedProp<String>("username").isEqualTo("the user name")
                    typedProp<String>("name").isEqualTo("the display name")
                    typedProp<CharArray>("password").isNotEmpty()
                    typedProp<Boolean>("verifyEmail").isTrue()
                }
            })
            userRepository.updateIdentityId(617265L, refEq(now), "the-user-id")
            operations.listRolesIds("my-tenant", setOf(RoleName.TESTER, RoleName.REPORTER, RoleName.USER), true)
            operations.assignRoles("the-user-id", listOf("a", "b"))
        }
        confirmVerified(operations, userRepository)
    }

    @Test
    internal fun `should retrieve the identity from Auth0`() = testDispatcherProvider.run {
        // given
        val user = User().apply {
            username = "the user name"
            name = "the display name"
            email = "the email"
            isEmailVerified = true
            id = "the-user-id"
            isBlocked = true
        }
        coEvery { operations.getUser(any()) } returns user
        coEvery { operations.getUserRolesInTenant(any(), any()) } returns setOf(
            Role().apply { name = "my-tenant:tester" },
            Role().apply { name = "my-tenant:reporter" }
        )

        // when
        val identity = identityManagement.get("my-tenant", "the-user-id")

        // then
        assertThat(identity).all {
            prop(UserIdentity::id).isEqualTo("the-user-id")
            prop(UserIdentity::username).isEqualTo("the user name")
            prop(UserIdentity::displayName).isEqualTo("the display name")
            prop(UserIdentity::email).isEqualTo("the email")
            prop(UserIdentity::emailVerified).isTrue()
            prop(UserIdentity::blocked).isTrue()
            prop(UserIdentity::roles).containsOnly(RoleName.TESTER, RoleName.REPORTER)
        }
    }

    @Test
    internal fun `should not retrieve the identity from Auth0 when the user has no role in the tenant`() =
        testDispatcherProvider.run {
            // given
            val user = User().apply {
                username = "the user name"
                name = "the display name"
                email = "the email"
                isEmailVerified = true
                id = "the-user-id"
                isBlocked = true
            }
            coEvery { operations.getUser(any()) } returns user
            coEvery { operations.getUserRolesInTenant(any(), any()) } returns emptySet()

            // when
            assertThrows<IllegalArgumentException> {
                identityManagement.get("my-tenant", "the-user-id")
            }
        }

    @Test
    fun `should apply patches from auth0 but not update in Auth0 when none has effect`() = testDispatcherProvider.run {
        // given
        val userPatch1 = relaxedMockk<UserPatch>()
        val userPatch2 = relaxedMockk<UserPatch>()
        val auth0Patch1 = relaxedMockk<Auth0Patch>()
        coEvery { auth0Patch1.apply(any()) } returns false
        val auth0Patch2 = relaxedMockk<Auth0Patch>()
        coEvery { auth0Patch2.apply(any()) } returns false
        every { patchConverter.convert("my-tenant", listOf(userPatch1, userPatch2)) } returns listOf(
            auth0Patch1,
            auth0Patch2
        )
        val user = User().apply {
            username = "the user name"
            name = "the display name"
            email = "the email"
            isEmailVerified = true
            id = "the-user-id"
        }
        coEvery { operations.getUser(any()) } returns user
        coEvery { operations.getUserRolesInTenant(any(), any()) } returns setOf(
            Role().apply { name = "my-tenant:tester" },
            Role().apply { name = "my-tenant:reporter" }
        )
        val userEntity = relaxedMockk<UserEntity> {
            every { identityId } returns "the-user-id"
        }

        // when
        val identity = identityManagement.update("my-tenant", userEntity, listOf(userPatch1, userPatch2))

        // then
        coVerifyOrder {
            operations.getUser("the-user-id")
            auth0Patch1.apply(refEq(user))
            auth0Patch2.apply(refEq(user))
            operations.getUserRolesInTenant("the-user-id", "my-tenant")
        }
        assertThat(identity).all {
            prop(UserIdentity::id).isEqualTo("the-user-id")
            prop(UserIdentity::username).isEqualTo("the user name")
            prop(UserIdentity::displayName).isEqualTo("the display name")
            prop(UserIdentity::email).isEqualTo("the email")
            prop(UserIdentity::emailVerified).isTrue()
            prop(UserIdentity::roles).containsOnly(RoleName.TESTER, RoleName.REPORTER)
        }
        confirmVerified(operations)
    }

    @Test
    fun `should apply patches from auth0 and update in Auth0 when one patch has effect`() = testDispatcherProvider.run {
        // given
        val userPatch1 = relaxedMockk<UserPatch>()
        val userPatch2 = relaxedMockk<UserPatch>()
        val auth0Patch1 = relaxedMockk<Auth0Patch>()
        coEvery { auth0Patch1.apply(any()) } returns true
        val auth0Patch2 = relaxedMockk<Auth0Patch>()
        coEvery { auth0Patch2.apply(any()) } returns false

        every { patchConverter.convert("my-tenant", listOf(userPatch1, userPatch2)) } returns listOf(
            auth0Patch1,
            auth0Patch2
        )
        val user = User().apply {
            username = "the user name"
            name = "the display name"
            email = "the email"
            isEmailVerified = true
            id = "the-user-id"
        }
        coEvery { operations.getUser(any()) } returns user
        coEvery { operations.getUserRolesInTenant(any(), any()) } returns setOf(
            Role().apply { name = "my-tenant:tester" },
            Role().apply { name = "my-tenant:reporter" }
        )
        val userEntity = relaxedMockk<UserEntity> {
            every { identityId } returns "the-user-id"
        }

        // when
        val identity = identityManagement.update("my-tenant", userEntity, listOf(userPatch1, userPatch2))

        // then
        coVerifyOrder {
            operations.getUser("the-user-id")
            auth0Patch1.apply(refEq(user))
            auth0Patch2.apply(refEq(user))
            operations.updateUser(refEq(user))
            operations.getUserRolesInTenant("the-user-id", "my-tenant")
        }
        assertThat(identity).all {
            prop(UserIdentity::id).isEqualTo("the-user-id")
            prop(UserIdentity::username).isEqualTo("the user name")
            prop(UserIdentity::displayName).isEqualTo("the display name")
            prop(UserIdentity::email).isEqualTo("the email")
            prop(UserIdentity::emailVerified).isTrue()
            prop(UserIdentity::roles).containsOnly(RoleName.TESTER, RoleName.REPORTER)
        }
        confirmVerified(operations)
    }

    @Test
    internal fun `should cancel the deletion when the user is the latest administrator of a tenant`() =
        testDispatcherProvider.run {
            // given
            val now = Instant.now()
            val user = relaxedMockk<UserEntity> {
                every { version } returns now
                every { identityId } returns "the-user-id"
            }
            coEvery { operations.getUserRolesInTenant(any(), any()) } returns setOf(
                Role().apply { name = "my-tenant:billing-admin" }
            )
            coEvery { operations.validateAdministrationRolesRemoval(any(), any()) } throws IllegalArgumentException()

            // when
            assertThrows<IllegalArgumentException> {
                identityManagement.delete("my-tenant", user)
            }

            // then
            coVerifyOrder {
                operations.getUserRolesInTenant("the-user-id", "my-tenant")
                operations.validateAdministrationRolesRemoval("my-tenant", listOf(RoleName.BILLING_ADMINISTRATOR))
            }
            confirmVerified(operations)
        }

    @Test
    internal fun `should unassign the user from the tenant but not delete if having roles in other tenants`() =
        testDispatcherProvider.run {
            // given
            val now = Instant.now()
            val user = relaxedMockk<UserEntity> {
                every { version } returns now
                every { identityId } returns "the-user-id"
            }
            coEvery { operations.getUserRolesInTenant(any(), any()) } returns setOf(
                Role().apply { name = "my-tenant:tester" },
                Role().apply { name = "my-tenant:reporter" }
            )
            coEvery { operations.getAllUserRoles(any()) } returns setOf(Role().apply { name = "my-tenant2:tester" })

            // when
            identityManagement.delete("my-tenant", user)

            // then
            coVerifyOrder {
                operations.getUserRolesInTenant("the-user-id", "my-tenant")
                operations.validateAdministrationRolesRemoval("my-tenant", listOf(RoleName.TESTER, RoleName.REPORTER))
                operations.removeFromTenant("the-user-id", "my-tenant")
                operations.getAllUserRoles("the-user-id")
            }
            confirmVerified(operations)
        }

    @Test
    internal fun `should unassign the user from the tenant then delete when there is no other role in other tenant`() =
        testDispatcherProvider.run {
            // given
            val now = Instant.now()
            val user = relaxedMockk<UserEntity> {
                every { id } returns 617265L
                every { version } returns now
                every { identityId } returns "the-user-id"
            }
            coEvery { userRepository.updateIdentityId(any(), any(), any()) } returns 1
            coEvery { operations.getUserRolesInTenant(any(), any()) } returns setOf(
                Role().apply { name = "my-tenant:tester" },
                Role().apply { name = "my-tenant:reporter" }
            )
            coEvery { operations.getAllUserRoles(any()) } returns emptySet()

            // when
            identityManagement.delete("my-tenant", user)

            // then
            coVerifyOrder {
                operations.getUserRolesInTenant("the-user-id", "my-tenant")
                operations.validateAdministrationRolesRemoval("my-tenant", listOf(RoleName.TESTER, RoleName.REPORTER))
                operations.removeFromTenant("the-user-id", "my-tenant")
                operations.getAllUserRoles("the-user-id")
                operations.deleteUser("the-user-id")
                userRepository.updateIdentityId(617265L, now, null)
            }
            confirmVerified(operations)
        }

    @Test
    internal fun `should not unassign the identity from Auth0 when the user has no role in the tenant`() =
        testDispatcherProvider.run {
            // given
            val user = relaxedMockk<UserEntity> {
                every { identityId } returns "the-user-id"
            }
            coEvery { operations.getUserRolesInTenant(any(), any()) } returns emptySet()

            // when
            assertThrows<IllegalArgumentException> {
                identityManagement.delete("my-tenant", user)
            }

            coVerifyOrder {
                operations.getUserRolesInTenant("the-user-id", "my-tenant")
            }
            confirmVerified(operations)
        }

    @Test
    internal fun `should list all the users of the tenant`() = testDispatcherProvider.run {
        // given
        val identity1 = relaxedMockk<User> {
            every { id } returns "the identity 1"
            every { isEmailVerified } returns true
            every { username } returns "the username 1"
            every { email } returns "email 1"
            every { name } returns "the display name 1"
            every { isBlocked } returns false
        }
        val identity2 = relaxedMockk<User> {
            every { id } returns "the identity 2"
            every { isEmailVerified } returns false
            every { username } returns "the username 2"
            every { email } returns "email 2"
            every { name } returns "the display name 2"
            every { isBlocked } returns true
        }
        coEvery { operations.listUsersWithRoleInTenant(any(), any()) } returns listOf(identity1, identity2)
        coEvery { operations.getUserRolesInTenant(any(), any()) } returns setOf(
            Role().apply { name = "my-tenant:tester" },
            Role().apply { name = "my-tenant:reporter" }
        ) andThen setOf(Role().apply { name = "my-tenant:tenant-admin" })

        // when
        val identities = identityManagement.listUsers("my-tenant")

        // then
        assertThat(identities).all {
            hasSize(2)
            index(0).all {
                prop(UserIdentity::username).isEqualTo("the username 1")
                prop(UserIdentity::displayName).isEqualTo("the display name 1")
                prop(UserIdentity::email).isEqualTo("email 1")
                prop(UserIdentity::emailVerified).isTrue()
                prop(UserIdentity::blocked).isFalse()
                prop(UserIdentity::roles).containsOnly(RoleName.TESTER, RoleName.REPORTER)
            }
            index(1).all {
                prop(UserIdentity::username).isEqualTo("the username 2")
                prop(UserIdentity::displayName).isEqualTo("the display name 2")
                prop(UserIdentity::email).isEqualTo("email 2")
                prop(UserIdentity::emailVerified).isFalse()
                prop(UserIdentity::blocked).isTrue()
                prop(UserIdentity::roles).containsOnly(RoleName.TENANT_ADMINISTRATOR)
            }
        }
    }
}