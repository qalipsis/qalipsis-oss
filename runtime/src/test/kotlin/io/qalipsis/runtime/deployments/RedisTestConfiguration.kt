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

package io.qalipsis.runtime.deployments

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import kotlin.math.pow

/**
 * Configuration of Redis for testing.
 */
object RedisTestConfiguration {

    /**
     * Default image name and tag.
     */
    const val DEFAULT_DOCKER_IMAGE = "redis"

    /**
     * Default exposed port.
     */
    const val DEFAULT_PORT = 6379

    /**
     * Default [DockerImageName].
     */
    @JvmStatic
    val DEFAULT_DOCKER_IMAGE_NAME = DockerImageName.parse(DEFAULT_DOCKER_IMAGE)

    @JvmStatic
    fun createContainer(redisImageNameAndTag: String = DEFAULT_DOCKER_IMAGE): GenericContainer<Nothing> {
        return GenericContainer<Nothing>(redisImageNameAndTag)
            .apply {
                withCreateContainerCmdModifier { cmd ->
                    cmd.hostConfig!!.withMemory(50 * 1024.0.pow(2).toLong()).withCpuCount(2)
                }
                withExposedPorts(DEFAULT_PORT)
                waitingFor(Wait.forListeningPort())
                withStartupTimeout(Duration.ofSeconds(60))
            }
    }
}