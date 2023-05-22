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

package io.qalipsis.core.redis

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import io.qalipsis.core.configuration.ExecutionEnvironments
import jakarta.inject.Inject
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Parent class for all integration tests requiring Redis.
 */
@ExperimentalLettuceCoroutinesApi
@Testcontainers
@MicronautTest(environments = [ExecutionEnvironments.REDIS], startApplication = false)
abstract class AbstractRedisIntegrationTest : TestPropertyProvider {

    @Inject
    protected lateinit var connection: StatefulRedisConnection<String, String>

    protected val redisCoroutinesCommands by lazy { connection.coroutines() }

    override fun getProperties(): Map<String, String> {
        return mapOf(
            "redis.uri" to "redis://localhost:${CONTAINER.getMappedPort(RedisTestConfiguration.DEFAULT_PORT)}",
            "redis.io-thread-pool-size" to "2",
            "redis.computation-thread-pool-size" to "2",
            "redis.client-name" to "test"
        )
    }

    companion object {

        @JvmStatic
        @Container
        private val CONTAINER = RedisTestConfiguration.createContainer()
    }
}