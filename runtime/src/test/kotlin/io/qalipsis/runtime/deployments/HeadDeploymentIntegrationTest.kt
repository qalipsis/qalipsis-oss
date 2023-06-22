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
import assertk.assertions.matches
import assertk.assertions.none
import assertk.assertions.startsWith
import io.aerisconsulting.catadioptre.getProperty
import io.micronaut.context.BeanRegistration
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.HttpHeaders
import io.micronaut.http.MediaType
import io.qalipsis.runtime.Qalipsis
import io.qalipsis.runtime.bootstrap.QalipsisBootstrap
import io.qalipsis.runtime.deployments.PostgresTestContainerConfiguration.testProperties
import org.awaitility.kotlin.await
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Test class to validate the execution of QALIPSIS as a pure head.
 *
 * @author Eric Jessé
 */
@Testcontainers(parallel = true)
internal class HeadDeploymentIntegrationTest : AbstractDeploymentIntegrationTest() {

    @Test
    @Timeout(20)
    internal fun `should start head with the web interface and remain active`() {
        // given
        val httpPort = SocketUtils.findAvailableTcpPort()
        val qalipsisBootstrap = QalipsisBootstrap()

        // when
        val exitCodeFuture = CompletableFuture.supplyAsync {
            qalipsisBootstrap.start(
                arrayOf(
                    "head",
                    "-c", "micronaut.server.port=$httpPort",
                    "-c",
                    "redis.uri=redis://localhost:${REDIS_CONTAINER.getMappedPort(RedisTestConfiguration.DEFAULT_PORT)}",
                    "-c", "report.export.console.enabled=false",
                    "-c", "report.export.junit.enabled=true",
                    "-c", "report.export.junit.folder=build/test-results/standalone-deployment"
                )
            )
        }

        // then
        checkRestEndpointAvailability(httpPort)
        val qalipsisCoreSingletonObjectsPackages = qalipsisBootstrap.applicationContext
            .getProperty<Any>("singletonScope")
            .getProperty<Map<Any, BeanRegistration<*>>>("singletonByBeanDefinition")
            .values.mapNotNull { it.bean::class.qualifiedName }
            .filter { it.startsWith("io.qalipsis.core") }

        assertThat(qalipsisCoreSingletonObjectsPackages).all {
            any { it.startsWith("io.qalipsis.core.head") }
            none { it.startsWith("io.qalipsis.core.report") }
            none { it.startsWith("io.qalipsis.core.factory") }
        }

        assertThrows<TimeoutException> {
            exitCodeFuture.get(2, TimeUnit.SECONDS)
        }
    }


    @Test
    @Timeout(20)
    internal fun `should start head in persistence mode with the web interface and remain active`() {
        // given
        val httpPort = SocketUtils.findAvailableTcpPort()
        val qalipsisBootstrap = QalipsisBootstrap()

        // when
        val exitCodeFuture = CompletableFuture.supplyAsync {
            qalipsisBootstrap.start(
                arrayOf(
                    "head",
                    "--persistent",
                    "-c", "micronaut.server.port=$httpPort",
                    "-c",
                    "redis.uri=redis://localhost:${REDIS_CONTAINER.getMappedPort(RedisTestConfiguration.DEFAULT_PORT)}",
                    "-c", "report.export.console.enabled=false",
                    "-c", "report.export.junit.enabled=true",
                    "-c", "report.export.junit.folder=build/test-results/standalone-deployment"
                ) + PGSQL_CONTAINER.testProperties().flatMap { (key, value) ->
                    listOf("-c", "$key=$value")
                }.toTypedArray()
            )
        }

        // then
        checkRestEndpointAvailability(httpPort)
        val qalipsisCoreSingletonObjectsPackages = qalipsisBootstrap.applicationContext
            .getProperty<Any>("singletonScope")
            .getProperty<Map<Any, BeanRegistration<*>>>("singletonByBeanDefinition")
            .values.mapNotNull { it.bean::class.qualifiedName }
            .filter { it.startsWith("io.qalipsis.core") }

        assertThat(qalipsisCoreSingletonObjectsPackages).all {
            any { it.startsWith("io.qalipsis.core.head") }
            none { it.startsWith("io.qalipsis.core.report") }
            none { it.startsWith("io.qalipsis.core.factory") }
        }

        assertThrows<TimeoutException> {
            exitCodeFuture.get(2, TimeUnit.SECONDS)
        }
    }

    @Test
    @Timeout(30)
    internal fun `should create a process to start head with the web interface only and remain active`() {
        // given
        val httpPort = SocketUtils.findAvailableTcpPort()

        // when
        val qalipsisProcess = jvmProcessUtils.startNewJavaProcess(
            Qalipsis::class,
            arguments = arrayOf(
                "head",
                "-c", "micronaut.server.port=$httpPort",
                "-c",
                "redis.uri=redis://localhost:${REDIS_CONTAINER.getMappedPort(RedisTestConfiguration.DEFAULT_PORT)}",
                "-c", "logging.level.io.qalipsis.runtime.bootstrap.QalipsisApplicationContext=TRACE"
            ),
            jvmOptions = arrayOf("-Xmx256m")
        )

        // then
        checkRestEndpointAvailability(httpPort)
        assertThrows<TimeoutException> {
            qalipsisProcess.await(Duration.ofSeconds(5))
        }
        assertThat(qalipsisProcess.outputLines).all {
            any { it.matches(Regex(".*to wait before exiting the process.*WebProcessBlocker.*")) }
            none { it.startsWith("Refreshing the scenarios specifications") }
        }
    }

    @Test
    @Timeout(20)
    internal fun `should start head with the autostart and remain active because there is no factory to auto execute`() {
        // given
        val qalipsisBootstrap = QalipsisBootstrap()
        val exitCodeFuture = CompletableFuture.supplyAsync {
            qalipsisBootstrap.start(
                arrayOf(
                    "head",
                    "--autostart",
                    "-c",
                    "redis.uri=redis://localhost:${REDIS_CONTAINER.getMappedPort(RedisTestConfiguration.DEFAULT_PORT)}",
                    "-c", "report.export.console.enabled=false",
                    "-c", "report.export.junit.enabled=true",
                    "-c", "report.export.junit.folder=build/test-results/standalone-deployment"
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
            any { it.startsWith("io.qalipsis.core.head") }
            none { it.startsWith("io.qalipsis.core.report") }
            none { it.startsWith("io.qalipsis.core.head.web") }
            none { it.startsWith("io.qalipsis.core.factory") }
        }

        assertThrows<TimeoutException> {
            exitCodeFuture.get(2, TimeUnit.SECONDS)
        }
    }

    private fun checkRestEndpointAvailability(httpPort: Int) {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:$httpPort"))
            .headers(HttpHeaders.ACCEPT, MediaType.TEXT_PLAIN)
            .build()
        await.await("Querying homepage")
            .atMost(Duration.ofSeconds(20))
            .pollInterval(Duration.ofSeconds(2))
            .until {
                kotlin.runCatching {
                    client.send(request, HttpResponse.BodyHandlers.ofString())
                }.getOrNull() != null
            }
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        assertThat(response.body()).isEqualTo("Welcome to Qalipsis")
    }

    companion object {

        @JvmStatic
        @Container
        val REDIS_CONTAINER = RedisTestConfiguration.createContainer()

        @JvmStatic
        @Container
        val PGSQL_CONTAINER = PostgresTestContainerConfiguration.createContainer()
    }
}