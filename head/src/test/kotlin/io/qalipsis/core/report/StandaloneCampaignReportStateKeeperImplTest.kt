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

package io.qalipsis.core.report

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.key
import assertk.assertions.prop
import io.micronaut.scheduling.TaskScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.mockk
import io.qalipsis.api.report.CampaignReport
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ReportMessage
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.api.report.ScenarioReport
import io.qalipsis.api.report.StepReport
import io.qalipsis.api.report.TimeSeriesMeter
import io.qalipsis.core.head.campaign.CampaignService
import io.qalipsis.core.head.inmemory.InMemoryScenarioReportingExecutionState
import io.qalipsis.core.head.inmemory.InMemoryStepExecutionState
import io.qalipsis.core.head.inmemory.StandaloneCampaignReportStateKeeperImpl
import io.qalipsis.core.head.inmemory.catadioptre.campaignStates
import io.qalipsis.core.head.inmemory.consolereporter.ConsoleCampaignProgressionReporter
import io.qalipsis.core.head.model.Campaign
import io.qalipsis.core.head.model.CampaignConfiguration
import io.qalipsis.core.head.model.CampaignExecutionDetails
import io.qalipsis.core.head.model.ScenarioExecutionDetails
import io.qalipsis.core.head.model.ScenarioRequest
import io.qalipsis.core.head.model.Zone
import io.qalipsis.core.head.report.CampaignMeterEnricher
import io.qalipsis.core.head.report.MeterDistribution
import io.qalipsis.core.head.zone.ZoneService
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.lang.TestIdGenerator
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeoutException

@WithMockk
internal class StandaloneCampaignReportStateKeeperImplTest {

    @field:RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    private val idGenerator = TestIdGenerator

    @RelaxedMockK
    private lateinit var taskScheduler: TaskScheduler

    @RelaxedMockK
    private lateinit var consoleReporter: ConsoleCampaignProgressionReporter

    @MockK
    private lateinit var campaignService: CampaignService

    @MockK
    private lateinit var zoneService: ZoneService

    @MockK
    private lateinit var campaignMeterEnricher: CampaignMeterEnricher

    private val now: Instant = Instant.parse("2024-01-15T10:00:00Z")
    private val start: Instant = Instant.parse("2024-01-15T09:00:00Z")
    private val end: Instant = Instant.parse("2024-01-15T09:30:00Z")

    @Test
    internal fun `should start the scenario in all the campaigns`() = testDispatcherProvider.run {
        // given
        val campaignStateKeeper =
            StandaloneCampaignReportStateKeeperImpl(
                idGenerator,
                consoleReporter,
                Duration.ofSeconds(5),
                taskScheduler,
                mockk {
                    every { get() } returns campaignService
                },
                zoneService,
                campaignMeterEnricher
            )

        // when
        campaignStateKeeper.start("the campaign-1", "the scenario-1")
        campaignStateKeeper.start("the campaign-2", "the scenario-1")
        campaignStateKeeper.start("the campaign-2", "the scenario-2")

        // then
        assertThat(campaignStateKeeper.campaignStates()).isNotNull().all {
            key("the campaign-1").all {
                hasSize(1)
                key("the scenario-1").all {
                    prop(InMemoryScenarioReportingExecutionState::scenarioName).isEqualTo("the scenario-1")
                    prop(InMemoryScenarioReportingExecutionState::start).isNotNull()
                    prop(InMemoryScenarioReportingExecutionState::end).isNull()
                    prop(InMemoryScenarioReportingExecutionState::startedMinions).isEqualTo(0)
                    prop(InMemoryScenarioReportingExecutionState::completedMinions).isEqualTo(0)
                    prop(InMemoryScenarioReportingExecutionState::successfulStepExecutions).isEqualTo(0)
                    prop(InMemoryScenarioReportingExecutionState::failedStepExecutions).isEqualTo(0)
                    prop(InMemoryScenarioReportingExecutionState::messages).isEmpty()
                }
            }
            key("the campaign-2").all {
                hasSize(2)
                key("the scenario-1").all {
                    prop(InMemoryScenarioReportingExecutionState::scenarioName).isEqualTo("the scenario-1")
                    prop(InMemoryScenarioReportingExecutionState::start).isNotNull()
                    prop(InMemoryScenarioReportingExecutionState::end).isNull()
                    prop(InMemoryScenarioReportingExecutionState::startedMinions).isEqualTo(0)
                    prop(InMemoryScenarioReportingExecutionState::completedMinions).isEqualTo(0)
                    prop(InMemoryScenarioReportingExecutionState::successfulStepExecutions).isEqualTo(0)
                    prop(InMemoryScenarioReportingExecutionState::failedStepExecutions).isEqualTo(0)
                    prop(InMemoryScenarioReportingExecutionState::messages).isEmpty()
                }
                key("the scenario-2").all {
                    prop(InMemoryScenarioReportingExecutionState::scenarioName).isEqualTo("the scenario-2")
                    prop(InMemoryScenarioReportingExecutionState::start).isNotNull()
                    prop(InMemoryScenarioReportingExecutionState::end).isNull()
                    prop(InMemoryScenarioReportingExecutionState::startedMinions).isEqualTo(0)
                    prop(InMemoryScenarioReportingExecutionState::completedMinions).isEqualTo(0)
                    prop(InMemoryScenarioReportingExecutionState::successfulStepExecutions).isEqualTo(0)
                    prop(InMemoryScenarioReportingExecutionState::failedStepExecutions).isEqualTo(0)
                    prop(InMemoryScenarioReportingExecutionState::messages).isEmpty()
                }
            }
        }
    }

    @Test
    internal fun `should start the scenario`() = testDispatcherProvider.run {
        // given
        val campaignStateKeeper =
            StandaloneCampaignReportStateKeeperImpl(
                idGenerator,
                consoleReporter,
                Duration.ofSeconds(5),
                taskScheduler,
                mockk {
                    every { get() } returns campaignService
                },
                zoneService,
                campaignMeterEnricher
            )

        // when
        campaignStateKeeper.start("the campaign", "the scenario")

        // then
        assertThat(campaignStateKeeper.campaignStates().get("the campaign")).isNotNull().all {
            hasSize(1)
            key("the scenario").all {
                prop(InMemoryScenarioReportingExecutionState::scenarioName).isEqualTo("the scenario")
                prop(InMemoryScenarioReportingExecutionState::start).isNotNull()
                prop(InMemoryScenarioReportingExecutionState::end).isNull()
                prop(InMemoryScenarioReportingExecutionState::startedMinions).isEqualTo(0)
                prop(InMemoryScenarioReportingExecutionState::completedMinions).isEqualTo(0)
                prop(InMemoryScenarioReportingExecutionState::successfulStepExecutions).isEqualTo(0)
                prop(InMemoryScenarioReportingExecutionState::failedStepExecutions).isEqualTo(0)
                prop(InMemoryScenarioReportingExecutionState::messages).isEmpty()
            }
        }
        coVerifyOnce {
            consoleReporter.start("the scenario")
        }
        confirmVerified(consoleReporter)
    }

    @Test
    internal fun `should complete the started scenario`() = testDispatcherProvider.run {
        // given
        val campaignStateKeeper =
            StandaloneCampaignReportStateKeeperImpl(
                idGenerator,
                consoleReporter,
                Duration.ofSeconds(5),
                taskScheduler,
                mockk {
                    every { get() } returns campaignService
                },
                zoneService,
                campaignMeterEnricher
            )
        campaignStateKeeper.start("the campaign", "the scenario")

        // when
        campaignStateKeeper.complete("the campaign", "the scenario")

        // then
        assertThat(campaignStateKeeper.campaignStates().get("the campaign")).isNotNull().all {
            hasSize(1)
            key("the scenario").all {
                prop(InMemoryScenarioReportingExecutionState::scenarioName).isEqualTo("the scenario")
                prop(InMemoryScenarioReportingExecutionState::start).isNotNull()
                prop(InMemoryScenarioReportingExecutionState::end).isNotNull()
                prop(InMemoryScenarioReportingExecutionState::startedMinions)
                    .isEqualTo(0)
                prop(InMemoryScenarioReportingExecutionState::completedMinions)
                    .isEqualTo(0)
                prop(InMemoryScenarioReportingExecutionState::successfulStepExecutions)
                    .isEqualTo(0)
                prop(InMemoryScenarioReportingExecutionState::failedStepExecutions)
                    .isEqualTo(0)
                prop(InMemoryScenarioReportingExecutionState::messages).isEmpty()
            }
        }
        coVerifyOrder {
            consoleReporter.start("the scenario")
            consoleReporter.complete("the scenario")
        }
        confirmVerified(consoleReporter)
    }

    @Test
    internal fun `should add a message to the started scenario`() = testDispatcherProvider.runTest {
        // given
        val campaignStateKeeper =
            StandaloneCampaignReportStateKeeperImpl(
                idGenerator,
                consoleReporter,
                Duration.ofSeconds(5),
                taskScheduler,
                mockk {
                    every { get() } returns campaignService
                },
                zoneService,
                campaignMeterEnricher
            )
        campaignStateKeeper.start("the campaign", "the scenario")

        // when
        val messageId = campaignStateKeeper.put(
            "the campaign",
            "the scenario",
            "the step",
            ReportMessageSeverity.INFO,
            "The message"
        )

        // then
        assertThat(campaignStateKeeper.campaignStates().get("the campaign")).isNotNull().all {
            hasSize(1)
            key("the scenario").all {
                prop(InMemoryScenarioReportingExecutionState::scenarioName).isEqualTo("the scenario")
                prop(InMemoryScenarioReportingExecutionState::start).isNotNull()
                prop(InMemoryScenarioReportingExecutionState::end).isNull()
                prop(InMemoryScenarioReportingExecutionState::startedMinions)
                    .isEqualTo(0)
                prop(InMemoryScenarioReportingExecutionState::completedMinions)
                    .isEqualTo(0)
                prop(InMemoryScenarioReportingExecutionState::successfulStepExecutions)
                    .isEqualTo(0)
                prop(InMemoryScenarioReportingExecutionState::failedStepExecutions)
                    .isEqualTo(0)
                prop(InMemoryScenarioReportingExecutionState::messages).all {
                    hasSize(1)
                    index(0).all {
                        prop(ReportMessage::messageId).isEqualTo(messageId)
                        prop(ReportMessage::stepName).isEqualTo("the step")
                        prop(ReportMessage::severity).isEqualTo(ReportMessageSeverity.INFO)
                        prop(ReportMessage::message).isEqualTo("The message")
                    }
                }
            }
        }
        coVerifyOrder {
            consoleReporter.start("the scenario")
            consoleReporter.attachMessage(
                scenarioName = "the scenario",
                stepName = "the step",
                severity = ReportMessageSeverity.INFO,
                messageId = any(),
                message = "The message"
            )
        }
        confirmVerified(consoleReporter)
    }

    @Test
    internal fun `should delete a message from the started scenario`() = testDispatcherProvider.runTest {
        // given
        val campaignStateKeeper =
            StandaloneCampaignReportStateKeeperImpl(
                idGenerator,
                consoleReporter,
                Duration.ofSeconds(5),
                taskScheduler,
                mockk {
                    every { get() } returns campaignService
                },
                zoneService,
                campaignMeterEnricher
            )
        campaignStateKeeper.start("the campaign", "the scenario")
        val messageId = campaignStateKeeper.put(
            "the campaign",
            "the scenario",
            "the step",
            ReportMessageSeverity.INFO,
            "The message"
        )

        // when
        campaignStateKeeper.delete("the campaign", "the scenario", "the step", messageId)

        // then
        assertThat(campaignStateKeeper.campaignStates().get("the campaign")).isNotNull().all {
            hasSize(1)
            key("the scenario").all {
                prop(InMemoryScenarioReportingExecutionState::scenarioName).isEqualTo("the scenario")
                prop(InMemoryScenarioReportingExecutionState::start).isNotNull()
                prop(InMemoryScenarioReportingExecutionState::end).isNull()
                prop(InMemoryScenarioReportingExecutionState::startedMinions)
                    .isEqualTo(0)
                prop(InMemoryScenarioReportingExecutionState::completedMinions)
                    .isEqualTo(0)
                prop(InMemoryScenarioReportingExecutionState::successfulStepExecutions)
                    .isEqualTo(0)
                prop(InMemoryScenarioReportingExecutionState::failedStepExecutions)
                    .isEqualTo(0)
                prop(InMemoryScenarioReportingExecutionState::messages).isEmpty()
            }
        }
        coVerifyOrder {
            consoleReporter.start("the scenario")
            consoleReporter.attachMessage(
                scenarioName = "the scenario",
                stepName = "the step",
                severity = ReportMessageSeverity.INFO,
                messageId = any(),
                message = "The message"
            )
            consoleReporter.detachMessage("the scenario", "the step", messageId)
        }
        confirmVerified(consoleReporter)
    }

    @Test
    internal fun `should record started minions to the started scenario`() = testDispatcherProvider.runTest {
        // given
        val campaignStateKeeper =
            StandaloneCampaignReportStateKeeperImpl(
                idGenerator,
                consoleReporter,
                Duration.ofSeconds(5),
                taskScheduler,
                mockk {
                    every { get() } returns campaignService
                },
                zoneService,
                campaignMeterEnricher
            )
        campaignStateKeeper.start("the campaign", "the scenario")

        // when
        campaignStateKeeper.recordStartedMinion("the campaign", "the scenario", 5)
        campaignStateKeeper.recordStartedMinion("the campaign", "the scenario", 3)

        // then
        assertThat(campaignStateKeeper.campaignStates().get("the campaign")).isNotNull().all {
            hasSize(1)
            key("the scenario").all {
                prop(InMemoryScenarioReportingExecutionState::scenarioName).isEqualTo("the scenario")
                prop(InMemoryScenarioReportingExecutionState::start).isNotNull()
                prop(InMemoryScenarioReportingExecutionState::end).isNull()
                prop(InMemoryScenarioReportingExecutionState::startedMinions)
                    .isEqualTo(8)
                prop(InMemoryScenarioReportingExecutionState::completedMinions)
                    .isEqualTo(0)
                prop(InMemoryScenarioReportingExecutionState::successfulStepExecutions)
                    .isEqualTo(0)
                prop(InMemoryScenarioReportingExecutionState::failedStepExecutions)
                    .isEqualTo(0)
                prop(InMemoryScenarioReportingExecutionState::messages).isEmpty()
            }
        }
        coVerifyOrder {
            consoleReporter.start("the scenario")
            consoleReporter.recordStartedMinion("the scenario", 5)
            consoleReporter.recordStartedMinion("the scenario", 3)
        }
        confirmVerified(consoleReporter)
    }

    @Test
    internal fun `should record completed minions to the started scenario`() = testDispatcherProvider.runTest {
        // given
        val campaignStateKeeper =
            StandaloneCampaignReportStateKeeperImpl(
                idGenerator,
                consoleReporter,
                Duration.ofSeconds(5),
                taskScheduler,
                mockk {
                    every { get() } returns campaignService
                },
                zoneService,
                campaignMeterEnricher
            )
        campaignStateKeeper.start("the campaign", "the scenario")

        // when
        campaignStateKeeper.recordCompletedMinion("the campaign", "the scenario", 5)
        campaignStateKeeper.recordCompletedMinion("the campaign", "the scenario", 3)

        // then
        assertThat(campaignStateKeeper.campaignStates().get("the campaign")).isNotNull().all {
            hasSize(1)
            key("the scenario").all {
                prop(InMemoryScenarioReportingExecutionState::scenarioName).isEqualTo("the scenario")
                prop(InMemoryScenarioReportingExecutionState::start).isNotNull()
                prop(InMemoryScenarioReportingExecutionState::end).isNull()
                prop(InMemoryScenarioReportingExecutionState::startedMinions)
                    .isEqualTo(0)
                prop(InMemoryScenarioReportingExecutionState::completedMinions)
                    .isEqualTo(8)
                prop(InMemoryScenarioReportingExecutionState::successfulStepExecutions)
                    .isEqualTo(0)
                prop(InMemoryScenarioReportingExecutionState::failedStepExecutions)
                    .isEqualTo(0)
                prop(InMemoryScenarioReportingExecutionState::messages).isEmpty()
            }
        }
        coVerifyOrder {
            consoleReporter.start("the scenario")
            consoleReporter.recordCompletedMinion("the scenario", 5)
            consoleReporter.recordCompletedMinion("the scenario", 3)
        }
        confirmVerified(consoleReporter)
    }

    @Test
    internal fun `should successful steps to the started scenario`() = testDispatcherProvider.runTest {
        // given
        val campaignStateKeeper =
            StandaloneCampaignReportStateKeeperImpl(
                idGenerator,
                consoleReporter,
                Duration.ofSeconds(5),
                taskScheduler,
                mockk {
                    every { get() } returns campaignService
                },
                zoneService,
                campaignMeterEnricher
            )
        campaignStateKeeper.start("the campaign", "the scenario")

        // when
        campaignStateKeeper.recordSuccessfulStepExecution("the campaign", "the scenario", "the step", 5)
        campaignStateKeeper.recordSuccessfulStepExecution("the campaign", "the scenario", "the step", 3)
        campaignStateKeeper.recordSuccessfulStepExecution("the campaign", "the scenario", "the other step", 10)

        // then
        assertThat(campaignStateKeeper.campaignStates().get("the campaign")).isNotNull().all {
            hasSize(1)
            key("the scenario").all {
                prop(InMemoryScenarioReportingExecutionState::scenarioName).isEqualTo("the scenario")
                prop(InMemoryScenarioReportingExecutionState::start).isNotNull()
                prop(InMemoryScenarioReportingExecutionState::end).isNull()
                prop(InMemoryScenarioReportingExecutionState::startedMinions)
                    .isEqualTo(0)
                prop(InMemoryScenarioReportingExecutionState::completedMinions)
                    .isEqualTo(0)
                prop(InMemoryScenarioReportingExecutionState::successfulStepExecutions)
                    .isEqualTo(18)
                prop(InMemoryScenarioReportingExecutionState::failedStepExecutions)
                    .isEqualTo(0)
                prop(InMemoryScenarioReportingExecutionState::messages).isEmpty()
            }
        }
        coVerifyOrder {
            consoleReporter.start("the scenario")
            consoleReporter.recordSuccessfulStepExecution("the scenario", "the step", 5)
            consoleReporter.recordSuccessfulStepExecution("the scenario", "the step", 3)
            consoleReporter.recordSuccessfulStepExecution("the scenario", "the other step", 10)
        }
        confirmVerified(consoleReporter)
    }

    @Test
    internal fun `should record failed steps to the started scenario`() = testDispatcherProvider.runTest {
        // given
        val campaignStateKeeper =
            StandaloneCampaignReportStateKeeperImpl(
                idGenerator,
                consoleReporter,
                Duration.ofSeconds(5),
                taskScheduler,
                mockk {
                    every { get() } returns campaignService
                },
                zoneService,
                campaignMeterEnricher
            )
        campaignStateKeeper.start("the campaign", "the scenario")

        // when
        val failure = TimeoutException()
        campaignStateKeeper.recordFailedStepExecution("the campaign", "the scenario", "the step", 5)
        campaignStateKeeper.recordFailedStepExecution("the campaign", "the scenario", "the step", 3, failure)
        campaignStateKeeper.recordFailedStepExecution("the campaign", "the scenario", "the other step", 10)

        // then
        assertThat(campaignStateKeeper.campaignStates().get("the campaign")).isNotNull().all {
            hasSize(1)
            key("the scenario").all {
                prop(InMemoryScenarioReportingExecutionState::scenarioName).isEqualTo("the scenario")
                prop(InMemoryScenarioReportingExecutionState::start).isNotNull()
                prop(InMemoryScenarioReportingExecutionState::end).isNull()
                prop(InMemoryScenarioReportingExecutionState::startedMinions)
                    .isEqualTo(0)
                prop(InMemoryScenarioReportingExecutionState::completedMinions)
                    .isEqualTo(0)
                prop(InMemoryScenarioReportingExecutionState::successfulStepExecutions)
                    .isEqualTo(0)
                prop(InMemoryScenarioReportingExecutionState::failedStepExecutions)
                    .isEqualTo(18)
                prop(InMemoryScenarioReportingExecutionState::messages).isEmpty()
            }
        }
        coVerifyOrder {
            consoleReporter.start("the scenario")
            consoleReporter.recordFailedStepExecution("the scenario", "the step", 5, null)
            consoleReporter.recordFailedStepExecution("the scenario", "the step", 3, refEq(failure))
            consoleReporter.recordFailedStepExecution("the scenario", "the other step", 10, null)
        }
        confirmVerified(consoleReporter)
    }

    @Test
    internal fun `should record successful step initialization`() = testDispatcherProvider.runTest {
        // given
        val campaignStateKeeper =
            StandaloneCampaignReportStateKeeperImpl(
                idGenerator,
                consoleReporter,
                Duration.ofSeconds(5),
                taskScheduler,
                mockk {
                    every { get() } returns campaignService
                },
                zoneService,
                campaignMeterEnricher
            )
        campaignStateKeeper.start("the campaign", "the scenario")

        // when
        campaignStateKeeper.recordSuccessfulStepInitialization("the campaign", "the scenario", "step-1", "dag-1", true)
        campaignStateKeeper.recordSuccessfulStepInitialization("the campaign", "the scenario", "step-2", "dag-2", false)

        // then
        assertThat(campaignStateKeeper.campaignStates()["the campaign"]!!["the scenario"]!!.stepStates).all {
            hasSize(2)
            key("dag-1/step-1").all {
                prop(InMemoryStepExecutionState::name).isEqualTo("step-1")
                prop(InMemoryStepExecutionState::dagId).isEqualTo("dag-1")
                prop(InMemoryStepExecutionState::isUnderLoad).isEqualTo(true)
                prop(InMemoryStepExecutionState::initialized).isEqualTo(true)
                prop(InMemoryStepExecutionState::initializationError).isNull()
            }
            key("dag-2/step-2").all {
                prop(InMemoryStepExecutionState::name).isEqualTo("step-2")
                prop(InMemoryStepExecutionState::dagId).isEqualTo("dag-2")
                prop(InMemoryStepExecutionState::isUnderLoad).isEqualTo(false)
                prop(InMemoryStepExecutionState::initialized).isEqualTo(true)
                prop(InMemoryStepExecutionState::initializationError).isNull()
            }
        }
    }

    @Test
    internal fun `should record failed step initialization`() = testDispatcherProvider.runTest {
        // given
        val campaignStateKeeper =
            StandaloneCampaignReportStateKeeperImpl(
                idGenerator,
                consoleReporter,
                Duration.ofSeconds(5),
                taskScheduler,
                mockk {
                    every { get() } returns campaignService
                },
                zoneService,
                campaignMeterEnricher
            )
        campaignStateKeeper.start("the campaign", "the scenario")

        // when
        campaignStateKeeper.recordFailedStepInitialization(
            "the campaign", "the scenario", "step-1", "dag-1", true,
            TimeoutException("Connection timed out")
        )
        campaignStateKeeper.recordFailedStepInitialization(
            "the campaign", "the scenario", "step-2", "dag-2", false
        )

        // then
        assertThat(campaignStateKeeper.campaignStates()["the campaign"]!!["the scenario"]!!.stepStates).all {
            hasSize(2)
            key("dag-1/step-1").all {
                prop(InMemoryStepExecutionState::initialized).isEqualTo(false)
                prop(InMemoryStepExecutionState::initializationError)
                    .isEqualTo("java.util.concurrent.TimeoutException: Connection timed out")
            }
            key("dag-2/step-2").all {
                prop(InMemoryStepExecutionState::initialized).isEqualTo(false)
                prop(InMemoryStepExecutionState::initializationError).isEqualTo("<Unknown>")
            }
        }
    }

    @Test
    internal fun `should track per-step execution counters`() = testDispatcherProvider.runTest {
        // given
        val campaignStateKeeper =
            StandaloneCampaignReportStateKeeperImpl(
                idGenerator,
                consoleReporter,
                Duration.ofSeconds(5),
                taskScheduler,
                mockk {
                    every { get() } returns campaignService
                },
                zoneService,
                campaignMeterEnricher
            )
        campaignStateKeeper.start("the campaign", "the scenario")
        campaignStateKeeper.recordSuccessfulStepInitialization("the campaign", "the scenario", "step-1", "dag-1", true)
        campaignStateKeeper.recordSuccessfulStepInitialization("the campaign", "the scenario", "step-2", "dag-1", true)

        // when
        campaignStateKeeper.recordSuccessfulStepExecution("the campaign", "the scenario", "step-1", 5)
        campaignStateKeeper.recordSuccessfulStepExecution("the campaign", "the scenario", "step-1", 3)
        campaignStateKeeper.recordSuccessfulStepExecution("the campaign", "the scenario", "step-2", 7)
        campaignStateKeeper.recordFailedStepExecution("the campaign", "the scenario", "step-1", 2)
        campaignStateKeeper.recordFailedStepExecution("the campaign", "the scenario", "step-2", 4, TimeoutException())

        // then
        val stepStates = campaignStateKeeper.campaignStates()["the campaign"]!!["the scenario"]!!.stepStates
        assertThat(stepStates["dag-1/step-1"]!!.successfulExecutionsCounter.get()).isEqualTo(8L)
        assertThat(stepStates["dag-1/step-1"]!!.failedExecutionsCounter.get()).isEqualTo(2L)
        assertThat(stepStates["dag-1/step-2"]!!.successfulExecutionsCounter.get()).isEqualTo(7L)
        assertThat(stepStates["dag-1/step-2"]!!.failedExecutionsCounter.get()).isEqualTo(4L)
    }

    @Test
    internal fun `should preserve step initialization order in the steps list`() = testDispatcherProvider.runTest {
        // given
        val campaignStateKeeper =
            StandaloneCampaignReportStateKeeperImpl(
                idGenerator,
                consoleReporter,
                Duration.ofSeconds(5),
                taskScheduler,
                mockk {
                    every { get() } returns campaignService
                },
                zoneService,
                campaignMeterEnricher
            )
        campaignStateKeeper.start("the campaign", "the scenario")

        // when - register in non-alphabetical order
        campaignStateKeeper.recordSuccessfulStepInitialization("the campaign", "the scenario", "step-3", "dag-1", true)
        campaignStateKeeper.recordSuccessfulStepInitialization("the campaign", "the scenario", "step-1", "dag-2", false)
        campaignStateKeeper.recordFailedStepInitialization("the campaign", "the scenario", "step-2", "dag-1", true)

        // then
        val state = campaignStateKeeper.campaignStates()["the campaign"]!!["the scenario"]!!
        assertThat(state.steps).all {
            hasSize(3)
            index(0).prop(StepReport::name).isEqualTo("step-3")
            index(1).prop(StepReport::name).isEqualTo("step-1")
            index(2).prop(StepReport::name).isEqualTo("step-2")
        }
    }

    @Test
    @Timeout(1)
    internal fun `should not generate a report while there are running scenarios`() = testDispatcherProvider.run {
        // given
        val campaignStateKeeper =
            StandaloneCampaignReportStateKeeperImpl(
                idGenerator,
                consoleReporter,
                Duration.ofSeconds(5),
                taskScheduler,
                mockk {
                    every { get() } returns campaignService
                },
                zoneService,
                campaignMeterEnricher
            )
        campaignStateKeeper.start("the campaign", "the scenario 1")

        // when + then
        assertThrows<TimeoutCancellationException> {
            withTimeout(300) {
                campaignStateKeeper.generateReport("the campaign")
            }
        }
    }

    @Test
    @Timeout(1)
    internal fun `should not generate a report when nothing started`() = testDispatcherProvider.run {
        // given
        val campaignStateKeeper =
            StandaloneCampaignReportStateKeeperImpl(
                idGenerator,
                consoleReporter,
                Duration.ofSeconds(5),
                taskScheduler,
                mockk {
                    every { get() } returns campaignService
                },
                zoneService,
                campaignMeterEnricher
            )

        // when + then
        assertThrows<NoSuchElementException> {
            campaignStateKeeper.generateReport("the campaign")
        }
    }

    @Test
    @Timeout(1)
    internal fun `should allow the report when the campaign started and is aborted`() = testDispatcherProvider.run {
        // given
        val campaignStateKeeper =
            StandaloneCampaignReportStateKeeperImpl(
                idGenerator,
                consoleReporter,
                Duration.ofSeconds(5),
                taskScheduler,
                mockk {
                    every { get() } returns campaignService
                },
                zoneService,
                campaignMeterEnricher
            )
        campaignStateKeeper.start("the campaign", "the scenario 1")
        campaignStateKeeper.abort("the campaign")

        // when
        val report = campaignStateKeeper.generateReport("the campaign")

        // then
        assertThat(report).isNotNull().all {
            prop(CampaignReport::campaignKey).isEqualTo("the campaign")
            prop(CampaignReport::start).isNotNull()
            prop(CampaignReport::end).isNotNull()
            prop(CampaignReport::status).isEqualTo(ExecutionStatus.ABORTED)
        }
    }

    @Test
    @Timeout(1)
    internal fun `should generate a report for all the scenarios of the campaign as successful when there are info only`() =
        testDispatcherProvider.run {
            // given
            val campaignStateKeeper =
                StandaloneCampaignReportStateKeeperImpl(
                    idGenerator,
                    consoleReporter,
                    Duration.ofSeconds(5),
                    taskScheduler,
                    mockk {
                        every { get() } returns campaignService
                    },
                    zoneService,
                    campaignMeterEnricher
                )
            campaignStateKeeper.start("the campaign", "the scenario 1")
            campaignStateKeeper.start("the campaign", "the scenario 2")
            campaignStateKeeper.complete("the campaign", "the scenario 1")
            campaignStateKeeper.complete("the campaign", "the scenario 2")
            val states = campaignStateKeeper.campaignStates().get("the campaign")!!
            val state1 = states["the scenario 1"]!!
            val state2 = states["the scenario 2"]!!

            state1.end = Instant.now()
            state1.startedMinionsCounter.addAndGet(1231)
            state1.completedMinionsCounter.addAndGet(234)
            state1.successfulStepExecutionsCounter.addAndGet(7643)
            state1.keyedMessages["the id 1"] =
                ReportMessage("step 1", "the id 1", ReportMessageSeverity.INFO, " The message 1")
            state1.keyedMessages["the id 2"] =
                ReportMessage("step 2", "the id 2", ReportMessageSeverity.INFO, " The message 2")

            state2.end = Instant.now().minusSeconds(123)
            state2.startedMinionsCounter.addAndGet(765)
            state2.completedMinionsCounter.addAndGet(345)
            state2.successfulStepExecutionsCounter.addAndGet(854)
            state2.keyedMessages["the id 3"] =
                ReportMessage("step 1", "the id 3", ReportMessageSeverity.INFO, " The message 3")
            state2.keyedMessages["the id 4"] =
                ReportMessage("step 2", "the id 4", ReportMessageSeverity.INFO, " The message 4")
            campaignStateKeeper.complete("the campaign")

            // when
            val report = campaignStateKeeper.generateReport("the campaign")

            // then
            assertThat(report).isNotNull().all {
                prop(CampaignReport::campaignKey).isEqualTo("the campaign")
                prop(CampaignReport::start).isEqualTo(state1.start)
                prop(CampaignReport::end).isEqualTo(state1.end)
                prop(CampaignReport::status).isEqualTo(ExecutionStatus.SUCCESSFUL)
                prop(CampaignReport::startedMinions).isEqualTo(1231 + 765)
                prop(CampaignReport::completedMinions).isEqualTo(234 + 345)
                prop(CampaignReport::successfulExecutions).isEqualTo(7643 + 854)
                prop(CampaignReport::failedExecutions).isEqualTo(0)
                prop(CampaignReport::scenariosReports).hasSize(2)
            }
        }

    @Test
    @Timeout(1)
    internal fun `should generate a report for all the scenarios of the campaign as warning when there are warning`() =
        testDispatcherProvider.run {
            // given
            val campaignStateKeeper =
                StandaloneCampaignReportStateKeeperImpl(
                    idGenerator,
                    consoleReporter,
                    Duration.ofSeconds(5),
                    taskScheduler,
                    mockk {
                        every { get() } returns campaignService
                    },
                    zoneService,
                    campaignMeterEnricher
                )
            campaignStateKeeper.start("the campaign", "the scenario 1")
            campaignStateKeeper.start("the campaign", "the scenario 2")
            campaignStateKeeper.complete("the campaign", "the scenario 1")
            campaignStateKeeper.complete("the campaign", "the scenario 2")
            val states = campaignStateKeeper.campaignStates().get("the campaign")!!
            val state1 = states["the scenario 1"]!!
            val state2 = states["the scenario 2"]!!

            state1.end = Instant.now()
            state1.startedMinionsCounter.addAndGet(1231)
            state1.completedMinionsCounter.addAndGet(234)
            state1.successfulStepExecutionsCounter.addAndGet(7643)
            state1.keyedMessages["the id 1"] =
                ReportMessage("step 1", "the id 1", ReportMessageSeverity.INFO, " The message 1")
            state1.keyedMessages["the id 2"] =
                ReportMessage("step 2", "the id 2", ReportMessageSeverity.WARN, " The message 2")

            state2.end = Instant.now().minusSeconds(123)
            state2.startedMinionsCounter.addAndGet(765)
            state2.completedMinionsCounter.addAndGet(345)
            state2.successfulStepExecutionsCounter.addAndGet(854)
            state2.keyedMessages["the id 3"] =
                ReportMessage("step 1", "the id 3", ReportMessageSeverity.INFO, " The message 3")
            state2.keyedMessages["the id 4"] =
                ReportMessage("step 2", "the id 4", ReportMessageSeverity.WARN, " The message 4")
            campaignStateKeeper.complete("the campaign")

            // when
            val report = campaignStateKeeper.generateReport("the campaign")

            // then
            assertThat(report).isNotNull().all {
                prop(CampaignReport::campaignKey).isEqualTo("the campaign")
                prop(CampaignReport::start).isEqualTo(state1.start)
                prop(CampaignReport::end).isEqualTo(state1.end)
                prop(CampaignReport::status).isEqualTo(ExecutionStatus.WARNING)
                prop(CampaignReport::startedMinions).isEqualTo(1231 + 765)
                prop(CampaignReport::completedMinions).isEqualTo(234 + 345)
                prop(CampaignReport::successfulExecutions).isEqualTo(7643 + 854)
                prop(CampaignReport::failedExecutions).isEqualTo(0)
                prop(CampaignReport::scenariosReports).hasSize(2)
            }
        }

    @Test
    @Timeout(1)
    internal fun `should generate a report for all the scenarios of the campaign as error when there is one error`() =
        testDispatcherProvider.run {
            // given
            val campaignStateKeeper = StandaloneCampaignReportStateKeeperImpl(
                idGenerator,
                consoleReporter,
                Duration.ofSeconds(5),
                taskScheduler,
                mockk {
                    every { get() } returns campaignService
                },
                zoneService,
                campaignMeterEnricher
            )
            campaignStateKeeper.start("the campaign", "the scenario 1")
            campaignStateKeeper.start("the campaign", "the scenario 2")
            campaignStateKeeper.complete("the campaign", "the scenario 1")
            campaignStateKeeper.complete("the campaign", "the scenario 2")
            val states = campaignStateKeeper.campaignStates().get("the campaign")!!
            val state1 = states["the scenario 1"]!!
            val state2 = states["the scenario 2"]!!

            state1.end = Instant.now().minusSeconds(123)
            state1.startedMinionsCounter.addAndGet(1231)
            state1.completedMinionsCounter.addAndGet(234)
            state1.successfulStepExecutionsCounter.addAndGet(7643)
            state1.failedStepExecutionsCounter.addAndGet(2345)
            state1.keyedMessages["the id 1"] =
                ReportMessage("step 1", "the id 1", ReportMessageSeverity.INFO, " The message 1")
            state1.keyedMessages["the id 2"] =
                ReportMessage("step 2", "the id 2", ReportMessageSeverity.WARN, " The message 2")

            state2.end = Instant.now()
            state2.startedMinionsCounter.addAndGet(765)
            state2.completedMinionsCounter.addAndGet(345)
            state2.successfulStepExecutionsCounter.addAndGet(854)
            state2.failedStepExecutionsCounter.addAndGet(3567)
            state2.keyedMessages["the id 3"] =
                ReportMessage("step 1", "the id 3", ReportMessageSeverity.ERROR, " The message 3")
            state2.keyedMessages["the id 4"] =
                ReportMessage("step 2", "the id 4", ReportMessageSeverity.WARN, " The message 4")
            campaignStateKeeper.complete("the campaign")

            // when
            val report = campaignStateKeeper.generateReport("the campaign")

            // then
            assertThat(report).isNotNull().all {
                prop(CampaignReport::campaignKey).isEqualTo("the campaign")
                prop(CampaignReport::start).isEqualTo(state1.start)
                prop(CampaignReport::end).isEqualTo(state2.end)
                prop(CampaignReport::status).isEqualTo(ExecutionStatus.FAILED)
                prop(CampaignReport::startedMinions).isEqualTo(1231 + 765)
                prop(CampaignReport::completedMinions).isEqualTo(234 + 345)
                prop(CampaignReport::successfulExecutions).isEqualTo(7643 + 854)
                prop(CampaignReport::failedExecutions).isEqualTo(2345 + 3567)
                prop(CampaignReport::scenariosReports).hasSize(2)
            }
        }

    @ParameterizedTest
    @CsvSource(
        textBlock = """SUCCESSFUL,,SUCCESSFUL
        WARNING,There are warnings,SUCCESSFUL
        FAILED,The campaign failed,FAILED
        ABORTED,The campaign succeeded,ABORTED""",
        nullValues = [""]
    )
    @Timeout(2)
    internal fun `should generate a report using the status provided at completion when it is a failure`(
        completionStatus: ExecutionStatus,
        completionMessage: String?,
        expectedCampaignStatus: ExecutionStatus
    ) =
        testDispatcherProvider.run {
            // given
            val campaignStateKeeper =
                StandaloneCampaignReportStateKeeperImpl(
                    idGenerator,
                    consoleReporter,
                    Duration.ofSeconds(5),
                    taskScheduler,
                    mockk {
                        every { get() } returns campaignService
                    },
                    zoneService,
                    campaignMeterEnricher
                )
            campaignStateKeeper.start("the campaign", "the scenario 1")
            campaignStateKeeper.start("the campaign", "the scenario 2")
            campaignStateKeeper.complete("the campaign", "the scenario 1")
            campaignStateKeeper.complete("the campaign", "the scenario 2")
            val states = campaignStateKeeper.campaignStates()["the campaign"]!!
            val state1 = states["the scenario 1"]!!
            val state2 = states["the scenario 2"]!!

            state1.end = Instant.now().minusSeconds(123)
            state1.startedMinionsCounter.addAndGet(1231)
            state1.completedMinionsCounter.addAndGet(234)
            state1.successfulStepExecutionsCounter.addAndGet(7643)
            state1.failedStepExecutionsCounter.addAndGet(0)
            state1.keyedMessages["the id 1"] =
                ReportMessage("step 1", "the id 1", ReportMessageSeverity.INFO, " The message 1")
            state1.keyedMessages["the id 2"] =
                ReportMessage("step 2", "the id 2", ReportMessageSeverity.INFO, " The message 2")

            state2.end = Instant.now()
            state2.startedMinionsCounter.addAndGet(765)
            state2.completedMinionsCounter.addAndGet(345)
            state2.successfulStepExecutionsCounter.addAndGet(854)
            state2.keyedMessages["the id 3"] =
                ReportMessage("step 1", "the id 3", ReportMessageSeverity.INFO, " The message 3")
            state2.keyedMessages["the id 4"] =
                ReportMessage("step 2", "the id 4", ReportMessageSeverity.INFO, " The message 4")
            campaignStateKeeper.complete("the campaign", completionStatus, completionMessage)

            // when
            val report = campaignStateKeeper.generateReport("the campaign")

            // then
            assertThat(report).isNotNull().all {
                prop(CampaignReport::campaignKey).isEqualTo("the campaign")
                prop(CampaignReport::start).isEqualTo(state1.start)
                prop(CampaignReport::end).isEqualTo(state2.end)
                prop(CampaignReport::status).isEqualTo(expectedCampaignStatus)
                prop(CampaignReport::startedMinions).isEqualTo(1231 + 765)
                prop(CampaignReport::completedMinions).isEqualTo(234 + 345)
                prop(CampaignReport::successfulExecutions).isEqualTo(7643 + 854)
                prop(CampaignReport::failedExecutions).isEqualTo(0)
                prop(CampaignReport::scenariosReports).hasSize(2)
            }
        }

    @Test
    @Timeout(1)
    internal fun `should retrieve the report details for a unique campaign`() = testDispatcherProvider.run {
        // given
        val campaignReportProvider =
            StandaloneCampaignReportStateKeeperImpl(
                idGenerator,
                consoleReporter,
                Duration.ofSeconds(5),
                taskScheduler,
                mockk {
                    every { get() } returns campaignService
                },
                zoneService,
                campaignMeterEnricher
            )
        campaignReportProvider.start("key-1", "the scenario 1")
        campaignReportProvider.start("key-1", "the scenario 2")
        campaignReportProvider.complete("key-1", "the scenario 1")
        campaignReportProvider.complete("key-1", "the scenario 2")
        val states1 = campaignReportProvider.campaignStates()["key-1"]!!
        val state11 = states1["the scenario 1"]!!
        val state12 = states1["the scenario 2"]!!

        state11.end = Instant.now().minusSeconds(123)
        state11.startedMinionsCounter.addAndGet(1231)
        state11.completedMinionsCounter.addAndGet(234)
        state11.successfulStepExecutionsCounter.addAndGet(7643)
        state11.failedStepExecutionsCounter.addAndGet(2345)
        state11.keyedMessages["the id 1"] =
            ReportMessage("step 1", "the id 1", ReportMessageSeverity.ABORT, " The message 1")
        state11.keyedMessages["the id 2"] =
            ReportMessage("step 2", "the id 2", ReportMessageSeverity.WARN, " The message 2")

        state12.end = Instant.now()
        state12.startedMinionsCounter.addAndGet(765)
        state12.completedMinionsCounter.addAndGet(345)
        state12.successfulStepExecutionsCounter.addAndGet(854)
        state12.failedStepExecutionsCounter.addAndGet(3567)
        state12.keyedMessages["the id 3"] =
            ReportMessage("step 1", "the id 3", ReportMessageSeverity.ERROR, " The message 3")
        state12.keyedMessages["the id 4"] =
            ReportMessage("step 2", "the id 4", ReportMessageSeverity.WARN, " The message 4")
        campaignReportProvider.complete("key-1")

        coEvery { campaignService.retrieveConfiguration(any(), any()) } throws RuntimeException("no config")
        coEvery { campaignService.retrieve(any(), any()) } throws RuntimeException("no campaign")
        coEvery { zoneService.resolve(any(), any()) } returns emptyList()
        coEvery { campaignMeterEnricher.distribute(any(), any<Collection<String>>(), any()) } returns emptyMap()

        // when
        val reports = campaignReportProvider.retrieveCampaignsReports("my-tenant", listOf("key-1"))

        // then
        assertThat(reports.toList()).all {
            hasSize(1)
            index(0).all {
                prop(CampaignExecutionDetails::key).isEqualTo("key-1")
                prop(CampaignExecutionDetails::name).isEqualTo("key-1")
                prop(CampaignExecutionDetails::start).isEqualTo(state11.start)
                prop(CampaignExecutionDetails::end).isEqualTo(state12.end)
                prop(CampaignExecutionDetails::status).isEqualTo(ExecutionStatus.ABORTED)
                prop(CampaignExecutionDetails::startedMinions).isEqualTo(1231 + 765)
                prop(CampaignExecutionDetails::completedMinions).isEqualTo(234 + 345)
                prop(CampaignExecutionDetails::successfulExecutions).isEqualTo(7643L + 854L)
                prop(CampaignExecutionDetails::failedExecutions).isEqualTo(2345L + 3567L)
                prop(CampaignExecutionDetails::scenarios).all {
                    hasSize(2)
                    index(0).all {
                        prop(ScenarioExecutionDetails::messages).hasSize(2)
                    }
                    index(1).all {
                        prop(ScenarioExecutionDetails::messages).hasSize(2)
                    }
                }
            }
        }
    }

    @Test
    @Timeout(1)
    internal fun `should retrieve the report details for a collection of campaigns`() = testDispatcherProvider.run {
        // given
        val campaignReportProvider =
            StandaloneCampaignReportStateKeeperImpl(
                idGenerator,
                consoleReporter,
                Duration.ofSeconds(5),
                taskScheduler,
                mockk {
                    every { get() } returns campaignService
                },
                zoneService,
                campaignMeterEnricher
            )
        campaignReportProvider.start("key-1", "the scenario 1")
        campaignReportProvider.start("key-1", "the scenario 2")
        campaignReportProvider.start("key-2", "the scenario 1")
        campaignReportProvider.start("key-3", "the scenario 2")
        campaignReportProvider.complete("key-1", "the scenario 1")
        campaignReportProvider.complete("key-1", "the scenario 2")
        campaignReportProvider.complete("key-2", "the scenario 1")
        campaignReportProvider.complete("key-3", "the scenario 2")
        val states1 = campaignReportProvider.campaignStates()["key-1"]!!
        val state11 = states1["the scenario 1"]!!
        val state12 = states1["the scenario 2"]!!
        val states2 = campaignReportProvider.campaignStates()["key-2"]!!
        val state21 = states2["the scenario 1"]!!
        val states3 = campaignReportProvider.campaignStates()["key-3"]!!
        val state31 = states3["the scenario 2"]!!

        state11.end = Instant.now().minusSeconds(123)
        state11.startedMinionsCounter.addAndGet(1231)
        state11.completedMinionsCounter.addAndGet(234)
        state11.successfulStepExecutionsCounter.addAndGet(7643)
        state11.failedStepExecutionsCounter.addAndGet(2345)
        state11.keyedMessages["the id 1"] =
            ReportMessage("step 1", "the id 1", ReportMessageSeverity.ABORT, " The message 1")
        state11.keyedMessages["the id 2"] =
            ReportMessage("step 2", "the id 2", ReportMessageSeverity.WARN, " The message 2")

        state12.end = Instant.now()
        state12.startedMinionsCounter.addAndGet(765)
        state12.completedMinionsCounter.addAndGet(345)
        state12.successfulStepExecutionsCounter.addAndGet(854)
        state12.failedStepExecutionsCounter.addAndGet(3567)
        state12.keyedMessages["the id 3"] =
            ReportMessage("step 1", "the id 3", ReportMessageSeverity.ERROR, " The message 3")
        state12.keyedMessages["the id 4"] =
            ReportMessage("step 2", "the id 4", ReportMessageSeverity.WARN, " The message 4")
        campaignReportProvider.complete("key-1")

        state21.end = Instant.now()
        state21.startedMinionsCounter.addAndGet(765)
        state21.completedMinionsCounter.addAndGet(345)
        state21.successfulStepExecutionsCounter.addAndGet(854)
        state21.failedStepExecutionsCounter.addAndGet(3567)
        state21.keyedMessages["the id 5"] =
            ReportMessage("step 1", "the id 5", ReportMessageSeverity.ABORT, " The message 5")
        state21.keyedMessages["the id 6"] =
            ReportMessage("step 2", "the id 6", ReportMessageSeverity.ERROR, " The message 6")
        campaignReportProvider.complete("key-2")

        state31.end = Instant.now()
        state31.startedMinionsCounter.addAndGet(765)
        state31.completedMinionsCounter.addAndGet(345)
        state31.successfulStepExecutionsCounter.addAndGet(854)
        state31.failedStepExecutionsCounter.addAndGet(3567)
        state31.keyedMessages["the id 7"] =
            ReportMessage("step 1", "the id 7", ReportMessageSeverity.INFO, " The message 7")
        state31.keyedMessages["the id 8"] =
            ReportMessage("step 2", "the id 8", ReportMessageSeverity.ABORT, " The message 8")
        campaignReportProvider.complete("key-3")

        coEvery { campaignService.retrieveConfiguration(any(), any()) } throws RuntimeException("no config")
        coEvery { campaignService.retrieve(any(), any()) } throws RuntimeException("no campaign")
        coEvery { zoneService.resolve(any(), any()) } returns emptyList()
        coEvery { campaignMeterEnricher.distribute(any(), any<Collection<String>>(), any()) } returns emptyMap()

        // when
        val reports =
            campaignReportProvider.retrieveCampaignsReports("my-tenant", listOf("key-1", "key-3", "key-2"))

        // then
        assertThat(reports.toList()).all {
            hasSize(3)
            index(0).all {
                prop(CampaignExecutionDetails::key).isEqualTo("key-1")
                prop(CampaignExecutionDetails::name).isEqualTo("key-1")
                prop(CampaignExecutionDetails::start).isEqualTo(state11.start)
                prop(CampaignExecutionDetails::end).isEqualTo(state12.end)
                prop(CampaignExecutionDetails::status).isEqualTo(ExecutionStatus.ABORTED)
                prop(CampaignExecutionDetails::startedMinions).isEqualTo(1231 + 765)
                prop(CampaignExecutionDetails::completedMinions).isEqualTo(234 + 345)
                prop(CampaignExecutionDetails::successfulExecutions).isEqualTo(7643L + 854L)
                prop(CampaignExecutionDetails::failedExecutions).isEqualTo(2345L + 3567L)
                prop(CampaignExecutionDetails::scenarios).all {
                    hasSize(2)
                    index(0).all {
                        prop(ScenarioExecutionDetails::messages).hasSize(2)
                    }
                    index(1).all {
                        prop(ScenarioExecutionDetails::messages).hasSize(2)
                    }
                }
            }
            index(1).all {
                prop(CampaignExecutionDetails::key).isEqualTo("key-3")
                prop(CampaignExecutionDetails::name).isEqualTo("key-3")
                prop(CampaignExecutionDetails::start).isEqualTo(state31.start)
                prop(CampaignExecutionDetails::end).isEqualTo(state31.end)
                prop(CampaignExecutionDetails::status).isEqualTo(ExecutionStatus.ABORTED)
                prop(CampaignExecutionDetails::startedMinions).isEqualTo(765)
                prop(CampaignExecutionDetails::completedMinions).isEqualTo(345)
                prop(CampaignExecutionDetails::successfulExecutions).isEqualTo(854L)
                prop(CampaignExecutionDetails::failedExecutions).isEqualTo(3567L)
                prop(CampaignExecutionDetails::scenarios).all {
                    hasSize(1)
                    index(0).all {
                        prop(ScenarioExecutionDetails::messages).hasSize(2)
                    }
                }
            }
            index(2).all {
                prop(CampaignExecutionDetails::key).isEqualTo("key-2")
                prop(CampaignExecutionDetails::name).isEqualTo("key-2")
                prop(CampaignExecutionDetails::start).isEqualTo(state21.start)
                prop(CampaignExecutionDetails::end).isEqualTo(state21.end)
                prop(CampaignExecutionDetails::status).isEqualTo(ExecutionStatus.ABORTED)
                prop(CampaignExecutionDetails::startedMinions).isEqualTo(765)
                prop(CampaignExecutionDetails::completedMinions).isEqualTo(345)
                prop(CampaignExecutionDetails::successfulExecutions).isEqualTo(854L)
                prop(CampaignExecutionDetails::failedExecutions).isEqualTo(3567L)
                prop(CampaignExecutionDetails::scenarios).all {
                    hasSize(1)
                    index(0).all {
                        prop(ScenarioExecutionDetails::messages).hasSize(2)
                    }
                }
            }
        }
    }

    @Test
    @Timeout(1)
    internal fun `should retrieve the report details when a campaign key is missing`() = testDispatcherProvider.run {
        // given
        val campaignReportProvider =
            StandaloneCampaignReportStateKeeperImpl(
                idGenerator,
                null,
                Duration.ofSeconds(5),
                taskScheduler,
                mockk {
                    every { get() } returns campaignService
                },
                zoneService,
                campaignMeterEnricher
            )
        campaignReportProvider.start("key-1", "the scenario 1")
        campaignReportProvider.start("key-1", "the scenario 2")
        campaignReportProvider.start("key-3", "the scenario 2")
        campaignReportProvider.complete("key-1", "the scenario 1")
        campaignReportProvider.complete("key-1", "the scenario 2")
        campaignReportProvider.complete("key-3", "the scenario 2")
        val states1 = campaignReportProvider.campaignStates()["key-1"]!!
        val state11 = states1["the scenario 1"]!!
        val state12 = states1["the scenario 2"]!!
        val states3 = campaignReportProvider.campaignStates()["key-3"]!!
        val state31 = states3["the scenario 2"]!!

        state11.end = Instant.now().minusSeconds(123)
        state11.startedMinionsCounter.addAndGet(1231)
        state11.completedMinionsCounter.addAndGet(234)
        state11.successfulStepExecutionsCounter.addAndGet(7643)
        state11.failedStepExecutionsCounter.addAndGet(2345)
        state11.keyedMessages["the id 1"] =
            ReportMessage("step 1", "the id 1", ReportMessageSeverity.ABORT, " The message 1")
        state11.keyedMessages["the id 2"] =
            ReportMessage("step 2", "the id 2", ReportMessageSeverity.WARN, " The message 2")

        state12.end = Instant.now()
        state12.startedMinionsCounter.addAndGet(765)
        state12.completedMinionsCounter.addAndGet(345)
        state12.successfulStepExecutionsCounter.addAndGet(854)
        state12.failedStepExecutionsCounter.addAndGet(3567)
        state12.keyedMessages["the id 3"] =
            ReportMessage("step 1", "the id 3", ReportMessageSeverity.ERROR, " The message 3")
        state12.keyedMessages["the id 4"] =
            ReportMessage("step 2", "the id 4", ReportMessageSeverity.WARN, " The message 4")
        campaignReportProvider.complete("key-1")

        state31.end = Instant.now()
        state31.startedMinionsCounter.addAndGet(765)
        state31.completedMinionsCounter.addAndGet(345)
        state31.successfulStepExecutionsCounter.addAndGet(854)
        state31.failedStepExecutionsCounter.addAndGet(3567)
        state31.keyedMessages["the id 7"] =
            ReportMessage("step 1", "the id 7", ReportMessageSeverity.INFO, " The message 7")
        state31.keyedMessages["the id 8"] =
            ReportMessage("step 2", "the id 8", ReportMessageSeverity.ABORT, " The message 8")
        campaignReportProvider.complete("key-3")

        coEvery { campaignService.retrieveConfiguration(any(), any()) } throws RuntimeException("no config")
        coEvery { campaignService.retrieve(any(), any()) } throws RuntimeException("no campaign")
        coEvery { zoneService.resolve(any(), any()) } returns emptyList()
        coEvery { campaignMeterEnricher.distribute(any(), any<Collection<String>>(), any()) } returns emptyMap()

        // when
        val report = campaignReportProvider.retrieveCampaignsReports("my-tenant", listOf("keys-1", "key-3", "key-1"))

        // then
        assertThat(report.toList()).all {
            hasSize(2)
            index(0).all {
                prop(CampaignExecutionDetails::key).isEqualTo("key-3")
                prop(CampaignExecutionDetails::name).isEqualTo("key-3")
                prop(CampaignExecutionDetails::start).isEqualTo(state31.start)
                prop(CampaignExecutionDetails::end).isEqualTo(state31.end)
                prop(CampaignExecutionDetails::status).isEqualTo(ExecutionStatus.ABORTED)
                prop(CampaignExecutionDetails::startedMinions).isEqualTo(765)
                prop(CampaignExecutionDetails::completedMinions).isEqualTo(345)
                prop(CampaignExecutionDetails::successfulExecutions).isEqualTo(854L)
                prop(CampaignExecutionDetails::failedExecutions).isEqualTo(3567L)
                prop(CampaignExecutionDetails::scenarios).all {
                    hasSize(1)
                    index(0).all {
                        prop(ScenarioExecutionDetails::messages).hasSize(2)
                    }
                }
            }
            index(1).all {
                prop(CampaignExecutionDetails::key).isEqualTo("key-1")
                prop(CampaignExecutionDetails::name).isEqualTo("key-1")
                prop(CampaignExecutionDetails::start).isEqualTo(state11.start)
                prop(CampaignExecutionDetails::end).isEqualTo(state12.end)
                prop(CampaignExecutionDetails::status).isEqualTo(ExecutionStatus.ABORTED)
                prop(CampaignExecutionDetails::startedMinions).isEqualTo(1231 + 765)
                prop(CampaignExecutionDetails::completedMinions).isEqualTo(234 + 345)
                prop(CampaignExecutionDetails::successfulExecutions).isEqualTo(7643L + 854L)
                prop(CampaignExecutionDetails::failedExecutions).isEqualTo(2345L + 3567L)
                prop(CampaignExecutionDetails::scenarios).all {
                    hasSize(2)
                    index(0).all {
                        prop(ScenarioExecutionDetails::messages).hasSize(2)
                    }
                    index(1).all {
                        prop(ScenarioExecutionDetails::messages).hasSize(2)
                    }
                }
            }
        }
    }

    @Test
    @Timeout(1)
    internal fun `should retrieve the report details when all campaign keys are missing`() =
        testDispatcherProvider.run {
            // given
            val campaignReportProvider =
                StandaloneCampaignReportStateKeeperImpl(
                    idGenerator,
                    null,
                    Duration.ofSeconds(5),
                    taskScheduler,
                    mockk {
                        every { get() } returns campaignService
                    },
                    zoneService,
                    campaignMeterEnricher
                )

            // when
            val report =
                campaignReportProvider.retrieveCampaignsReports("my-tenant", listOf("keys-1", "campaign-1", "my-key"))

            // then
            assertThat(report.toList()).isEmpty()
        }

    // -------------------------------------------------------------------------
    // retrieve() — tests migrated from InMemoryCampaignExecutionDetailsServiceTest
    // -------------------------------------------------------------------------

    private fun emptyDistribution(): MeterDistribution = MeterDistribution(
        campaignMeters = emptyList(),
        byScenario = emptyMap(),
        byScenarioAndStep = emptyMap()
    )

    private fun buildScenarioReport(
        scenarioName: String = "sc-1",
        messages: List<ReportMessage> = emptyList(),
        steps: List<StepReport> = emptyList()
    ): ScenarioReport = ScenarioReport(
        campaignKey = "camp-1",
        scenarioName = scenarioName,
        start = start,
        end = end,
        startedMinions = 5,
        completedMinions = 4,
        successfulExecutions = 4,
        failedExecutions = 0,
        status = ExecutionStatus.SUCCESSFUL,
        messages = messages,
        steps = steps
    )

    @Test
    internal fun `should throw an error when no in-memory campaign report is found`() =
        testDispatcherProvider.runTest {
            // given
            val keeper = StandaloneCampaignReportStateKeeperImpl(
                idGenerator, consoleReporter, Duration.ofSeconds(5), taskScheduler,
                mockk {
                    every { get() } returns campaignService
                }, zoneService, campaignMeterEnricher
            )

            // when / then
            assertThrows<IllegalStateException> {
                keeper.retrieve("my-tenant", "camp-missing")
            }
        }

    @Test
    internal fun `should assemble CampaignExecutionDetails from the in-memory report`() =
        testDispatcherProvider.runTest {
            // given
            val keeper = StandaloneCampaignReportStateKeeperImpl(
                idGenerator, consoleReporter, Duration.ofSeconds(5), taskScheduler,
                mockk {
                    every { get() } returns campaignService
                }, zoneService, campaignMeterEnricher
            )
            keeper.start("camp-1", "sc-1")
            keeper.complete("camp-1", "sc-1")
            keeper.complete("camp-1")
            coEvery {
                campaignService.retrieveConfiguration("my-tenant", "camp-1")
            } throws RuntimeException("no config")
            coEvery { zoneService.resolve("my-tenant", emptySet()) } returns emptyList()
            coEvery {
                campaignMeterEnricher.distribute("my-tenant", listOf("camp-1"), listOf("sc-1"))
            } returns mapOf("camp-1" to emptyDistribution())
            coEvery { campaignService.retrieve("my-tenant", "camp-1") } returns Campaign(
                version = now,
                key = "camp-1",
                creation = start,
                name = "My Campaign",
                speedFactor = 1.0,
                scheduledMinions = 10,
                start = start,
                end = end,
                status = ExecutionStatus.SUCCESSFUL,
                configurerName = "user-1",
                configuredScenarios = emptyList()
            )

            // when
            val result = keeper.retrieve("my-tenant", "camp-1")

            // then
            assertThat(result.key).isEqualTo("camp-1")
            assertThat(result.name).isEqualTo("My Campaign")
            assertThat(result.status).isEqualTo(ExecutionStatus.SUCCESSFUL)
            coVerify { campaignService.retrieveConfiguration("my-tenant", "camp-1") }
            coVerify { campaignService.retrieve("my-tenant", "camp-1") }
            coVerify { zoneService.resolve("my-tenant", emptySet()) }
            coVerify { campaignMeterEnricher.distribute("my-tenant", listOf("camp-1"), listOf("sc-1")) }
            confirmVerified(campaignService, zoneService, campaignMeterEnricher)
        }

    @Test
    internal fun `should populate zone distribution from campaign configuration per scenario`() =
        testDispatcherProvider.runTest {
            // given
            val keeper = StandaloneCampaignReportStateKeeperImpl(
                idGenerator = idGenerator,
                consoleCampaignProgressionReporter = consoleReporter,
                cacheExpire = Duration.ofSeconds(5),
                taskScheduler = taskScheduler,
                campaignService = mockk {
                    every { get() } returns campaignService
                },
                zoneService = zoneService,
                campaignMeterEnricher = campaignMeterEnricher
            )
            val campaignConfig = CampaignConfiguration(
                name = "My Campaign",
                scenarios = mapOf(
                    "sc-1" to ScenarioRequest(minionsCount = 10, zones = mapOf("fr" to 70, "de" to 30))
                )
            )
            keeper.start("camp-1", "sc-1")
            keeper.complete("camp-1", "sc-1")
            keeper.complete("camp-1")
            coEvery { campaignService.retrieveConfiguration("my-tenant", "camp-1") } returns campaignConfig
            coEvery {
                zoneService.resolve("my-tenant", setOf("fr", "de"))
            } returns emptyList()
            coEvery {
                campaignMeterEnricher.distribute("my-tenant", listOf("camp-1"), listOf("sc-1"))
            } returns mapOf("camp-1" to emptyDistribution())
            coEvery { campaignService.retrieve("my-tenant", "camp-1") } returns Campaign(
                version = now,
                key = "camp-1",
                creation = start,
                name = "My Campaign",
                speedFactor = 1.0,
                scheduledMinions = null,
                start = start,
                end = end,
                status = ExecutionStatus.SUCCESSFUL,
                configurerName = null,
                configuredScenarios = emptyList()
            )

            // when
            val result = keeper.retrieve("my-tenant", "camp-1")

            // then
            assertThat(result.scenarios[0].zoneDistribution).isEqualTo(mapOf("fr" to 70, "de" to 30))
            coVerify { campaignService.retrieveConfiguration("my-tenant", "camp-1") }
            coVerify { campaignService.retrieve("my-tenant", "camp-1") }
            coVerify { zoneService.resolve("my-tenant", setOf("fr", "de")) }
            coVerify { campaignMeterEnricher.distribute("my-tenant", listOf("camp-1"), listOf("sc-1")) }
            confirmVerified(campaignService, zoneService, campaignMeterEnricher)
        }

    @Test
    internal fun `should default zone distribution to empty map when no campaign configuration exists`() =
        testDispatcherProvider.runTest {
            // given
            val keeper = StandaloneCampaignReportStateKeeperImpl(
                idGenerator, consoleReporter, Duration.ofSeconds(5), taskScheduler,
                mockk {
                    every { get() } returns campaignService
                }, zoneService, campaignMeterEnricher
            )
            keeper.start("camp-1", "sc-1")
            keeper.complete("camp-1", "sc-1")
            keeper.complete("camp-1")
            coEvery {
                campaignService.retrieveConfiguration("my-tenant", "camp-1")
            } throws RuntimeException("no config")
            coEvery { zoneService.resolve("my-tenant", emptySet()) } returns emptyList()
            coEvery {
                campaignMeterEnricher.distribute("my-tenant", listOf("camp-1"), listOf("sc-1"))
            } returns mapOf("camp-1" to emptyDistribution())
            coEvery { campaignService.retrieve("my-tenant", "camp-1") } returns Campaign(
                version = now,
                key = "camp-1",
                creation = start,
                name = "My Campaign",
                speedFactor = 1.0,
                scheduledMinions = null,
                start = start,
                end = end,
                status = ExecutionStatus.SUCCESSFUL,
                configurerName = null,
                configuredScenarios = emptyList()
            )

            // when
            val result = keeper.retrieve("my-tenant", "camp-1")

            // then
            assertThat(result.scenarios[0].zoneDistribution).isEqualTo(emptyMap())
            coVerify { campaignService.retrieveConfiguration("my-tenant", "camp-1") }
            coVerify { campaignService.retrieve("my-tenant", "camp-1") }
            coVerify { zoneService.resolve("my-tenant", emptySet()) }
            coVerify { campaignMeterEnricher.distribute("my-tenant", listOf("camp-1"), listOf("sc-1")) }
            confirmVerified(campaignService, zoneService, campaignMeterEnricher)
        }

    @Test
    internal fun `should use campaign name fetched from campaign service`() = testDispatcherProvider.runTest {
        // given
        val keeper = StandaloneCampaignReportStateKeeperImpl(
            idGenerator = idGenerator,
            consoleCampaignProgressionReporter = consoleReporter,
            cacheExpire = Duration.ofSeconds(5),
            taskScheduler = taskScheduler,
            campaignService = mockk {
                every { get() } returns campaignService
            },
            zoneService = zoneService,
            campaignMeterEnricher = campaignMeterEnricher
        )
        keeper.start("camp-1", "sc-1")
        keeper.complete("camp-1", "sc-1")
        keeper.complete("camp-1")
        coEvery {
            campaignService.retrieveConfiguration("my-tenant", "camp-1")
        } throws RuntimeException("no config")
        coEvery { zoneService.resolve("my-tenant", emptySet()) } returns emptyList()
        coEvery {
            campaignMeterEnricher.distribute("my-tenant", listOf("camp-1"), listOf("sc-1"))
        } returns mapOf("camp-1" to emptyDistribution())
        coEvery { campaignService.retrieve("my-tenant", "camp-1") } returns Campaign(
            version = now,
            key = "camp-1",
            creation = start,
            name = "Fetched Campaign Name",
            speedFactor = 1.0,
            scheduledMinions = null,
            start = start,
            end = end,
            status = ExecutionStatus.SUCCESSFUL,
            configurerName = null,
            configuredScenarios = emptyList()
        )

        // when
        val result = keeper.retrieve("my-tenant", "camp-1")

        // then
        assertThat(result.name).isEqualTo("Fetched Campaign Name")
        coVerify { campaignService.retrieveConfiguration("my-tenant", "camp-1") }
        coVerify { campaignService.retrieve("my-tenant", "camp-1") }
        coVerify { zoneService.resolve("my-tenant", emptySet()) }
        coVerify { campaignMeterEnricher.distribute("my-tenant", listOf("camp-1"), listOf("sc-1")) }
        confirmVerified(campaignService, zoneService, campaignMeterEnricher)
    }

    @Test
    internal fun `should fall back to campaign key as name when campaign service throws`() =
        testDispatcherProvider.runTest {
            // given
            val keeper = StandaloneCampaignReportStateKeeperImpl(
                idGenerator, consoleReporter, Duration.ofSeconds(5), taskScheduler,
                mockk {
                    every { get() } returns campaignService
                }, zoneService, campaignMeterEnricher
            )
            keeper.start("camp-1", "sc-1")
            keeper.complete("camp-1", "sc-1")
            keeper.complete("camp-1")
            coEvery {
                campaignService.retrieveConfiguration("my-tenant", "camp-1")
            } throws RuntimeException("no config")
            coEvery { zoneService.resolve("my-tenant", emptySet()) } returns emptyList()
            coEvery {
                campaignMeterEnricher.distribute("my-tenant", listOf("camp-1"), listOf("sc-1"))
            } returns mapOf("camp-1" to emptyDistribution())
            coEvery { campaignService.retrieve("my-tenant", "camp-1") } throws RuntimeException("not found")

            // when
            val result = keeper.retrieve("my-tenant", "camp-1")

            // then
            assertThat(result.name).isEqualTo("camp-1")
            coVerify { campaignService.retrieveConfiguration("my-tenant", "camp-1") }
            coVerify { campaignService.retrieve("my-tenant", "camp-1") }
            coVerify { zoneService.resolve("my-tenant", emptySet()) }
            coVerify { campaignMeterEnricher.distribute("my-tenant", listOf("camp-1"), listOf("sc-1")) }
            confirmVerified(campaignService, zoneService, campaignMeterEnricher)
        }

    private fun setupRetrieveForStepStatusTest(
        keeper: StandaloneCampaignReportStateKeeperImpl,
        step: StepReport,
        messages: List<ReportMessage> = emptyList()
    ) {
        coEvery {
            campaignService.retrieveConfiguration("my-tenant", "camp-1")
        } throws RuntimeException("no config")
        coEvery { zoneService.resolve("my-tenant", emptySet()) } returns emptyList()
        coEvery {
            campaignMeterEnricher.distribute("my-tenant", listOf("camp-1"), listOf("sc-1"))
        } returns mapOf("camp-1" to emptyDistribution())
        coEvery { campaignService.retrieve("my-tenant", "camp-1") } returns Campaign(
            version = now,
            key = "camp-1",
            creation = start,
            name = "My Campaign",
            speedFactor = 1.0,
            scheduledMinions = null,
            start = start,
            end = end,
            status = ExecutionStatus.SUCCESSFUL,
            configurerName = null,
            configuredScenarios = emptyList()
        )
        keeper.campaignStates()["camp-1"]!!["sc-1"]!!.keyedMessages.putAll(
            messages.associateBy { it.messageId }
        )
        val scenarioState = keeper.campaignStates()["camp-1"]!!["sc-1"]!!
        scenarioState.registerStep(
            step.name, step.dagId,
            InMemoryStepExecutionState(
                name = step.name,
                dagId = step.dagId,
                isUnderLoad = step.isUnderLoad,
                initialized = step.initialized,
                initializationError = step.initializationError
            )
        )
        scenarioState.stepByName(step.name)!!.successfulExecutionsCounter.addAndGet(step.successfulExecutions)
        scenarioState.stepByName(step.name)!!.failedExecutionsCounter.addAndGet(step.failedExecutions)
    }

    private fun createKeeperWithCampaign(): StandaloneCampaignReportStateKeeperImpl {
        val keeper = StandaloneCampaignReportStateKeeperImpl(
            idGenerator, consoleReporter, Duration.ofSeconds(5), taskScheduler,
            mockk {
                every { get() } returns campaignService
            }, zoneService, campaignMeterEnricher
        )
        return keeper
    }

    @Test
    @Timeout(2)
    internal fun `retrieve step status should be FAILED when step is not initialized`() =
        testDispatcherProvider.runTest {
            // given
            val keeper = createKeeperWithCampaign()
            keeper.start("camp-1", "sc-1")
            keeper.complete("camp-1", "sc-1")
            keeper.complete("camp-1")
            val step = StepReport(name = "step-1", dagId = "dag-1", isUnderLoad = true, initialized = false)
            setupRetrieveForStepStatusTest(keeper, step)

            // when
            val result = keeper.retrieve("my-tenant", "camp-1")

            // then
            assertThat(result.scenarios[0].steps[0].status).isEqualTo(ExecutionStatus.FAILED)
        }

    @Test
    @Timeout(2)
    internal fun `retrieve step status should be FAILED when step has initialization error`() =
        testDispatcherProvider.runTest {
            // given
            val keeper = createKeeperWithCampaign()
            keeper.start("camp-1", "sc-1")
            keeper.complete("camp-1", "sc-1")
            keeper.complete("camp-1")
            val step = StepReport(
                name = "step-1", dagId = "dag-1", isUnderLoad = true, initialized = true,
                initializationError = "Connection timeout"
            )
            setupRetrieveForStepStatusTest(keeper, step)

            // when
            val result = keeper.retrieve("my-tenant", "camp-1")

            // then
            assertThat(result.scenarios[0].steps[0].status).isEqualTo(ExecutionStatus.FAILED)
        }

    @Test
    @Timeout(2)
    internal fun `retrieve step status should be FAILED when there are ERROR messages for the step`() =
        testDispatcherProvider.runTest {
            // given
            val keeper = createKeeperWithCampaign()
            keeper.start("camp-1", "sc-1")
            keeper.complete("camp-1", "sc-1")
            keeper.complete("camp-1")
            val step = StepReport(name = "step-1", dagId = "dag-1", isUnderLoad = true, initialized = true)
            val messages = listOf(
                ReportMessage(
                    stepName = "step-1", messageId = "m1", severity = ReportMessageSeverity.ERROR,
                    message = "Fatal error"
                )
            )
            setupRetrieveForStepStatusTest(keeper, step, messages)

            // when
            val result = keeper.retrieve("my-tenant", "camp-1")

            // then
            assertThat(result.scenarios[0].steps[0].status).isEqualTo(ExecutionStatus.FAILED)
        }

    @Test
    @Timeout(2)
    internal fun `retrieve step status should be WARNING when there are WARN messages for the step`() =
        testDispatcherProvider.runTest {
            // given
            val keeper = createKeeperWithCampaign()
            keeper.start("camp-1", "sc-1")
            keeper.complete("camp-1", "sc-1")
            keeper.complete("camp-1")
            val step = StepReport(name = "step-1", dagId = "dag-1", isUnderLoad = true, initialized = true)
            val messages = listOf(
                ReportMessage(
                    stepName = "step-1", messageId = "m1", severity = ReportMessageSeverity.WARN,
                    message = "Slow response"
                )
            )
            setupRetrieveForStepStatusTest(keeper, step, messages)

            // when
            val result = keeper.retrieve("my-tenant", "camp-1")

            // then
            assertThat(result.scenarios[0].steps[0].status).isEqualTo(ExecutionStatus.WARNING)
        }

    @Test
    @Timeout(2)
    internal fun `retrieve step status should be WARNING when failedExecutions is greater than zero`() =
        testDispatcherProvider.runTest {
            // given
            val keeper = createKeeperWithCampaign()
            keeper.start("camp-1", "sc-1")
            keeper.complete("camp-1", "sc-1")
            keeper.complete("camp-1")
            val step = StepReport(
                name = "step-1", dagId = "dag-1", isUnderLoad = true, initialized = true,
                successfulExecutions = 9L, failedExecutions = 1L
            )
            setupRetrieveForStepStatusTest(keeper, step)

            // when
            val result = keeper.retrieve("my-tenant", "camp-1")

            // then
            assertThat(result.scenarios[0].steps[0].status).isEqualTo(ExecutionStatus.WARNING)
        }

    @Test
    @Timeout(2)
    internal fun `retrieve step status should be SUCCESSFUL when all checks pass`() =
        testDispatcherProvider.runTest {
            // given
            val keeper = createKeeperWithCampaign()
            keeper.start("camp-1", "sc-1")
            keeper.complete("camp-1", "sc-1")
            keeper.complete("camp-1")
            val step = StepReport(
                name = "step-1", dagId = "dag-1", isUnderLoad = true, initialized = true,
                successfulExecutions = 10L, failedExecutions = 0L
            )
            setupRetrieveForStepStatusTest(keeper, step)

            // when
            val result = keeper.retrieve("my-tenant", "camp-1")

            // then
            assertThat(result.scenarios[0].steps[0].status).isEqualTo(ExecutionStatus.SUCCESSFUL)
        }

    @Test
    @Timeout(1)
    internal fun `retrieveCampaignsReports should populate resolvedZones from zone service`() =
        testDispatcherProvider.run {
            // given
            val keeper = StandaloneCampaignReportStateKeeperImpl(
                idGenerator,
                null,
                Duration.ofSeconds(5),
                taskScheduler,
                mockk {
                    every { get() } returns campaignService
                },
                zoneService,
                campaignMeterEnricher
            )
            keeper.start("camp-1", "sc-1")
            keeper.complete("camp-1", "sc-1")
            keeper.complete("camp-1")
            val campaignConfig = CampaignConfiguration(
                name = "My Campaign",
                scenarios = mapOf("sc-1" to ScenarioRequest(minionsCount = 5, zones = mapOf("fr" to 70, "de" to 30)))
            )
            val zoneFr = Zone(key = "fr", title = "France")
            val zoneDe = Zone(key = "de", title = "Germany")
            coEvery { campaignService.retrieveConfiguration("my-tenant", "camp-1") } returns campaignConfig
            coEvery { zoneService.resolve("my-tenant", setOf("fr", "de")) } returns listOf(zoneFr, zoneDe)
            coEvery {
                campaignMeterEnricher.distribute("my-tenant", listOf("camp-1"), listOf("sc-1"))
            } returns mapOf("camp-1" to emptyDistribution())
            coEvery { campaignService.retrieve("my-tenant", "camp-1") } throws RuntimeException("not found")

            // when
            val reports = keeper.retrieveCampaignsReports("my-tenant", listOf("camp-1"))

            // then
            assertThat(reports.toList()[0].resolvedZones.map { it.key }.toSet()).isEqualTo(setOf("fr", "de"))
        }

    @Test
    @Timeout(1)
    internal fun `retrieveCampaignsReports should populate zones field from campaign configuration`() =
        testDispatcherProvider.run {
            // given
            val keeper = StandaloneCampaignReportStateKeeperImpl(
                idGenerator,
                null,
                Duration.ofSeconds(5),
                taskScheduler,
                mockk {
                    every { get() } returns campaignService
                },
                zoneService,
                campaignMeterEnricher
            )
            keeper.start("camp-1", "sc-1")
            keeper.complete("camp-1", "sc-1")
            keeper.complete("camp-1")
            val campaignConfig = CampaignConfiguration(
                name = "My Campaign",
                scenarios = mapOf("sc-1" to ScenarioRequest(minionsCount = 5, zones = mapOf("fr" to 70, "de" to 30)))
            )
            coEvery { campaignService.retrieveConfiguration("my-tenant", "camp-1") } returns campaignConfig
            coEvery { zoneService.resolve("my-tenant", setOf("fr", "de")) } returns emptyList()
            coEvery {
                campaignMeterEnricher.distribute("my-tenant", listOf("camp-1"), listOf("sc-1"))
            } returns mapOf("camp-1" to emptyDistribution())
            coEvery { campaignService.retrieve("my-tenant", "camp-1") } throws RuntimeException("not found")

            // when
            val reports = keeper.retrieveCampaignsReports("my-tenant", listOf("camp-1"))

            // then
            assertThat(reports.toList()[0].zones).isEqualTo(setOf("fr", "de"))
        }

    @Test
    @Timeout(1)
    internal fun `retrieveCampaignsReports should populate campaign level meters`() = testDispatcherProvider.run {
        // given
        val keeper = StandaloneCampaignReportStateKeeperImpl(
            idGenerator, null, Duration.ofSeconds(5), taskScheduler, mockk {
                every { get() } returns campaignService
            }, zoneService, campaignMeterEnricher
        )
        keeper.start("camp-1", "sc-1")
        keeper.complete("camp-1", "sc-1")
        keeper.complete("camp-1")
        val campaignMeter = TimeSeriesMeter(name = "throughput", timestamp = now, type = "counter", campaign = "camp-1")
        val distribution = MeterDistribution(
            campaignMeters = listOf(campaignMeter),
            byScenario = emptyMap(),
            byScenarioAndStep = emptyMap()
        )
        coEvery { campaignService.retrieveConfiguration("my-tenant", "camp-1") } throws RuntimeException("no config")
        coEvery { zoneService.resolve("my-tenant", emptySet()) } returns emptyList()
        coEvery {
            campaignMeterEnricher.distribute("my-tenant", listOf("camp-1"), listOf("sc-1"))
        } returns mapOf("camp-1" to distribution)
        coEvery { campaignService.retrieve("my-tenant", "camp-1") } throws RuntimeException("not found")

        // when
        val reports = keeper.retrieveCampaignsReports("my-tenant", listOf("camp-1"))

        // then
        assertThat(reports.toList()[0].meters).isEqualTo(listOf(campaignMeter))
    }

    @Test
    @Timeout(1)
    internal fun `retrieveCampaignsReports should populate scenario level meters`() = testDispatcherProvider.run {
        // given
        val keeper = StandaloneCampaignReportStateKeeperImpl(
            idGenerator, null, Duration.ofSeconds(5), taskScheduler, mockk {
                every { get() } returns campaignService
            }, zoneService, campaignMeterEnricher
        )
        keeper.start("camp-1", "sc-1")
        keeper.complete("camp-1", "sc-1")
        keeper.complete("camp-1")
        val scenarioMeter = TimeSeriesMeter(name = "rps", timestamp = now, type = "gauge", campaign = "camp-1")
        val distribution = MeterDistribution(
            campaignMeters = emptyList(),
            byScenario = mapOf("sc-1" to listOf(scenarioMeter)),
            byScenarioAndStep = emptyMap()
        )
        coEvery { campaignService.retrieveConfiguration("my-tenant", "camp-1") } throws RuntimeException("no config")
        coEvery { zoneService.resolve("my-tenant", emptySet()) } returns emptyList()
        coEvery {
            campaignMeterEnricher.distribute("my-tenant", listOf("camp-1"), listOf("sc-1"))
        } returns mapOf("camp-1" to distribution)
        coEvery { campaignService.retrieve("my-tenant", "camp-1") } throws RuntimeException("not found")

        // when
        val reports = keeper.retrieveCampaignsReports("my-tenant", listOf("camp-1"))

        // then
        assertThat(reports.toList()[0].scenarios[0].meters).isEqualTo(listOf(scenarioMeter))
    }

    @Test
    @Timeout(1)
    internal fun `retrieveCampaignsReports should populate step level meters`() = testDispatcherProvider.run {
        // given
        val keeper = StandaloneCampaignReportStateKeeperImpl(
            idGenerator, null, Duration.ofSeconds(5), taskScheduler, mockk {
                every { get() } returns campaignService
            }, zoneService, campaignMeterEnricher
        )
        keeper.start("camp-1", "sc-1")
        keeper.recordSuccessfulStepInitialization("camp-1", "sc-1", "step-1", "dag-1", true)
        keeper.complete("camp-1", "sc-1")
        keeper.complete("camp-1")
        val stepMeter = TimeSeriesMeter(name = "latency", timestamp = now, type = "timer", campaign = "camp-1")
        val distribution = MeterDistribution(
            campaignMeters = emptyList(),
            byScenario = emptyMap(),
            byScenarioAndStep = mapOf("sc-1" to mapOf("step-1" to listOf(stepMeter)))
        )
        coEvery { campaignService.retrieveConfiguration("my-tenant", "camp-1") } throws RuntimeException("no config")
        coEvery { zoneService.resolve("my-tenant", emptySet()) } returns emptyList()
        coEvery {
            campaignMeterEnricher.distribute("my-tenant", listOf("camp-1"), listOf("sc-1"))
        } returns mapOf("camp-1" to distribution)
        coEvery { campaignService.retrieve("my-tenant", "camp-1") } throws RuntimeException("not found")

        // when
        val reports = keeper.retrieveCampaignsReports("my-tenant", listOf("camp-1"))

        // then
        assertThat(reports.toList()[0].scenarios[0].steps[0].meters).isEqualTo(listOf(stepMeter))
    }
}
