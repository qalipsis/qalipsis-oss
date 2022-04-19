package io.qalipsis.core.head.security.impl

import io.aerisconsulting.catadioptre.coInvokeInvisible
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.core.head.jdbc.entity.UserEntity
import io.qalipsis.core.head.jdbc.repository.UserRepository
import io.qalipsis.core.head.security.AddRoleUserPatch
import io.qalipsis.core.head.security.Auth0Patch
import io.qalipsis.core.head.security.IdentityManagement
import io.qalipsis.core.head.security.UsernameUserPatch
import io.qalipsis.core.head.security.entity.QalipsisUser
import io.qalipsis.core.head.security.entity.RoleName
import io.qalipsis.core.head.security.entity.UserIdentity
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
    private lateinit var idendityManagement: IdentityManagement

    @RelaxedMockK
    private lateinit var userRepository: UserRepository

    @InjectMockKs
    private lateinit var userManagement: UserManagementImpl

    val now: Instant = Instant.now()

    private val userPrototype = UserEntity(
        id = 1,
        version = now,
        creation = now,
        username = "Qalipsis-test",
    )

    @Test
    fun `should get user`() = testDispatcherProvider.run {
        // given
        val mockedUserEntity = relaxedMockk<UserEntity> {
            every { disabled } returns null
            every { identityReference } returns "identity"
            every { version } returns now
            every { creation } returns now
            every { id } returns 555
        }
        val mockedUserIdentity = relaxedMockk<UserIdentity> {
            every { email_verified } returns true
            every { username } returns "username"
            every { email } returns "email"
            every { name } returns "name"
        }
        coEvery { userRepository.findByUsername("qalipsis") } returns mockedUserEntity
        coEvery { idendityManagement.get("qalipsis", "identity") } returns mockedUserIdentity

        // when
        userManagement.coInvokeInvisible<UserManagementImpl>(
            "get",
            "qalipsis",
            "qalipsis"
        )

        //  then
        coVerifyOrder {
            userRepository.findByUsername("qalipsis")
            idendityManagement.get("qalipsis", "identity")

        }
        confirmVerified(userRepository, idendityManagement)
    }

    @Test
    fun `shouldn't get if user disabled`() = testDispatcherProvider.run {
        // given
        val mockedUserEntity = relaxedMockk<UserEntity> {
            every { disabled } returns now
        }
        coEvery { userRepository.findByUsername("qalipsis") } returns mockedUserEntity

        // when
        userManagement.coInvokeInvisible<UserManagementImpl>(
            "get",
            "qalipsis",
            "qalipsis"
        )

        //  then
        coVerifyOrder {
            userRepository.findByUsername("qalipsis")
        }
        confirmVerified(userRepository, idendityManagement)
    }

    @Test
    fun `should update user`() = testDispatcherProvider.run {
        // given
        val patch = UsernameUserPatch("qalipsis-new")
        val patch2 = AddRoleUserPatch("qalipsis", RoleName.REPORTER)
        val mockedUser = relaxedMockk<QalipsisUser> {
            every { verify_email } returns true
            every { email_verified } returns false
            every { password } returns "pass"
            every { connection } returns "connection"
            every { username } returns "username"
            every { email } returns "email"
            every { name } returns "name"
            every { identityReference } returns "identity"
            every { disabled } returns null
            every { version } returns now
            every { creation } returns now
            every { userEntityId } returns 555
        }
        coEvery { userRepository.update(any()) } returns userPrototype

        // when
        userManagement.coInvokeInvisible<UserManagementImpl>(
            "save",
            "qalipsis",
            mockedUser,
            listOf(patch, patch2)
        )

        //  then
        coVerifyOrder {
            idendityManagement.update(
                "qalipsis",
                "identity",
                any() as UserIdentity,
                any() as List<Auth0Patch>
            )
            userRepository.update(any() as UserEntity)
        }
        confirmVerified(userRepository, idendityManagement)
    }

    @Test
    fun `should disable user`() = testDispatcherProvider.run {
        // given
        val disabledUserEntity = relaxedMockk<UserEntity>()
        val mockedUserEntity = relaxedMockk<UserEntity>()
        coEvery { mockedUserEntity.copy(any()) } returns disabledUserEntity
        coEvery { userRepository.findByUsername("Qalipsis-test") } returns mockedUserEntity
        coEvery { userRepository.update(any()) } returns userPrototype
        coEvery { mockedUserEntity.identityReference } returns "identity"

        // when
        userManagement.coInvokeInvisible<UserManagementImpl>(
            "delete",
            "qalipsis",
            "Qalipsis-test"
        )

        //  then
        coVerifyOrder {
            userRepository.findByUsername("Qalipsis-test")
            userRepository.update(any() as UserEntity)
            idendityManagement.delete("qalipsis", "identity")
        }
        confirmVerified(userRepository, idendityManagement)
    }
}