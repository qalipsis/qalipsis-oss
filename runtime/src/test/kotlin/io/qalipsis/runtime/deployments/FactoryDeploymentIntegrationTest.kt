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
@Testcontainers(parallel = true)
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
                    "redis.uri=${REDIS_CONTAINER.testProperties()["redis.uri"]}"
                )
            )
        }
        Thread.sleep(3000)

        // then
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
    @Timeout(10)
    internal fun `should start factory and shut down after the handshake timeout`() {
        // when
        val exitCodeFuture = CompletableFuture.supplyAsync {
            QalipsisBootstrap().start(
                arrayOf(
                    "factory",
                    "-c",
                    "redis.uri=${REDIS_CONTAINER.testProperties()["redis.uri"]}",
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
                    "redis.uri=${REDIS_CONTAINER.testProperties()["redis.uri"]}",
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
        val REDIS_CONTAINER = RedisRuntimeConfiguration.createContainer()

    }
}