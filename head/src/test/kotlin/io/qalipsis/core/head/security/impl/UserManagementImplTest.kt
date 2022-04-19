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
import io.qalipsis.core.head.security.IdendityManagement
import io.qalipsis.core.head.security.UsernameUserPatch
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
    private lateinit var idendityManagement: IdendityManagement

    @RelaxedMockK
    private lateinit var userRepository: UserRepository

    @InjectMockKs
    private lateinit var userManagement: UserManagementImpl

    val now = Instant.now()

    val userPrototype = UserEntity(
        id = 1,
        version = now,
        creation = now,
        username = "Qalipsis-test",
        displayName = "test",
        emailAddress = "foo@bar.com"
    )

    @Test
    fun `should get user`() = testDispatcherProvider.run {
        // given
        val mockedUserEntity = relaxedMockk<UserEntity> {
            every { disabled } returns null
        }
        coEvery { userRepository.findByUsername("qalipsis") } returns mockedUserEntity

        // when
        userManagement.coInvokeInvisible<UserManagementImpl>(
            "get",
            "qalipsis"
        )

        //  then
        coVerifyOrder {
            userRepository.findByUsername("qalipsis")
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
        val user = UserEntity(username = "qalipsis", displayName = "qalipsis")
        coEvery { userRepository.update(any()) } returns userPrototype

        // when
        userManagement.coInvokeInvisible<UserManagementImpl>(
            "save",
            listOf(patch),
            user
        )

        //  then
        coVerifyOrder {
            idendityManagement.update(any() as UserEntity)
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

        // when
        userManagement.coInvokeInvisible<UserManagementImpl>(
            "delete",
            "Qalipsis-test"
        )

        //  then
        coVerifyOrder {
            userRepository.findByUsername("Qalipsis-test")
            userRepository.update(any() as UserEntity)
            idendityManagement.update(any() as UserEntity)
        }
        confirmVerified(userRepository, idendityManagement)
    }
}