package io.qalipsis.core.head.redis

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isBetween
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.key
import assertk.assertions.prop
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.context.StepName
import io.qalipsis.api.report.CampaignReport
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ReportMessage
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.report.ScenarioReport
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.redis.AbstractRedisIntegrationTest
import io.qalipsis.test.coroutines.TestDispatcherProvider
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant

@ExperimentalLettuceCoroutinesApi
@MicronautTest(environments = [ExecutionEnvironments.REDIS, ExecutionEnvironments.HEAD], startApplication = false)
internal class RedisCampaignReportStateKeeperIntegrationTest : AbstractRedisIntegrationTest() {

    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    @Inject
    private lateinit var registry: RedisCampaignReportStateKeeper

    @AfterEach
    internal fun tearDown() = testDispatcherProvider.run {
        connection.sync().flushdb()
    }

    @Test
    internal fun `should start and stop scenarios, then create a report`() = testDispatcherProvider.run {
        // when
        val beforeStart = Instant.now()
        initCampaignReportData("my-campaign")
        val afterStart = Instant.now()
        val beforeCompleteScenario1 = Instant.now()
        registry.complete("my-campaign", "my-scenario-1")
        val beforeCompleteScenario2 = Instant.now()
        registry.complete("my-campaign", "my-scenario-2")
        val beforeCompleteScenario3 = Instant.now()
        registry.complete("my-campaign", "my-scenario-3")
        val afterCompleteScenario3 = Instant.now()
        registry.complete("my-campaign")

        val report = registry.generateReport("my-campaign")

        // then
        assertThat(report).all {
            prop(CampaignReport::campaignKey).isEqualTo("my-campaign")
            prop(CampaignReport::start).isBetween(beforeStart, afterStart)
            prop(CampaignReport::end).isNotNull().isBetween(beforeCompleteScenario3, afterCompleteScenario3)
            prop(CampaignReport::status).isEqualTo(ExecutionStatus.WARNING)
            prop(CampaignReport::startedMinions).isEqualTo(54 + 32)
            prop(CampaignReport::completedMinions).isEqualTo(43)
            prop(CampaignReport::successfulExecutions).isEqualTo(21 + 43 + 32)
            prop(CampaignReport::failedExecutions).isEqualTo(22 + 32)
            prop(CampaignReport::scenariosReports).all {
                hasSize(3)
                transform { it.associateBy { it.scenarioName } }.all {
                    key("my-scenario-1").all {
                        prop(ScenarioReport::start).isBetween(beforeStart, afterStart)
                        prop(ScenarioReport::end).isNotNull()
                            .isBetween(beforeCompleteScenario1, beforeCompleteScenario2)
                        prop(ScenarioReport::status).isEqualTo(ExecutionStatus.WARNING)
                        prop(ScenarioReport::startedMinions).isEqualTo(54)
                        prop(ScenarioReport::completedMinions).isEqualTo(43)
                        prop(ScenarioReport::successfulExecutions).isEqualTo(21 + 43)
                        prop(ScenarioReport::failedExecutions).isEqualTo(22)
                        prop(ScenarioReport::messages).all {
                            hasSize(3)
                            transform { it.associateBy { it.messageId } }.all {
                                key("message-1").isDataClassEqualTo(
                                    ReportMessage(
                                        "my-step-1", "message-1", ReportMessageSeverity.WARN,
                                        "This is the first message"
                                    )
                                )
                                key("message-3").isDataClassEqualTo(
                                    ReportMessage(
                                        "my-step-2", "message-3", ReportMessageSeverity.INFO,
                                        "This is the third message"
                                    )
                                )
                                key("message-4").isDataClassEqualTo(
                                    ReportMessage(
                                        "my-step-2", "message-4", ReportMessageSeverity.INFO,
                                        "This is the fourth message"
                                    )
                                )
                            }
                        }
                    }

                    key("my-scenario-2").all {
                        prop(ScenarioReport::start).isBetween(beforeStart, afterStart)
                        prop(ScenarioReport::end).isNotNull()
                            .isBetween(beforeCompleteScenario2, beforeCompleteScenario3)
                        prop(ScenarioReport::status).isEqualTo(ExecutionStatus.SUCCESSFUL)
                        prop(ScenarioReport::startedMinions).isEqualTo(32)
                        prop(ScenarioReport::completedMinions).isEqualTo(0)
                        prop(ScenarioReport::successfulExecutions).isEqualTo(32)
                        prop(ScenarioReport::failedExecutions).isEqualTo(32)
                        prop(ScenarioReport::messages).all {
                            hasSize(1)
                            index(0).isDataClassEqualTo(
                                ReportMessage(
                                    "my-step-1", "message-2", ReportMessageSeverity.INFO,
                                    "This is the second message"
                                )
                            )
                        }
                    }

                    key("my-scenario-3").all {
                        prop(ScenarioReport::start).isBetween(beforeStart, afterStart)
                        prop(ScenarioReport::end).isNotNull().isBetween(beforeCompleteScenario3, afterCompleteScenario3)
                        prop(ScenarioReport::status).isEqualTo(ExecutionStatus.SUCCESSFUL)
                        prop(ScenarioReport::startedMinions).isEqualTo(0)
                        prop(ScenarioReport::completedMinions).isEqualTo(0)
                        prop(ScenarioReport::successfulExecutions).isEqualTo(0)
                        prop(ScenarioReport::failedExecutions).isEqualTo(0)
                        prop(ScenarioReport::messages).isEmpty()
                    }
                }
            }
        }
    }

    @Test
    internal fun `should start and abort, then create a report`() = testDispatcherProvider.run {
        // when
        val beforeStart = Instant.now()
        initCampaignReportData("my-campaign")
        val afterStart = Instant.now()
        val beforeAbort = Instant.now()
        registry.abort("my-campaign")
        val afterAbort = Instant.now()

        val report = registry.generateReport("my-campaign")

        // then
        assertThat(report).all {
            prop(CampaignReport::campaignKey).isEqualTo("my-campaign")
            prop(CampaignReport::start).isBetween(beforeStart, afterStart)
            prop(CampaignReport::end).isNotNull().isBetween(beforeAbort, afterAbort)
            prop(CampaignReport::status).isEqualTo(ExecutionStatus.ABORTED)
            prop(CampaignReport::startedMinions).isEqualTo(54 + 32)
            prop(CampaignReport::completedMinions).isEqualTo(43)
            prop(CampaignReport::successfulExecutions).isEqualTo(21 + 43 + 32)
            prop(CampaignReport::failedExecutions).isEqualTo(22 + 32)
            prop(CampaignReport::scenariosReports).all {
                hasSize(3)
                transform { it.associateBy { it.scenarioName } }.all {
                    key("my-scenario-1").all {
                        prop(ScenarioReport::start).isBetween(beforeStart, afterStart)
                        prop(ScenarioReport::end).isNotNull().isBetween(beforeAbort, afterAbort)
                        prop(ScenarioReport::status).isEqualTo(ExecutionStatus.ABORTED)
                        prop(ScenarioReport::startedMinions).isEqualTo(54)
                        prop(ScenarioReport::completedMinions).isEqualTo(43)
                        prop(ScenarioReport::successfulExecutions).isEqualTo(21 + 43)
                        prop(ScenarioReport::failedExecutions).isEqualTo(22)
                        prop(ScenarioReport::messages).all {
                            hasSize(3)
                            transform { it.associateBy { it.messageId } }.all {
                                key("message-1").isDataClassEqualTo(
                                    ReportMessage(
                                        "my-step-1", "message-1", ReportMessageSeverity.WARN,
                                        "This is the first message"
                                    )
                                )
                                key("message-3").isDataClassEqualTo(
                                    ReportMessage(
                                        "my-step-2", "message-3", ReportMessageSeverity.INFO,
                                        "This is the third message"
                                    )
                                )
                                key("message-4").isDataClassEqualTo(
                                    ReportMessage(
                                        "my-step-2", "message-4", ReportMessageSeverity.INFO,
                                        "This is the fourth message"
                                    )
                                )
                            }
                        }
                    }

                    key("my-scenario-2").all {
                        prop(ScenarioReport::start).isBetween(beforeStart, afterStart)
                        prop(ScenarioReport::end).isNotNull().isBetween(beforeAbort, afterAbort)
                        prop(ScenarioReport::status).isEqualTo(ExecutionStatus.ABORTED)
                        prop(ScenarioReport::startedMinions).isEqualTo(32)
                        prop(ScenarioReport::completedMinions).isEqualTo(0)
                        prop(ScenarioReport::successfulExecutions).isEqualTo(32)
                        prop(ScenarioReport::failedExecutions).isEqualTo(32)
                        prop(ScenarioReport::messages).all {
                            hasSize(1)
                            index(0).isDataClassEqualTo(
                                ReportMessage(
                                    "my-step-1", "message-2", ReportMessageSeverity.INFO,
                                    "This is the second message"
                                )
                            )
                        }
                    }

                    key("my-scenario-3").all {
                        prop(ScenarioReport::start).isBetween(beforeStart, afterStart)
                        prop(ScenarioReport::end).isNotNull().isBetween(beforeAbort, afterAbort)
                        prop(ScenarioReport::status).isEqualTo(ExecutionStatus.ABORTED)
                        prop(ScenarioReport::startedMinions).isEqualTo(0)
                        prop(ScenarioReport::completedMinions).isEqualTo(0)
                        prop(ScenarioReport::successfulExecutions).isEqualTo(0)
                        prop(ScenarioReport::failedExecutions).isEqualTo(0)
                        prop(ScenarioReport::messages).isEmpty()
                    }
                }
            }
        }
    }

    @Test
    internal fun `should start and reset then have no longer data for the reset campaign`() =
        testDispatcherProvider.run {
            // given
            initCampaignReportData("my-campaign-1")
            initCampaignReportData("my-campaign-2")

            assertThat(connection.sync().keys("my-campaign-1*").count()).isEqualTo(8)
            assertThat(connection.sync().keys("my-campaign-2*").count()).isEqualTo(8)

            // when
            registry.clear("my-campaign-2")

            // then
            assertThat(connection.sync().keys("my-campaign-1*").count()).isEqualTo(8)
            assertThat(connection.sync().keys("my-campaign-2*").count()).isEqualTo(0)
        }

    private suspend fun initCampaignReportData(campaign: String) {
        registry.start(campaign, "my-scenario-1")
        registry.start(campaign, "my-scenario-2")
        registry.start(campaign, "my-scenario-3")

        putMessage(
            campaign, "my-scenario-1", "my-step-1", ReportMessageSeverity.WARN, "message-1",
            "This is the first message"
        )
        putMessage(
            campaign, "my-scenario-2", "my-step-1", ReportMessageSeverity.INFO, "message-2",
            "This is the second message"
        )
        putMessage(
            campaign, "my-scenario-1", "my-step-2", ReportMessageSeverity.INFO, "message-3",
            "This is the third message"
        )
        putMessage(
            campaign, "my-scenario-1", "my-step-2", ReportMessageSeverity.INFO, "message-4",
            "This is the fourth message"
        )

        setMinionsCounts(campaign, "my-scenario-1", 54, 43)
        setMinionsCounts(campaign, "my-scenario-2", 32, 0)

        setExecutionsCounts(campaign, "my-scenario-1", "my-step-1", 21, 22)
        setExecutionsCounts(campaign, "my-scenario-1", "my-step-2", 43, 0)
        setExecutionsCounts(campaign, "my-scenario-2", "my-step-1", 32, 32)
        setExecutionsCounts(campaign, "my-scenario-2", "my-step-2", 0, 0)
    }


    private suspend fun putMessage(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        stepName: StepName,
        severity: ReportMessageSeverity,
        messageId: Any? = null,
        message: String
    ) {
        val key = "$campaignKey-report:$scenarioName"
        val field = "${stepName}/${messageId}"
        val value = "${severity}/${message.trim()}"
        connection.sync().hset(key, field, value)
    }

    private suspend fun setMinionsCounts(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        started: Int,
        completed: Int
    ) {
        val key = "$campaignKey-report:$scenarioName"
        if (started > 0) {
            connection.sync().hset(key, "__started-minions", started.toString())
        }
        if (completed > 0) {
            connection.sync().hset(key, "__completed-minions", completed.toString())
        }
    }

    private suspend fun setExecutionsCounts(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        stepName: StepName,
        successful: Int,
        failed: Int
    ) {
        if (successful > 0) {
            connection.sync().hset(
                "$campaignKey-report:$scenarioName:successful-step-executions",
                stepName,
                successful.toString()
            )
        }
        if (failed > 0) {
            connection.sync()
                .hset("$campaignKey-report:$scenarioName:failed-step-executions", stepName, failed.toString())
        }
    }
}