package io.qalipsis.core.head.security

import assertk.all
import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.core.head.jdbc.entity.UserEntity
import io.qalipsis.core.head.jdbc.repository.UserRepository
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant

/**
 * @author pbril
 */
@WithMockk
internal class UserManagementImplTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var identityManagement: IdentityManagement

    @RelaxedMockK
    private lateinit var userRepository: UserRepository

    @InjectMockKs
    private lateinit var userManagement: UserManagementImpl

    @Test
    fun `should retrieve a user with identity`() = testDispatcherProvider.run {
        // given
        val creationInstant = Instant.now()
        val versionInstant = Instant.now()
        val existingUser = relaxedMockk<UserEntity> {
            every { creation } returns creationInstant
            every { version } returns versionInstant
            every { identityId } returns "the identity"
        }
        coEvery { userRepository.findByUsername("the username") } returns existingUser
        val existingIdentity = relaxedMockk<UserIdentity> {
            every { emailVerified } returns true
            every { username } returns "the username"
            every { email } returns "email"
            every { displayName } returns "the display name"
            every { emailVerified } returns true
            every { blocked } returns false
            every { roles } returns mutableSetOf(RoleName.TESTER, RoleName.REPORTER)
        }
        coEvery { identityManagement.get("my-tenant", "the identity") } returns existingIdentity

        // when
        val user = userManagement.get("my-tenant", "the username")

        //  then
        assertThat(user).isNotNull().all {
            prop(User::tenant).isEqualTo("my-tenant")
            prop(User::creation).isEqualTo(creationInstant)
            prop(User::version).isEqualTo(versionInstant)
            prop(User::username).isEqualTo("the username")
            prop(User::displayName).isEqualTo("the display name")
            prop(User::email).isEqualTo("email")
            prop(User::emailVerified).isTrue()
            prop(User::blocked).isFalse()
            prop(User::roles).containsOnly(RoleName.TESTER, RoleName.REPORTER)
        }
        coVerifyOrder {
            userRepository.findByUsername("the username")
            identityManagement.get("my-tenant", "the identity")
        }
        confirmVerified(userRepository, identityManagement)
    }

    @Test
    fun `should not retrieve a user without identity`() = testDispatcherProvider.run {
        // given
        val existingUser = relaxedMockk<UserEntity> {
            every { identityId } returns null
        }
        coEvery { userRepository.findByUsername(any()) } returns existingUser

        // when
        val user = userManagement.get("my-tenant", "the username")

        //  then
        assertThat(user).isNull()
        coVerifyOrder {
            userRepository.findByUsername("the username")
        }
        confirmVerified(userRepository, identityManagement)
    }

    @Test
    internal fun `should create a new user`() = testDispatcherProvider.run {
        // given
        val user = relaxedMockk<User> {
            every { username } returns "the username"
            every { email } returns "email"
            every { displayName } returns "the display name"
            every { emailVerified } returns false
            every { roles } returns setOf(RoleName.TESTER, RoleName.REPORTER)
        }
        val creationInstant = Instant.now()
        val versionInstant = Instant.now()
        val createdUserEntity = relaxedMockk<UserEntity> {
            every { creation } returns creationInstant
            every { version } returns versionInstant
            every { identityId } returns "the identity"
        }
        coEvery { userRepository.save(any()) } returns createdUserEntity
        coEvery { identityManagement.create(any(), any(), any()) } returnsArgument 2

        // when
        val createdUser = userManagement.create("my-tenant", user)

        // then
        assertThat(createdUser).isNotNull().all {
            prop(User::tenant).isEqualTo("my-tenant")
            prop(User::creation).isEqualTo(creationInstant)
            prop(User::version).isEqualTo(versionInstant)
            prop(User::username).isEqualTo("the username")
            prop(User::displayName).isEqualTo("the display name")
            prop(User::email).isEqualTo("email")
            prop(User::emailVerified).isFalse()
            prop(User::blocked).isFalse()
            prop(User::roles).containsOnly(RoleName.TESTER, RoleName.REPORTER)
        }
        coVerifyOrder {
            userRepository.save(withArg {
                assertThat(it).all {
                    prop(UserEntity::username).isEqualTo("the username")
                    prop(UserEntity::displayName).isEqualTo("the display name")
                }
            })
            identityManagement.create("my-tenant", refEq(createdUserEntity), withArg {
                assertThat(it).isDataClassEqualTo(
                    UserIdentity(
                        username = "the username",
                        displayName = "the display name",
                        email = "email",
                        emailVerified = false,
                        roles = mutableSetOf(RoleName.TESTER, RoleName.REPORTER)
                    )
                )
            })
        }
        confirmVerified(userRepository, identityManagement)
    }

    @Test
    fun `should apply patches from but not update when none has effect`() = testDispatcherProvider.run {
        // given
        val creationInstant = Instant.now()
        val versionInstant = Instant.now()
        val existingUser = relaxedMockk<UserEntity> {
            every { creation } returns creationInstant
            every { version } returns versionInstant
            every { identityId } returns "the identity"
        }
        coEvery { userRepository.findByUsername("the username") } returns existingUser
        val updatedIdentity = relaxedMockk<UserIdentity> {
            every { emailVerified } returns true
            every { username } returns "the username"
            every { email } returns "email"
            every { displayName } returns "the display name"
            every { emailVerified } returns true
            every { blocked } returns false
            every { roles } returns mutableSetOf(RoleName.TESTER, RoleName.REPORTER)
        }
        coEvery { identityManagement.update("my-tenant", any(), any()) } returns updatedIdentity
        val userPatch1 = relaxedMockk<UserPatch>()
        coEvery { userPatch1.apply(any()) } returns false
        val userPatch2 = relaxedMockk<UserPatch>()
        coEvery { userPatch1.apply(any()) } returns false

        // when
        val updatedUser = userManagement.update(
            tenant = "my-tenant",
            user = relaxedMockk { every { username } returns "the username" },
            userPatches = listOf(userPatch1, userPatch2)
        )

        // then
        assertThat(updatedUser).isNotNull().all {
            prop(User::tenant).isEqualTo("my-tenant")
            prop(User::creation).isEqualTo(creationInstant)
            prop(User::version).isEqualTo(versionInstant)
            prop(User::username).isEqualTo("the username")
            prop(User::displayName).isEqualTo("the display name")
            prop(User::email).isEqualTo("email")
            prop(User::emailVerified).isTrue()
            prop(User::blocked).isFalse()
            prop(User::roles).containsOnly(RoleName.TESTER, RoleName.REPORTER)
        }
        coVerifyOrder {
            userRepository.findByUsername("the username")
            userPatch1.apply(refEq(existingUser))
            userPatch2.apply(refEq(existingUser))
            identityManagement.update("my-tenant", refEq(existingUser), listOf(userPatch1, userPatch2))
        }
        confirmVerified(userRepository, identityManagement)
    }

    @Test
    fun `should apply patches and update when one patch has effect`() = testDispatcherProvider.run {
        // given
        val creationInstant = Instant.now()
        val versionInstant = Instant.now()
        val existingUser = relaxedMockk<UserEntity> {
            every { creation } returns creationInstant
            every { version } returns versionInstant
            every { identityId } returns "the identity"
        }
        coEvery { userRepository.findByUsername("the username") } returns existingUser
        val versionInstantUpdated = Instant.now()
        val updatedUserEntity = relaxedMockk<UserEntity> {
            every { creation } returns creationInstant
            every { version } returns versionInstantUpdated
            every { identityId } returns "the identity"
        }
        coEvery { userRepository.update(any()) } returns updatedUserEntity
        val updatedIdentity = relaxedMockk<UserIdentity> {
            every { emailVerified } returns true
            every { username } returns "the username"
            every { email } returns "email"
            every { displayName } returns "the display name"
            every { emailVerified } returns true
            every { blocked } returns false
            every { roles } returns mutableSetOf(RoleName.TESTER, RoleName.REPORTER)
        }
        coEvery { identityManagement.update("my-tenant", any(), any()) } returns updatedIdentity
        val userPatch1 = relaxedMockk<UserPatch>()
        coEvery { userPatch1.apply(any()) } returns true
        val userPatch2 = relaxedMockk<UserPatch>()
        coEvery { userPatch2.apply(any()) } returns false

        // when
        val updatedUser = userManagement.update(
            tenant = "my-tenant",
            user = relaxedMockk { every { username } returns "the username" },
            userPatches = listOf(userPatch1, userPatch2)
        )

        // then
        assertThat(updatedUser).isNotNull().all {
            prop(User::tenant).isEqualTo("my-tenant")
            prop(User::creation).isEqualTo(creationInstant)
            prop(User::version).isEqualTo(versionInstantUpdated)
            prop(User::username).isEqualTo("the username")
            prop(User::displayName).isEqualTo("the display name")
            prop(User::email).isEqualTo("email")
            prop(User::emailVerified).isTrue()
            prop(User::blocked).isFalse()
            prop(User::roles).containsOnly(RoleName.TESTER, RoleName.REPORTER)
        }
        coVerifyOrder {
            userRepository.findByUsername("the username")
            userPatch1.apply(refEq(existingUser))
            userPatch2.apply(refEq(existingUser))
            identityManagement.update("my-tenant", refEq(existingUser), listOf(userPatch1, userPatch2))
            userRepository.update(refEq(existingUser))
        }
        confirmVerified(userRepository, identityManagement)
    }

    @Test
    internal fun `should delete the user`() = testDispatcherProvider.run {
        // given
        val existingUser = relaxedMockk<UserEntity> {
            every { identityId } returns "the identity"
        }
        coEvery { userRepository.findByUsername("the username") } returns existingUser

        // when
        userManagement.delete("my-tenant", "the username")

        // then
        coVerifyOrder {
            userRepository.findByUsername("the username")
            identityManagement.delete("my-tenant", refEq(existingUser))
        }
        confirmVerified(userRepository, identityManagement)
    }
}