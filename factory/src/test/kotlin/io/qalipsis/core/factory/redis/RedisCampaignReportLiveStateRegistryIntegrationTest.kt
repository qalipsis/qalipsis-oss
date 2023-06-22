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

package io.qalipsis.core.factory.redis

import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.key
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.redis.AbstractRedisIntegrationTest
import io.qalipsis.test.coroutines.TestDispatcherProvider
import jakarta.inject.Inject
import kotlinx.coroutines.flow.toList
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.TimeoutException

@ExperimentalLettuceCoroutinesApi
@MicronautTest(environments = [ExecutionEnvironments.REDIS, ExecutionEnvironments.FACTORY], startApplication = false)
internal class RedisCampaignReportLiveStateRegistryIntegrationTest : AbstractRedisIntegrationTest() {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @Inject
    private lateinit var registry: RedisCampaignReportLiveStateRegistry

    @AfterEach
    internal fun tearDown() {
        connection.sync().flushdb()
    }

    @Test
    internal fun `should put, update and delete trimmed messages`() = testDispatcherProvider.run {
        // when
        val message1 = registry.put(
            "my-campaign",
            "my-scenario-1",
            "my-step",
            ReportMessageSeverity.INFO,
            "This is the first message   "
        )
        val message2 = registry.put(
            "my-campaign",
            "my-scenario-1",
            "my-other-step",
            ReportMessageSeverity.ERROR,
            "   This is the second message\n"
        )
        val message3 = registry.put(
            "my-campaign",
            "my-scenario-2",
            "my-other-step",
            ReportMessageSeverity.WARN,
            "  This is the third message   "
        )

        // then
        var messagesOfScenario1 =
            redisCoroutinesCommands.hgetall("my-campaign-report:my-scenario-1").toList()
                .associate { it.key to it.value }
        var messagesOfScenario2 =
            redisCoroutinesCommands.hgetall("my-campaign-report:my-scenario-2").toList()
                .associate { it.key to it.value }
        assertThat(messagesOfScenario1).all {
            hasSize(2)
            key("my-step/$message1").isEqualTo("INFO/This is the first message")
            key("my-other-step/$message2").isEqualTo("ERROR/This is the second message")
        }
        assertThat(messagesOfScenario2).all {
            hasSize(1)
            key("my-other-step/$message3").isEqualTo("WARN/This is the third message")
        }

        // when
        val updatedMessage = registry.put(
            "my-campaign",
            "my-scenario-1",
            "my-step",
            ReportMessageSeverity.WARN,
            message1,
            "This is the first updated message   "
        )

        // then
        assertThat(updatedMessage).isEqualTo(message1)
        messagesOfScenario1 =
            redisCoroutinesCommands.hgetall("my-campaign-report:my-scenario-1").toList()
                .associate { it.key to it.value }
        messagesOfScenario2 =
            redisCoroutinesCommands.hgetall("my-campaign-report:my-scenario-2").toList()
                .associate { it.key to it.value }
        assertThat(messagesOfScenario1).all {
            hasSize(2)
            key("my-step/$message1").isEqualTo("WARN/This is the first updated message")
            key("my-other-step/$message2").isEqualTo("ERROR/This is the second message")
        }
        assertThat(messagesOfScenario2).all {
            hasSize(1)
            key("my-other-step/$message3").isEqualTo("WARN/This is the third message")
        }

        // when
        registry.delete("my-campaign", "my-scenario-1", "my-other-step", message2)

        // then
        messagesOfScenario1 =
            redisCoroutinesCommands.hgetall("my-campaign-report:my-scenario-1").toList()
                .associate { it.key to it.value }
        messagesOfScenario2 =
            redisCoroutinesCommands.hgetall("my-campaign-report:my-scenario-2").toList()
                .associate { it.key to it.value }
        assertThat(messagesOfScenario1).all {
            hasSize(1)
            key("my-step/$message1").isEqualTo("WARN/This is the first updated message")
        }
        assertThat(messagesOfScenario2).all {
            hasSize(1)
            key("my-other-step/$message3").isEqualTo("WARN/This is the third message")
        }
    }

    @Test
    internal fun `should count started and completed minions`() = testDispatcherProvider.run {
        // Starting minions.
        registry.recordStartedMinion("my-campaign", "my-scenario-1", 3)
        assertThat(getCounter("my-campaign-report:my-scenario-1", "__started-minions")).isEqualTo(3)
        assertThat(getCounter("my-campaign-report:my-scenario-1", "__running-minions")).isEqualTo(3)
        assertThat(getCounter("my-campaign-report:my-scenario-1", "__completed-minions")).isNull()

        registry.recordStartedMinion("my-campaign", "my-scenario-2", 2)
        assertThat(getCounter("my-campaign-report:my-scenario-2", "__started-minions")).isEqualTo(2)
        assertThat(getCounter("my-campaign-report:my-scenario-2", "__running-minions")).isEqualTo(2)
        assertThat(getCounter("my-campaign-report:my-scenario-2", "__completed-minions")).isNull()

        registry.recordStartedMinion("my-campaign", "my-scenario-1", 5)
        assertThat(getCounter("my-campaign-report:my-scenario-1", "__started-minions")).isEqualTo(8)
        assertThat(getCounter("my-campaign-report:my-scenario-1", "__running-minions")).isEqualTo(8)
        assertThat(getCounter("my-campaign-report:my-scenario-1", "__completed-minions")).isNull()

        registry.recordStartedMinion("my-campaign", "my-scenario-2", 3)
        assertThat(getCounter("my-campaign-report:my-scenario-2", "__started-minions")).isEqualTo(5)
        assertThat(getCounter("my-campaign-report:my-scenario-2", "__running-minions")).isEqualTo(5)
        assertThat(getCounter("my-campaign-report:my-scenario-2", "__completed-minions")).isNull()

        // Completing minions.
        registry.recordCompletedMinion("my-campaign", "my-scenario-1", 2)
        assertThat(getCounter("my-campaign-report:my-scenario-1", "__started-minions")).isEqualTo(8)
        assertThat(getCounter("my-campaign-report:my-scenario-1", "__running-minions")).isEqualTo(6)
        assertThat(getCounter("my-campaign-report:my-scenario-1", "__completed-minions")).isEqualTo(2)

        registry.recordCompletedMinion("my-campaign", "my-scenario-2", 1)
        assertThat(getCounter("my-campaign-report:my-scenario-2", "__started-minions")).isEqualTo(5)
        assertThat(getCounter("my-campaign-report:my-scenario-2", "__running-minions")).isEqualTo(4)
        assertThat(getCounter("my-campaign-report:my-scenario-2", "__completed-minions")).isEqualTo(1)

        registry.recordCompletedMinion("my-campaign", "my-scenario-1", 3)
        assertThat(getCounter("my-campaign-report:my-scenario-1", "__started-minions")).isEqualTo(8)
        assertThat(getCounter("my-campaign-report:my-scenario-1", "__running-minions")).isEqualTo(3)
        assertThat(getCounter("my-campaign-report:my-scenario-1", "__completed-minions")).isEqualTo(5)

        registry.recordCompletedMinion("my-campaign", "my-scenario-2", 4)
        assertThat(getCounter("my-campaign-report:my-scenario-2", "__started-minions")).isEqualTo(5)
        assertThat(getCounter("my-campaign-report:my-scenario-2", "__running-minions")).isEqualTo(0)
        assertThat(getCounter("my-campaign-report:my-scenario-2", "__completed-minions")).isEqualTo(5)
    }

    @Test
    internal fun `should count successful and failed step initializations`() = testDispatcherProvider.run {
        registry.recordSuccessfulStepInitialization("my-campaign", "my-scenario-1", "my-step-2")
        registry.recordSuccessfulStepInitialization("my-campaign", "my-scenario-1", "my-step-1")
        registry.recordSuccessfulStepInitialization("my-campaign", "my-scenario-2", "my-step-1")

        registry.recordFailedStepInitialization("my-campaign", "my-scenario-1", "my-step-3")
        registry.recordFailedStepInitialization(
            "my-campaign",
            "my-scenario-1",
            "my-step-4",
            TimeoutException("The process could not be completed in time")
        )
        registry.recordFailedStepInitialization(
            "my-campaign",
            "my-scenario-2",
            "my-step-3",
            TimeoutException("The other process could not be completed in time")
        )

        assertThat(getList("my-campaign-report:my-scenario-1:successful-step-initializations")).all {
            hasSize(2)
            containsExactly("my-step-2", "my-step-1")
        }
        assertThat(getList("my-campaign-report:my-scenario-2:successful-step-initializations")).all {
            hasSize(1)
            containsOnly("my-step-1")
        }
        assertThat(getValues("my-campaign-report:my-scenario-1:failed-step-initializations")).all {
            hasSize(2)
            key("my-step-3").isEqualTo("<Unknown>")
            key("my-step-4").isEqualTo("java.util.concurrent.TimeoutException: The process could not be completed in time")
        }
        assertThat(getValues("my-campaign-report:my-scenario-2:failed-step-initializations")).all {
            hasSize(1)
            key("my-step-3").isEqualTo("java.util.concurrent.TimeoutException: The other process could not be completed in time")
        }
    }

    @Test
    internal fun `should count successful and failed step executions`() = testDispatcherProvider.run {
        registry.recordSuccessfulStepExecution("my-campaign", "my-scenario-1", "my-step-1")
        registry.recordSuccessfulStepExecution("my-campaign", "my-scenario-1", "my-step-1")
        registry.recordSuccessfulStepExecution("my-campaign", "my-scenario-1", "my-step-2")
        registry.recordSuccessfulStepExecution("my-campaign", "my-scenario-2", "my-step-1")

        registry.recordFailedStepExecution("my-campaign", "my-scenario-1", "my-step-1")
        registry.recordFailedStepExecution("my-campaign", "my-scenario-1", "my-step-1", 1, TimeoutException(""))
        registry.recordFailedStepExecution("my-campaign", "my-scenario-1", "my-step-1", 3, TimeoutException(""))
        registry.recordFailedStepExecution("my-campaign", "my-scenario-1", "my-step-2")
        registry.recordFailedStepExecution("my-campaign", "my-scenario-2", "my-step-1")
        registry.recordFailedStepExecution("my-campaign", "my-scenario-2", "my-step-1", 1, TimeoutException(""))

        assertThat(getCounter("my-campaign-report:my-scenario-1:successful-step-executions", "my-step-1")).isEqualTo(2)
        assertThat(getCounter("my-campaign-report:my-scenario-1:successful-step-executions", "my-step-2")).isEqualTo(1)
        assertThat(getCounter("my-campaign-report:my-scenario-2:successful-step-executions", "my-step-1")).isEqualTo(1)
        assertThat(getCounter("my-campaign-report:my-scenario-2:successful-step-executions", "my-step-3")).isNull()

        assertThat(getCounters("my-campaign-report:my-scenario-1:failed-step-executions")).all {
            hasSize(3)
            key("my-step-1:<Unknown>").isEqualTo(1)
            key("my-step-1:java.util.concurrent.TimeoutException").isEqualTo(4)
            key("my-step-2:<Unknown>").isEqualTo(1)
        }
        assertThat(getCounters("my-campaign-report:my-scenario-2:failed-step-executions")).all {
            hasSize(2)
            key("my-step-1:<Unknown>").isEqualTo(1)
            key("my-step-1:java.util.concurrent.TimeoutException").isEqualTo(1)
        }
    }

    private suspend fun getCounter(key: String, field: String): Int? =
        redisCoroutinesCommands.hget(key, field)?.toInt()

    private suspend fun getCounters(key: String): Map<String, Int?> =
        redisCoroutinesCommands.hgetall(key).toList().associate { it.key to it.value?.toIntOrNull() }

    private suspend fun getList(key: String): List<String> =
        redisCoroutinesCommands.lrange(key, 0, -1)

    private suspend fun getValues(key: String): Map<String, String?> =
        redisCoroutinesCommands.hgetall(key).toList().associate { it.key to it.value }
}