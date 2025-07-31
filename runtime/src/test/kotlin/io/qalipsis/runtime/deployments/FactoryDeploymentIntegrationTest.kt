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

import assertk.all
import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.none
import assertk.assertions.startsWith
import io.aerisconsulting.catadioptre.getProperty
import io.micronaut.context.BeanRegistration
import io.qalipsis.core.redis.RedisRuntimeConfiguration
import io.qalipsis.runtime.bootstrap.QalipsisBootstrap
import org.awaitility.kotlin.await
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Test class to validate the execution of QALIPSIS as a pure factory.
 *
 * @author Eric Jessé
 */
@Testcontainers(parallel = true)
@Timeout(60)
internal class FactoryDeploymentIntegrationTest : AbstractDeploymentIntegrationTest() {

    @Test
    internal fun `should start factory and wait for the handshake response`() {
        // when
        val qalipsisBootstrap = QalipsisBootstrap()
        val exitCodeFuture = CompletableFuture.supplyAsync {
            qalipsisBootstrap.start(
                arrayOf(
                    "factory",
                    "-c",
                    "redis.uri=${REDIS_CONTAINER.testProperties()["redis.uri"]}"
                )
            )
        }

        // then
        await.await("Await for the application context to be initialized")
            .pollInterval(Duration.ofMillis(500))
            .atMost(Duration.ofSeconds(10))
            .failFast(Callable { exitCodeFuture.isCompletedExceptionally })
            .until { runCatching { qalipsisBootstrap.applicationContext }.getOrNull() != null }

        val qalipsisCoreSingletonObjectsPackages = qalipsisBootstrap.applicationContext
            .getProperty<Any>("singletonScope")
            .getProperty<Map<Any, BeanRegistration<*>>>("singletonByBeanDefinition")
            .values.mapNotNull { it.bean::class.qualifiedName }
            .filter { it.startsWith("io.qalipsis.core") }

        assertThat(qalipsisCoreSingletonObjectsPackages).all {
            none { it.startsWith("io.qalipsis.core.head") }
            none {
                it.all {
                    it.startsWith("io.qalipsis.core.report")
                    it.isNotEqualTo("io.qalipsis.core.reporter.CompositeMeterReporter")
                }
            }
            any { it.startsWith("io.qalipsis.core.factory") }
        }

        assertThrows<TimeoutException> {
            exitCodeFuture.get(2, TimeUnit.SECONDS)
        }
    }

    @Test
    internal fun `should start factory and shut down after the handshake timeout in dry-run mode`() {
        // when
        val exitCode = QalipsisBootstrap().start(
            arrayOf(
                "factory",
                "-c",
                "redis.uri=${REDIS_CONTAINER.testProperties()["redis.uri"]}",
                "-c", "factory.handshake.timeout=10ms",
                "-c", "dry-run.enabled=true"
            )
        )

        // then
        assertThat(exitCode).isEqualTo(101)
    }

    @Test
    internal fun `should start factory and remain active after the handshake timeout in normal mode`() {
        // when
        val exitCodeFuture = CompletableFuture.supplyAsync {
            QalipsisBootstrap().start(
                arrayOf(
                    "factory",
                    "-c", "streaming.platform=redis",
                    "-c",
                    "redis.uri=${REDIS_CONTAINER.testProperties()["redis.uri"]}",
                    "-c", "factory.handshake.timeout=10ms",
                    "-c", "dry-run.enabled=false"
                )
            )
        }

        // then
        assertThrows<TimeoutException> { exitCodeFuture.get(8, TimeUnit.SECONDS) }
    }

    @Test
    internal fun `should start factory and shut down immediately when there is no enabled scenario`() {
        // when
        val exitCode = QalipsisBootstrap().start(
            arrayOf(
                "factory",
                "-c",
                "redis.uri=${REDIS_CONTAINER.testProperties()["redis.uri"]}",
                "-c", "factory.handshake.timeout=10s",
                "-s", "no-scenario",
                "-c", "logging.level.io.qalipsis.runtime.bootstrap=TRACE",
            )
        )

        // then
        assertThat(exitCode).isEqualTo(102)
    }

    companion object {

        @JvmStatic
        @Container
        val REDIS_CONTAINER = RedisRuntimeConfiguration.createContainer()

    }
}