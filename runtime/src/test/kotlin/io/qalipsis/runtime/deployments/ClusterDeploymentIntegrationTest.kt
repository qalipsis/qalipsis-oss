package io.qalipsis.runtime.deployments

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.runtime.Qalipsis
import io.qalipsis.runtime.bootstrap.QalipsisBootstrap
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.nio.file.Files
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Test class to validate the execution of QALIPSIS as a standalone application.
 *
 * @author Eric Jessé
 */
@Testcontainers
internal class ClusterDeploymentIntegrationTest : AbstractDeploymentIntegrationTest() {

    @Test
    @Timeout(60)
    internal fun `should create a cluster and autostart the campaign, then shutdown the cluster`() {
        // given
        val headConfig = arrayOf(
            "head",
            "--autostart",
            "-c", "campaign.required-factories=2",
            "-c", "redis.uri=redis://localhost:${REDIS_CONTAINER.getMappedPort(RedisTestConfiguration.DEFAULT_PORT)}",
            "-c", "report.export.console.enabled=true",
            "-c", "report.export.junit.enabled=false",
            "-c", "report.export.junit.folder=build/test-results/standalone-deployment",
            "-c", "logging.level.io.qalipsis.runtime.bootstrap=TRACE",
            "-c", "logging.level.io.qalipsis.core.head.redis.campaign.RedisFactoryAssignmentState=TRACE",
            "-c", "logging.level.io.qalipsis.core.head.campaign.CampaignAutoStarter=TRACE",
        )
        val factoryConfig = arrayOf(
            "factory",
            "-s", "deployment-test",
            "-c", "redis.uri=redis://localhost:${REDIS_CONTAINER.getMappedPort(RedisTestConfiguration.DEFAULT_PORT)}",
            "-c", "logging.level.io.qalipsis.core.factory=INFO",
            "-c", "logging.level.io.qalipsis.runtime.bootstrap=TRACE",
            "-c", "logging.level.io.qalipsis.core.factory.init=TRACE"
        )
        val head = CompletableFuture.supplyAsync {
            QalipsisBootstrap().start(headConfig)
        }

        val factory1 = jvmProcessUtils.startNewJavaProcess(
            Qalipsis::class,
            arguments = factoryConfig,
            workingDirectory = Files.createTempDirectory("factory-1").toFile()
        )

        val factory2 = jvmProcessUtils.startNewJavaProcess(
            Qalipsis::class,
            arguments = factoryConfig,
            workingDirectory = Files.createTempDirectory("factory-2").toFile()
        )

        // then
        try {
            val headCode = head.get(30, TimeUnit.SECONDS)
            factory1.await(Duration.ofSeconds(2))
            factory2.await(Duration.ofSeconds(2))
            assertThat(headCode).isEqualTo(0)
        } catch (e: TimeoutException) {
            log.error { "Factory 1 OUTPUT: ${factory1.outputLines.joinToString(separator = "\n\t")}" }
            log.error { "Factory 1 ERROR: ${factory1.errorLines.joinToString(separator = "\n\t")}" }
            log.error { "Factory 2 OUTPUT: ${factory2.outputLines.joinToString(separator = "\n\t")}" }
            log.error { "Factory 2 ERROR: ${factory2.errorLines.joinToString(separator = "\n\t")}" }

            throw e
        }
        assertThat(factory1.process.exitValue()).isEqualTo(0)
        assertThat(factory2.process.exitValue()).isEqualTo(0)
    }

    companion object {

        @JvmStatic
        @Container
        val REDIS_CONTAINER = RedisTestConfiguration.createContainer()

        val log = logger()
    }

}