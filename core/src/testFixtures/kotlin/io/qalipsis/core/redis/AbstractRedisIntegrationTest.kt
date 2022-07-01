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