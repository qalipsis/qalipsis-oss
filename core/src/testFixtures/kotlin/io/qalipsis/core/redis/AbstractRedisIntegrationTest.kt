package io.qalipsis.core.redis

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import io.qalipsis.core.configuration.ExecutionEnvironments
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Parent class for all integration tests requiring Redis.
 */
@Testcontainers
@MicronautTest(environments = [ExecutionEnvironments.REDIS])
abstract class AbstractRedisIntegrationTest : TestPropertyProvider {

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