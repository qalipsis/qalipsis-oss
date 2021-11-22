package io.qalipsis.core.redis

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
        return GenericContainer<Nothing>(DEFAULT_DOCKER_IMAGE_NAME)
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