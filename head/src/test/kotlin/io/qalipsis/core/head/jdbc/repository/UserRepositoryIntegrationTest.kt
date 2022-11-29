/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package io.qalipsis.core.head.jdbc.repository

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.prop
import io.qalipsis.core.head.jdbc.entity.Defaults
import io.qalipsis.core.head.jdbc.entity.UserEntity
import io.r2dbc.spi.R2dbcDataIntegrityViolationException
import jakarta.inject.Inject
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
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
        assertThat(fetched[0].username).isEqualTo(Defaults.USER)
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
        assertThrows<R2dbcDataIntegrityViolationException> {
            userRepository.save(userPrototype.copy())
        }
    }

    @Test
    fun `should not save users twice with the same username`() = testDispatcherProvider.run {
        userRepository.save(userPrototype.copy())

        assertThrows<R2dbcDataIntegrityViolationException> {
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
        Assertions.assertNull(userRepository.findById(saved.id))
    }

    @Test
    fun `should find user id by username`() = testDispatcherProvider.run {
        // given
        val saved = userRepository.save(userPrototype.copy())

        // when
        val fetched = userRepository.findIdByUsername(saved.username)

        // then
        assertThat(fetched).isNotNull().isEqualTo(saved.id)
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
        Assertions.assertNull(userRepository.findByUsername("not-qalipsis"))
    }

}