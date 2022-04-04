package io.qalipsis.runtime.deployments

import assertk.all
import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.isEqualTo
import assertk.assertions.none
import assertk.assertions.startsWith
import io.aerisconsulting.catadioptre.getProperty
import io.micronaut.context.BeanRegistration
import io.qalipsis.runtime.bootstrap.QalipsisBootstrap
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Test class to validate the execution of QALIPSIS as a pure factory.
 *
 * @author Eric Jessé
 */
@Testcontainers
internal class FactoryDeploymentIntegrationTest : AbstractDeploymentIntegrationTest() {

    @Test
    @Timeout(20)
    internal fun `should start factory and wait for the handshake response`() {
        // when
        val qalipsisBootstrap = QalipsisBootstrap()
        val exitCodeFuture = CompletableFuture.supplyAsync {
            qalipsisBootstrap.start(
                arrayOf(
                    "factory",
                    "-c",
                    "redis.uri=redis://localhost:${REDIS_CONTAINER.getMappedPort(RedisTestConfiguration.DEFAULT_PORT)}"
                )
            )
        }
        Thread.sleep(3000)

        // then
        val qalipsisCoreSingletonObjectsPackages = qalipsisBootstrap.applicationContext
            .getProperty<Map<Any, BeanRegistration<*>>>("singletonObjects")
            .values.mapNotNull { it.bean::class.qualifiedName }
            .filter { it.startsWith("io.qalipsis.core") }

        assertThat(qalipsisCoreSingletonObjectsPackages).all {
            none { it.startsWith("io.qalipsis.core.head") }
            none { it.startsWith("io.qalipsis.core.report") }
            any { it.startsWith("io.qalipsis.core.factory") }
        }

        assertThrows<TimeoutException> {
            exitCodeFuture.get(2, TimeUnit.SECONDS)
        }
    }

    @Test
    @Timeout(10)
    internal fun `should start factory and shut down after the handshake timeout`() {
        // when
        val exitCodeFuture = CompletableFuture.supplyAsync {
            QalipsisBootstrap().start(
                arrayOf(
                    "factory",
                    "-c",
                    "redis.uri=redis://localhost:${REDIS_CONTAINER.getMappedPort(RedisTestConfiguration.DEFAULT_PORT)}",
                    "-c", "factory.handshake.timeout=10ms"
                )
            )
        }

        // then
        val exitCode = exitCodeFuture.get()
        assertThat(exitCode).isEqualTo(101)
    }

    @Test
    @Timeout(10)
    internal fun `should start factory and shut down immediately when there is no enabled scenario`() {
        // when
        val exitCodeFuture = CompletableFuture.supplyAsync {
            QalipsisBootstrap().start(
                arrayOf(
                    "factory",
                    "-c",
                    "redis.uri=redis://localhost:${REDIS_CONTAINER.getMappedPort(RedisTestConfiguration.DEFAULT_PORT)}",
                    "-s", "no-scenario"
                )
            )
        }

        // then
        val exitCode = exitCodeFuture.get()
        assertThat(exitCode).isEqualTo(102)
    }

    companion object {

        @JvmStatic
        @Container
        val REDIS_CONTAINER = RedisTestConfiguration.createContainer()

    }
}