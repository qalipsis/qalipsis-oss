package io.qalipsis.core.head.jdbc.repository

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.prop
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.exceptions.EmptyResultException
import io.qalipsis.core.head.jdbc.entity.UserEntity
import jakarta.inject.Inject
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

internal class UserRepositoryIntegrationTest : PostgresqlTemplateTest() {

    @Inject
    private lateinit var userRepository: UserRepository

    val now = Instant.now()

    val userPrototype = UserEntity(
        username = "Qalipsis-test"
    )

    @AfterAll
    fun tearDown() = testDispatcherProvider.run {
        userRepository.deleteAll()
    }

    @Test
    fun `should default user exist`() = testDispatcherProvider.run {
        // when
        val fetched = userRepository.findAll().toList()

        // then
        assertThat(fetched).isNotNull()
        assertThat(fetched[0].username).isEqualTo("_qalipsis_")
    }

    @Test
    fun `should save then get`() = testDispatcherProvider.run {
        // given
        val saved = userRepository.save(userPrototype.copy())

        // when
        val fetched = userRepository.findById(saved.id)

        // then
        assertThat(fetched).all {
            prop(UserEntity::username).isEqualTo(saved.username)
        }
        assertThat(fetched!!.creation.toEpochMilli() == saved.creation.toEpochMilli())
    }

    @Test
    fun `should not save users twice with the same username`() = testDispatcherProvider.run {
        userRepository.save(userPrototype.copy(username = "Qalipsis-test-one"))

        assertThrows<DataAccessException> {
            userRepository.save(userPrototype.copy(username = "Qalipsis-test-one"))
        }
    }

    @Test
    fun `should update the version when the user is updated`() = testDispatcherProvider.run {
        // given
        val saved = userRepository.save(userPrototype.copy(username = "Qalipsis-test-two"))

        // when
        val updated = userRepository.update(saved)

        // then
        assertThat(updated.version).isGreaterThan(saved.version)
    }


    @Test
    fun `should delete user on deleteById`() = testDispatcherProvider.run {
        // given
        val saved = userRepository.save(userPrototype.copy(username = "Qalipsis-test-three"))

        assertThat(userRepository.findById(saved.id)).isNotNull()

        // when
        userRepository.deleteById(saved.id)

        // then
        assertThrows<EmptyResultException> {
            userRepository.findById(saved.id)
        }
    }
}