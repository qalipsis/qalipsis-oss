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
package io.qalipsis.core.head.lock

import assertk.assertThat
import io.lettuce.core.RedisClient
import io.mockk.spyk
import io.qalipsis.core.redis.RedisTestConfiguration
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@WithMockk
internal class RedisLockProviderIntegrationTest {

    @RegisterExtension
    @JvmField
    val testDispatcherProvider = TestDispatcherProvider()

    private lateinit var redisClient: RedisClient

    private lateinit var redisLockProvider: RedisLockProviderImpl

    @BeforeEach
    fun setup() {
        redisClient = RedisClient.create("redis://localhost:${REDIS_CONTAINER.getMappedPort(RedisTestConfiguration.DEFAULT_PORT)}")
    }
    @Test
    fun `should execute the function within the lock`() = testDispatcherProvider.run {
        redisLockProvider = spyk(RedisLockProviderImpl(redisClient), recordPrivateCalls = true)

        //given
        var result = 0

        //when
        List(3) {
            launch {
                redisLockProvider.withLock("campaign-key") {
                    delay(200L)
                }
                result++
            }
        }.joinAll()

        //then
        assertThat(result == 3)
    }

    companion object {
        @JvmStatic
        @Container
        val REDIS_CONTAINER = RedisTestConfiguration.createContainer()
    }
}