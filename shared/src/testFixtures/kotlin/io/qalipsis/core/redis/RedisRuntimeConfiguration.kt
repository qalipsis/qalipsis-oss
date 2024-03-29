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

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import java.time.Duration
import kotlin.math.pow

/**
 * Configuration of Redis for testing.
 */
object RedisRuntimeConfiguration {

    /**
     * Default image name and tag.
     */
    const val DEFAULT_DOCKER_IMAGE = "redis:alpine"

    /**
     * Default exposed port.
     */
    const val DEFAULT_PORT = 6379

    fun createContainer(redisImageNameAndTag: String = DEFAULT_DOCKER_IMAGE): RedisContainer {
        return RedisContainer(redisImageNameAndTag)
            .apply {
                withStartupAttempts(5)
                withCreateContainerCmdModifier { cmd ->
                    cmd.hostConfig!!.withMemory(50 * 1024.0.pow(2).toLong()).withCpuCount(2)
                }
                withExposedPorts(DEFAULT_PORT)
                waitingFor(Wait.forListeningPort())
                withStartupTimeout(Duration.ofSeconds(60))
            }
    }

    class RedisContainer(dockerImageName: String) : GenericContainer<Nothing>(dockerImageName) {

        fun testProperties(): Map<String, String> {
            return mapOf(
                "redis.uri" to "redis://localhost:${getMappedPort(DEFAULT_PORT)}",
                "redis.io-thread-pool-size" to "2",
                "redis.computation-thread-pool-size" to "2",
                "redis.client-name" to "test"
            )
        }
    }
}