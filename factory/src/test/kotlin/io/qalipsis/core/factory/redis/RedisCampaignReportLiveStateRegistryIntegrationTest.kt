package io.qalipsis.core.factory.redis

import assertk.all
import assertk.assertThat
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

@ExperimentalLettuceCoroutinesApi
@MicronautTest(environments = [ExecutionEnvironments.REDIS, ExecutionEnvironments.FACTORY])
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
            redisCoroutinesCommands.hgetall("_qalipsis_:my-campaign-report:my-scenario-1").toList()
                .associate { it.key to it.value }
        var messagesOfScenario2 =
            redisCoroutinesCommands.hgetall("_qalipsis_:my-campaign-report:my-scenario-2").toList()
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
            redisCoroutinesCommands.hgetall("_qalipsis_:my-campaign-report:my-scenario-1").toList()
                .associate { it.key to it.value }
        messagesOfScenario2 =
            redisCoroutinesCommands.hgetall("_qalipsis_:my-campaign-report:my-scenario-2").toList()
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
            redisCoroutinesCommands.hgetall("_qalipsis_:my-campaign-report:my-scenario-1").toList()
                .associate { it.key to it.value }
        messagesOfScenario2 =
            redisCoroutinesCommands.hgetall("_qalipsis_:my-campaign-report:my-scenario-2").toList()
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
        var runningMinions = registry.recordStartedMinion("my-campaign", "my-scenario-1", 3)
        assertThat(runningMinions).isEqualTo(3)
        assertThat(getCounter("my-campaign-report:my-scenario-1", "__started-minions")).isEqualTo(3)
        assertThat(getCounter("my-campaign-report:my-scenario-1", "__running-minions")).isEqualTo(3)
        assertThat(getCounter("my-campaign-report:my-scenario-1", "__completed-minions")).isNull()

        runningMinions = registry.recordStartedMinion("my-campaign", "my-scenario-2", 2)
        assertThat(runningMinions).isEqualTo(2)
        assertThat(getCounter("my-campaign-report:my-scenario-2", "__started-minions")).isEqualTo(2)
        assertThat(getCounter("my-campaign-report:my-scenario-2", "__running-minions")).isEqualTo(2)
        assertThat(getCounter("my-campaign-report:my-scenario-2", "__completed-minions")).isNull()

        runningMinions = registry.recordStartedMinion("my-campaign", "my-scenario-1", 5)
        assertThat(runningMinions).isEqualTo(8)
        assertThat(getCounter("my-campaign-report:my-scenario-1", "__started-minions")).isEqualTo(8)
        assertThat(getCounter("my-campaign-report:my-scenario-1", "__running-minions")).isEqualTo(8)
        assertThat(getCounter("my-campaign-report:my-scenario-1", "__completed-minions")).isNull()

        runningMinions = registry.recordStartedMinion("my-campaign", "my-scenario-2", 3)
        assertThat(runningMinions).isEqualTo(5)
        assertThat(getCounter("my-campaign-report:my-scenario-2", "__started-minions")).isEqualTo(5)
        assertThat(getCounter("my-campaign-report:my-scenario-2", "__running-minions")).isEqualTo(5)
        assertThat(getCounter("my-campaign-report:my-scenario-2", "__completed-minions")).isNull()

        // Completing minions.
        runningMinions = registry.recordCompletedMinion("my-campaign", "my-scenario-1", 2)
        assertThat(runningMinions).isEqualTo(6)
        assertThat(getCounter("my-campaign-report:my-scenario-1", "__started-minions")).isEqualTo(8)
        assertThat(getCounter("my-campaign-report:my-scenario-1", "__running-minions")).isEqualTo(6)
        assertThat(getCounter("my-campaign-report:my-scenario-1", "__completed-minions")).isEqualTo(2)

        runningMinions = registry.recordCompletedMinion("my-campaign", "my-scenario-2", 1)
        assertThat(runningMinions).isEqualTo(4)
        assertThat(getCounter("my-campaign-report:my-scenario-2", "__started-minions")).isEqualTo(5)
        assertThat(getCounter("my-campaign-report:my-scenario-2", "__running-minions")).isEqualTo(4)
        assertThat(getCounter("my-campaign-report:my-scenario-2", "__completed-minions")).isEqualTo(1)

        runningMinions = registry.recordCompletedMinion("my-campaign", "my-scenario-1", 3)
        assertThat(runningMinions).isEqualTo(3)
        assertThat(getCounter("my-campaign-report:my-scenario-1", "__started-minions")).isEqualTo(8)
        assertThat(getCounter("my-campaign-report:my-scenario-1", "__running-minions")).isEqualTo(3)
        assertThat(getCounter("my-campaign-report:my-scenario-1", "__completed-minions")).isEqualTo(5)

        runningMinions = registry.recordCompletedMinion("my-campaign", "my-scenario-2", 4)
        assertThat(runningMinions).isEqualTo(0)
        assertThat(getCounter("my-campaign-report:my-scenario-2", "__started-minions")).isEqualTo(5)
        assertThat(getCounter("my-campaign-report:my-scenario-2", "__running-minions")).isEqualTo(0)
        assertThat(getCounter("my-campaign-report:my-scenario-2", "__completed-minions")).isEqualTo(5)
    }

    @Test
    internal fun `should count successful and failed step executions`() = testDispatcherProvider.run {
        var successes = registry.recordSuccessfulStepExecution("my-campaign", "my-scenario-1", "my-step-1")
        assertThat(successes).isEqualTo(1)

        successes = registry.recordSuccessfulStepExecution("my-campaign", "my-scenario-1", "my-step-1")
        assertThat(successes).isEqualTo(2)

        successes = registry.recordSuccessfulStepExecution("my-campaign", "my-scenario-1", "my-step-2")
        assertThat(successes).isEqualTo(1)

        successes = registry.recordSuccessfulStepExecution("my-campaign", "my-scenario-2", "my-step-1")
        assertThat(successes).isEqualTo(1)

        var failures = registry.recordFailedStepExecution("my-campaign", "my-scenario-1", "my-step-1")
        assertThat(failures).isEqualTo(1)
        failures = registry.recordFailedStepExecution("my-campaign", "my-scenario-1", "my-step-1")
        assertThat(failures).isEqualTo(2)
        failures = registry.recordFailedStepExecution("my-campaign", "my-scenario-1", "my-step-1")
        assertThat(failures).isEqualTo(3)

        failures = registry.recordFailedStepExecution("my-campaign", "my-scenario-1", "my-step-2")
        assertThat(failures).isEqualTo(1)

        failures = registry.recordFailedStepExecution("my-campaign", "my-scenario-2", "my-step-1")
        assertThat(failures).isEqualTo(1)
        failures = registry.recordFailedStepExecution("my-campaign", "my-scenario-2", "my-step-1")
        assertThat(failures).isEqualTo(2)

        assertThat(getCounter("my-campaign-report:my-scenario-1:successful-step-executions", "my-step-1")).isEqualTo(2)
        assertThat(getCounter("my-campaign-report:my-scenario-1:successful-step-executions", "my-step-2")).isEqualTo(1)
        assertThat(getCounter("my-campaign-report:my-scenario-2:successful-step-executions", "my-step-1")).isEqualTo(1)
        assertThat(getCounter("my-campaign-report:my-scenario-2:successful-step-executions", "my-step-3")).isNull()

        assertThat(getCounter("my-campaign-report:my-scenario-1:failed-step-executions", "my-step-1")).isEqualTo(3)
        assertThat(getCounter("my-campaign-report:my-scenario-1:failed-step-executions", "my-step-2")).isEqualTo(1)
        assertThat(getCounter("my-campaign-report:my-scenario-2:failed-step-executions", "my-step-1")).isEqualTo(2)
        assertThat(getCounter("my-campaign-report:my-scenario-2:failed-step-executions", "my-step-3")).isNull()
    }

    private suspend fun getCounter(key: String, field: String): Int? =
        redisCoroutinesCommands.hget("_qalipsis_:$key", field)?.toInt()
}