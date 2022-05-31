package io.qalipsis.core.head.jdbc.repository

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.prop
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.exceptions.EmptyResultException
import io.qalipsis.core.head.jdbc.entity.UserEntity
import jakarta.inject.Inject
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class UserRepositoryIntegrationTest : PostgresqlTemplateTest() {

    @Inject
    private lateinit var userRepository: UserRepository

    private val userPrototype = UserEntity(username = "test-user")

    private lateinit var defaultUser: UserEntity

    @BeforeEach
    internal fun setUp() = testDispatcherProvider.run {
        defaultUser = userRepository.findAll().first()
    }

    @AfterEach
    fun tearDown() = testDispatcherProvider.run {
        // Delete the users but the default one.
        userRepository.findAll().filter { it.id != defaultUser.id }.toList().takeIf { it.isNotEmpty() }?.let {
            userRepository.deleteAll(it)
        }
    }

    @Test
    fun `default user should exist`() = testDispatcherProvider.run {
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

        // when
        val allFetched = userRepository.findAll().toList()

        // then
        assertThat(allFetched).hasSize(2)
    }

    @Test
    fun `should not save two users with same username`() = testDispatcherProvider.run {
        // given
        userRepository.save(userPrototype.copy())
        assertThrows<DataAccessException> {
            userRepository.save(userPrototype.copy())
        }
    }

    @Test
    fun `should not save users twice with the same username`() = testDispatcherProvider.run {
        userRepository.save(userPrototype.copy())

        assertThrows<DataAccessException> {
            userRepository.save(userPrototype.copy())
        }
    }

    @Test
    fun `should update the version when the user is updated`() = testDispatcherProvider.run {
        // given
        val saved = userRepository.save(userPrototype.copy())

        // when
        val updated = userRepository.update(saved)

        // then
        assertThat(updated.version).isGreaterThan(saved.version)
    }

    @Test
    fun `should delete user on deleteById`() = testDispatcherProvider.run {
        // given
        val saved = userRepository.save(userPrototype.copy())

        // when
        userRepository.deleteById(saved.id)

        // then
        assertThrows<EmptyResultException> {
            userRepository.findById(saved.id)
        }
    }

    @Test
    fun `should find user by username`() = testDispatcherProvider.run {
        // given
        val saved = userRepository.save(userPrototype.copy())

        // when
        val fetched = userRepository.findByUsername(saved.username)

        // then
        assertThat(fetched).isNotNull().prop(UserEntity::id).isEqualTo(saved.id)
    }

    @Test
    fun `should not find user by username when it does not exist`() = testDispatcherProvider.run {
        assertThrows<EmptyResultException> {
            userRepository.findByUsername("not-qalipsis")
        }
    }

}