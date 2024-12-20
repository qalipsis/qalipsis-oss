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

package io.qalipsis.core.head.redis

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isBetween
import assertk.assertions.isDataClassEqualTo
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
    private lateinit var reportStateKeeper: RedisCampaignReportStateKeeper

    @AfterEach
    internal fun tearDown() = testDispatcherProvider.run {
        connection.sync().flushdb()
    }

    @Test
    internal fun `should start and stop scenarios with failures, then create a report`() = testDispatcherProvider.run {
        // when
        val beforeStart = Instant.now()
        initCampaignReportData("my-campaign", withFailures = true)
        val afterStart = Instant.now()
        val beforeCompleteScenario1 = Instant.now()
        reportStateKeeper.complete("my-campaign", "my-scenario-1")
        val beforeCompleteScenario2 = Instant.now()
        reportStateKeeper.complete("my-campaign", "my-scenario-2")
        val beforeCompleteScenario3 = Instant.now()
        reportStateKeeper.complete("my-campaign", "my-scenario-3")
        val afterCompleteScenario3 = Instant.now()
        reportStateKeeper.complete("my-campaign")

        val report = reportStateKeeper.generateReport("my-campaign")

        // then
        assertThat(report).all {
            prop(CampaignReport::campaignKey).isEqualTo("my-campaign")
            prop(CampaignReport::start).isNotNull().isBetween(beforeStart, afterStart)
            prop(CampaignReport::end).isNotNull().isBetween(beforeCompleteScenario3, afterCompleteScenario3)
            prop(CampaignReport::status).isEqualTo(ExecutionStatus.FAILED)
            prop(CampaignReport::startedMinions).isEqualTo(54 + 32)
            prop(CampaignReport::completedMinions).isEqualTo(43)
            prop(CampaignReport::successfulExecutions).isEqualTo(21 + 43 + 32)
            prop(CampaignReport::failedExecutions).isEqualTo(22)
            prop(CampaignReport::scenariosReports).all {
                hasSize(3)
                transform { it.associateBy { it.scenarioName } }.all {
                    key("my-scenario-1").all {
                        prop(ScenarioReport::start).isNotNull().isBetween(beforeStart, afterStart)
                        prop(ScenarioReport::end).isNotNull()
                            .isBetween(beforeCompleteScenario1, beforeCompleteScenario2)
                        prop(ScenarioReport::status).isEqualTo(ExecutionStatus.FAILED)
                        prop(ScenarioReport::startedMinions).isEqualTo(54)
                        prop(ScenarioReport::completedMinions).isEqualTo(43)
                        prop(ScenarioReport::successfulExecutions).isEqualTo(21 + 43)
                        prop(ScenarioReport::failedExecutions).isEqualTo(22)
                        prop(ScenarioReport::messages).all {
                            hasSize(6)
                            transform { it.associateBy { it.messageId } }.all {
                                key("_init").isDataClassEqualTo(
                                    ReportMessage(
                                        "_init", "_init", ReportMessageSeverity.INFO,
                                        "Steps successfully initialized: my-step-1, my-step-2"
                                    )
                                )
                                key("my-step-3_init").isDataClassEqualTo(
                                    ReportMessage(
                                        "my-step-3", "my-step-3_init", ReportMessageSeverity.ERROR,
                                        "Any init failure"
                                    )
                                )
                                key("my-step-1_failure_0").isDataClassEqualTo(
                                    ReportMessage(
                                        "my-step-1", "my-step-1_failure_0", ReportMessageSeverity.ERROR,
                                        "Count of errors this-is-an-error: 22 (100.0%)"
                                    )
                                )
                                key("message-1").isDataClassEqualTo(
                                    ReportMessage(
                                        "my-step-1", "message-1", ReportMessageSeverity.INFO,
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
                        prop(ScenarioReport::start).isNotNull().isBetween(beforeStart, afterStart)
                        prop(ScenarioReport::end).isNotNull()
                            .isBetween(beforeCompleteScenario2, beforeCompleteScenario3)
                        prop(ScenarioReport::status).isEqualTo(ExecutionStatus.SUCCESSFUL)
                        prop(ScenarioReport::startedMinions).isEqualTo(32)
                        prop(ScenarioReport::completedMinions).isEqualTo(0)
                        prop(ScenarioReport::successfulExecutions).isEqualTo(32)
                        prop(ScenarioReport::failedExecutions).isEqualTo(0)
                        prop(ScenarioReport::messages).all {
                            hasSize(2)
                            transform { it.associateBy { it.messageId } }.all {
                                key("_init").isDataClassEqualTo(
                                    ReportMessage(
                                        "_init", "_init", ReportMessageSeverity.INFO,
                                        "Steps successfully initialized: my-step-2"
                                    )
                                )
                                key("message-2").isDataClassEqualTo(
                                    ReportMessage(
                                        "my-step-1", "message-2", ReportMessageSeverity.INFO,
                                        "This is the second message"
                                    )
                                )
                            }
                        }
                    }

                    key("my-scenario-3").all {
                        prop(ScenarioReport::start).isNotNull().isBetween(beforeStart, afterStart)
                        prop(ScenarioReport::end).isNotNull().isBetween(beforeCompleteScenario3, afterCompleteScenario3)
                        prop(ScenarioReport::status).isEqualTo(ExecutionStatus.SUCCESSFUL)
                        prop(ScenarioReport::startedMinions).isEqualTo(0)
                        prop(ScenarioReport::completedMinions).isEqualTo(0)
                        prop(ScenarioReport::successfulExecutions).isEqualTo(0)
                        prop(ScenarioReport::failedExecutions).isEqualTo(0)
                        prop(ScenarioReport::messages).all {
                            hasSize(1)
                            index(0).isDataClassEqualTo(
                                ReportMessage(
                                    "_init", "_init", ReportMessageSeverity.INFO,
                                    "Steps successfully initialized: my-step-1"
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    internal fun `should start and stop scenarios without failures, then create a report`() =
        testDispatcherProvider.run {
            // when
            val beforeStart = Instant.now()
            initCampaignReportData("my-campaign")
            val afterStart = Instant.now()
            val beforeCompleteScenario1 = Instant.now()
            reportStateKeeper.complete("my-campaign", "my-scenario-1")
            val beforeCompleteScenario2 = Instant.now()
            reportStateKeeper.complete("my-campaign", "my-scenario-2")
            val beforeCompleteScenario3 = Instant.now()
            reportStateKeeper.complete("my-campaign", "my-scenario-3")
            val afterCompleteScenario3 = Instant.now()
            reportStateKeeper.complete("my-campaign")

            val report = reportStateKeeper.generateReport("my-campaign")

            // then
            assertThat(report).all {
                prop(CampaignReport::campaignKey).isEqualTo("my-campaign")
                prop(CampaignReport::start).isNotNull().isBetween(beforeStart, afterStart)
                prop(CampaignReport::end).isNotNull().isBetween(beforeCompleteScenario3, afterCompleteScenario3)
                prop(CampaignReport::status).isEqualTo(ExecutionStatus.SUCCESSFUL)
                prop(CampaignReport::startedMinions).isEqualTo(54 + 32)
                prop(CampaignReport::completedMinions).isEqualTo(43)
                prop(CampaignReport::successfulExecutions).isEqualTo(21 + 43 + 32)
                prop(CampaignReport::failedExecutions).isEqualTo(0)
                prop(CampaignReport::scenariosReports).all {
                    hasSize(3)
                    transform { it.associateBy { it.scenarioName } }.all {
                        key("my-scenario-1").all {
                            prop(ScenarioReport::start).isNotNull().isBetween(beforeStart, afterStart)
                            prop(ScenarioReport::end).isNotNull()
                                .isBetween(beforeCompleteScenario1, beforeCompleteScenario2)
                            prop(ScenarioReport::status).isEqualTo(ExecutionStatus.SUCCESSFUL)
                            prop(ScenarioReport::startedMinions).isEqualTo(54)
                            prop(ScenarioReport::completedMinions).isEqualTo(43)
                            prop(ScenarioReport::successfulExecutions).isEqualTo(21 + 43)
                            prop(ScenarioReport::failedExecutions).isEqualTo(0)
                            prop(ScenarioReport::messages).all {
                                hasSize(4)
                                transform { it.associateBy { it.messageId } }.all {
                                    key("_init").isDataClassEqualTo(
                                        ReportMessage(
                                            "_init", "_init", ReportMessageSeverity.INFO,
                                            "Steps successfully initialized: my-step-1, my-step-2"
                                        )
                                    )
                                    key("message-1").isDataClassEqualTo(
                                        ReportMessage(
                                            "my-step-1", "message-1", ReportMessageSeverity.INFO,
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
                        prop(ScenarioReport::start).isNotNull().isBetween(beforeStart, afterStart)
                        prop(ScenarioReport::end).isNotNull()
                            .isBetween(beforeCompleteScenario2, beforeCompleteScenario3)
                        prop(ScenarioReport::status).isEqualTo(ExecutionStatus.SUCCESSFUL)
                        prop(ScenarioReport::startedMinions).isEqualTo(32)
                        prop(ScenarioReport::completedMinions).isEqualTo(0)
                        prop(ScenarioReport::successfulExecutions).isEqualTo(32)
                        prop(ScenarioReport::failedExecutions).isEqualTo(0)
                        prop(ScenarioReport::messages).all {
                            hasSize(2)
                            transform { it.associateBy { it.messageId } }.all {
                                key("_init").isDataClassEqualTo(
                                    ReportMessage(
                                        "_init", "_init", ReportMessageSeverity.INFO,
                                        "Steps successfully initialized: my-step-2"
                                    )
                                )
                                key("message-2").isDataClassEqualTo(
                                    ReportMessage(
                                        "my-step-1", "message-2", ReportMessageSeverity.INFO,
                                        "This is the second message"
                                    )
                                )
                            }
                        }
                    }

                    key("my-scenario-3").all {
                        prop(ScenarioReport::start).isNotNull().isBetween(beforeStart, afterStart)
                        prop(ScenarioReport::end).isNotNull().isBetween(beforeCompleteScenario3, afterCompleteScenario3)
                        prop(ScenarioReport::status).isEqualTo(ExecutionStatus.SUCCESSFUL)
                        prop(ScenarioReport::startedMinions).isEqualTo(0)
                        prop(ScenarioReport::completedMinions).isEqualTo(0)
                        prop(ScenarioReport::successfulExecutions).isEqualTo(0)
                        prop(ScenarioReport::failedExecutions).isEqualTo(0)
                        prop(ScenarioReport::messages).all {
                            hasSize(1)
                            index(0).isDataClassEqualTo(
                                ReportMessage(
                                    "_init", "_init", ReportMessageSeverity.INFO,
                                    "Steps successfully initialized: my-step-1"
                                )
                            )
                        }
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
        reportStateKeeper.abort("my-campaign")
        reportStateKeeper.complete("my-campaign", ExecutionStatus.ABORTED, "The campaign was aborted")
        val afterAbort = Instant.now()

        val report = reportStateKeeper.generateReport("my-campaign")

        // then
        assertThat(report).all {
            prop(CampaignReport::campaignKey).isEqualTo("my-campaign")
            prop(CampaignReport::start).isNotNull().isBetween(beforeStart, afterStart)
            prop(CampaignReport::end).isNotNull().isBetween(beforeAbort, afterAbort)
            prop(CampaignReport::status).isEqualTo(ExecutionStatus.ABORTED)
            prop(CampaignReport::startedMinions).isEqualTo(54 + 32)
            prop(CampaignReport::completedMinions).isEqualTo(43)
            prop(CampaignReport::successfulExecutions).isEqualTo(21 + 43 + 32)
            prop(CampaignReport::failedExecutions).isEqualTo(0)
            prop(CampaignReport::scenariosReports).all {
                hasSize(3)
                transform { it.associateBy { it.scenarioName } }.all {
                    key("my-scenario-1").all {
                        prop(ScenarioReport::start).isNotNull().isBetween(beforeStart, afterStart)
                        prop(ScenarioReport::end).isNotNull().isBetween(beforeAbort, afterAbort)
                        prop(ScenarioReport::status).isEqualTo(ExecutionStatus.ABORTED)
                        prop(ScenarioReport::startedMinions).isEqualTo(54)
                        prop(ScenarioReport::completedMinions).isEqualTo(43)
                        prop(ScenarioReport::successfulExecutions).isEqualTo(21 + 43)
                        prop(ScenarioReport::failedExecutions).isEqualTo(0)
                        prop(ScenarioReport::messages).all {
                            hasSize(5)
                            transform { it.associateBy { it.messageId } }.all {
                                key("").isDataClassEqualTo(
                                    ReportMessage(
                                        "", "", ReportMessageSeverity.ERROR,
                                        "The campaign was aborted"
                                    )
                                )
                                key("_init").isDataClassEqualTo(
                                    ReportMessage(
                                        "_init", "_init", ReportMessageSeverity.INFO,
                                        "Steps successfully initialized: my-step-1, my-step-2"
                                    )
                                )
                                key("message-1").isDataClassEqualTo(
                                    ReportMessage(
                                        "my-step-1", "message-1", ReportMessageSeverity.INFO,
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
                        prop(ScenarioReport::start).isNotNull().isBetween(beforeStart, afterStart)
                        prop(ScenarioReport::end).isNotNull().isBetween(beforeAbort, afterAbort)
                        prop(ScenarioReport::status).isEqualTo(ExecutionStatus.ABORTED)
                        prop(ScenarioReport::startedMinions).isEqualTo(32)
                        prop(ScenarioReport::completedMinions).isEqualTo(0)
                        prop(ScenarioReport::successfulExecutions).isEqualTo(32)
                        prop(ScenarioReport::failedExecutions).isEqualTo(0)
                        prop(ScenarioReport::messages).all {
                            hasSize(3)
                            transform { it.associateBy { it.messageId } }.all {
                                key("").isDataClassEqualTo(
                                    ReportMessage(
                                        "", "", ReportMessageSeverity.ERROR,
                                        "The campaign was aborted"
                                    )
                                )
                                key("_init").isDataClassEqualTo(
                                    ReportMessage(
                                        "_init", "_init", ReportMessageSeverity.INFO,
                                        "Steps successfully initialized: my-step-2"
                                    )
                                )
                                key("message-2").isDataClassEqualTo(
                                    ReportMessage(
                                        "my-step-1", "message-2", ReportMessageSeverity.INFO,
                                        "This is the second message"
                                    )
                                )
                            }
                        }
                    }

                    key("my-scenario-3").all {
                        prop(ScenarioReport::start).isNotNull().isBetween(beforeStart, afterStart)
                        prop(ScenarioReport::end).isNotNull().isBetween(beforeAbort, afterAbort)
                        prop(ScenarioReport::status).isEqualTo(ExecutionStatus.ABORTED)
                        prop(ScenarioReport::startedMinions).isEqualTo(0)
                        prop(ScenarioReport::completedMinions).isEqualTo(0)
                        prop(ScenarioReport::successfulExecutions).isEqualTo(0)
                        prop(ScenarioReport::failedExecutions).isEqualTo(0)
                        prop(ScenarioReport::messages).all {
                            hasSize(2)
                            index(0).isDataClassEqualTo(
                                ReportMessage(
                                    "", "", ReportMessageSeverity.ERROR,
                                    "The campaign was aborted"
                                )
                            )
                            index(1).isDataClassEqualTo(
                                ReportMessage(
                                    "_init", "_init", ReportMessageSeverity.INFO,
                                    "Steps successfully initialized: my-step-1"
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    internal fun `should start and stop with failure at completion, then create a report`() =
        testDispatcherProvider.run {
            // when
            val beforeStart = Instant.now()
            initCampaignReportData("my-campaign")
            val afterStart = Instant.now()
            val beforeAbort = Instant.now()
            reportStateKeeper.abort("my-campaign")
            reportStateKeeper.complete("my-campaign", ExecutionStatus.FAILED, "The campaign failed")
            val afterAbort = Instant.now()

            val report = reportStateKeeper.generateReport("my-campaign")

            // then
            assertThat(report).all {
                prop(CampaignReport::campaignKey).isEqualTo("my-campaign")
                prop(CampaignReport::start).isNotNull().isBetween(beforeStart, afterStart)
                prop(CampaignReport::end).isNotNull().isBetween(beforeAbort, afterAbort)
                prop(CampaignReport::status).isEqualTo(ExecutionStatus.FAILED)
                prop(CampaignReport::startedMinions).isEqualTo(54 + 32)
                prop(CampaignReport::completedMinions).isEqualTo(43)
                prop(CampaignReport::successfulExecutions).isEqualTo(21 + 43 + 32)
                prop(CampaignReport::failedExecutions).isEqualTo(0)
                prop(CampaignReport::scenariosReports).all {
                    hasSize(3)
                    transform { it.associateBy { it.scenarioName } }.all {
                        key("my-scenario-1").all {
                            prop(ScenarioReport::start).isNotNull().isBetween(beforeStart, afterStart)
                            prop(ScenarioReport::end).isNotNull().isBetween(beforeAbort, afterAbort)
                            prop(ScenarioReport::status).isEqualTo(ExecutionStatus.FAILED)
                            prop(ScenarioReport::startedMinions).isEqualTo(54)
                            prop(ScenarioReport::completedMinions).isEqualTo(43)
                            prop(ScenarioReport::successfulExecutions).isEqualTo(21 + 43)
                            prop(ScenarioReport::failedExecutions).isEqualTo(0)
                            prop(ScenarioReport::messages).all {
                                hasSize(5)
                                transform { it.associateBy { it.messageId } }.all {
                                    key("").isDataClassEqualTo(
                                        ReportMessage(
                                            "", "", ReportMessageSeverity.ERROR,
                                            "The campaign failed"
                                        )
                                    )
                                    key("_init").isDataClassEqualTo(
                                        ReportMessage(
                                            "_init", "_init", ReportMessageSeverity.INFO,
                                            "Steps successfully initialized: my-step-1, my-step-2"
                                        )
                                    )
                                    key("message-1").isDataClassEqualTo(
                                        ReportMessage(
                                            "my-step-1", "message-1", ReportMessageSeverity.INFO,
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
                            prop(ScenarioReport::start).isNotNull().isBetween(beforeStart, afterStart)
                            prop(ScenarioReport::end).isNotNull().isBetween(beforeAbort, afterAbort)
                            prop(ScenarioReport::status).isEqualTo(ExecutionStatus.FAILED)
                            prop(ScenarioReport::startedMinions).isEqualTo(32)
                            prop(ScenarioReport::completedMinions).isEqualTo(0)
                            prop(ScenarioReport::successfulExecutions).isEqualTo(32)
                            prop(ScenarioReport::failedExecutions).isEqualTo(0)
                            prop(ScenarioReport::messages).all {
                                hasSize(3)
                                transform { it.associateBy { it.messageId } }.all {
                                    key("").isDataClassEqualTo(
                                        ReportMessage(
                                            "", "", ReportMessageSeverity.ERROR,
                                            "The campaign failed"
                                        )
                                    )
                                    key("_init").isDataClassEqualTo(
                                        ReportMessage(
                                            "_init", "_init", ReportMessageSeverity.INFO,
                                            "Steps successfully initialized: my-step-2"
                                        )
                                    )
                                    key("message-2").isDataClassEqualTo(
                                        ReportMessage(
                                            "my-step-1", "message-2", ReportMessageSeverity.INFO,
                                            "This is the second message"
                                        )
                                    )
                                }
                            }
                        }

                        key("my-scenario-3").all {
                            prop(ScenarioReport::start).isNotNull().isBetween(beforeStart, afterStart)
                            prop(ScenarioReport::end).isNotNull().isBetween(beforeAbort, afterAbort)
                            prop(ScenarioReport::status).isEqualTo(ExecutionStatus.FAILED)
                            prop(ScenarioReport::startedMinions).isEqualTo(0)
                            prop(ScenarioReport::completedMinions).isEqualTo(0)
                            prop(ScenarioReport::successfulExecutions).isEqualTo(0)
                            prop(ScenarioReport::failedExecutions).isEqualTo(0)
                            prop(ScenarioReport::messages).all {
                                hasSize(2)
                            index(0).isDataClassEqualTo(
                                ReportMessage(
                                    "", "", ReportMessageSeverity.ERROR,
                                    "The campaign failed"
                                )
                            )
                                index(1).isDataClassEqualTo(
                                    ReportMessage(
                                    "_init", "_init", ReportMessageSeverity.INFO,
                                    "Steps successfully initialized: my-step-1"
                                )
                            )
                        }
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
            initCampaignReportData("my-campaign-2", withFailures = true)

            assertThat(connection.sync().keys("my-campaign-1*").count()).isEqualTo(9)
            assertThat(connection.sync().keys("my-campaign-2*").count()).isEqualTo(11)

            // when
            reportStateKeeper.clear("my-campaign-2")

            // then
            assertThat(connection.sync().keys("my-campaign-1*").count()).isEqualTo(9)
            assertThat(connection.sync().keys("my-campaign-2*").count()).isEqualTo(0)
        }

    private suspend fun initCampaignReportData(
        campaign: String,
        withFailures: Boolean = false,
        withWarnings: Boolean = false
    ) {
        reportStateKeeper.start(campaign, "my-scenario-1")
        reportStateKeeper.start(campaign, "my-scenario-2")
        reportStateKeeper.start(campaign, "my-scenario-3")

        setSuccessfulInitializedSteps(campaign, "my-scenario-1", "my-step-1", "my-step-2")
        if (withFailures) {
            setFailedInitializedSteps(campaign, "my-scenario-1", "my-step-3")
        }
        setSuccessfulInitializedSteps(campaign, "my-scenario-2", "my-step-2")
        setSuccessfulInitializedSteps(campaign, "my-scenario-3", "my-step-1")

        putMessage(
            campaign,
            "my-scenario-1",
            "my-step-1",
            if (withWarnings) ReportMessageSeverity.WARN else ReportMessageSeverity.INFO,
            "message-1",
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

        setExecutionsCounts(campaign, "my-scenario-1", "my-step-1", 21, if (withFailures) 22 else 0)
        setExecutionsCounts(campaign, "my-scenario-1", "my-step-2", 43, 0)
        setExecutionsCounts(campaign, "my-scenario-2", "my-step-1", 32, 0)
        setExecutionsCounts(campaign, "my-scenario-2", "my-step-2", 0, 0)
    }

    private fun setSuccessfulInitializedSteps(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        vararg stepNames: StepName,
    ) {
        connection.sync().rpush(
            "$campaignKey-report:$scenarioName:successful-step-initializations",
            *stepNames
        )
    }

    private fun setFailedInitializedSteps(
        campaignKey: CampaignKey,
        scenarioName: ScenarioName,
        stepName: StepName,
    ) {
        connection.sync().hset(
            "$campaignKey-report:$scenarioName:failed-step-initializations",
            stepName, "Any init failure"
        )
    }

    private fun putMessage(
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

    private fun setMinionsCounts(
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

    private fun setExecutionsCounts(
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
                .hset(
                    "$campaignKey-report:$scenarioName:failed-step-executions",
                    "$stepName:this-is-an-error",
                    failed.toString()
                )
        }
    }
}