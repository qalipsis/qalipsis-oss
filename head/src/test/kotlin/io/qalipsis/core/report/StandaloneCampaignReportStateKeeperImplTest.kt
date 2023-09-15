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
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.report.CampaignReport
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ReportMessage
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.core.head.inmemory.InMemoryScenarioReportingExecutionState
import io.qalipsis.core.head.inmemory.StandaloneCampaignReportStateKeeperImpl
import io.qalipsis.core.head.inmemory.catadioptre.campaignStates
import io.qalipsis.core.head.inmemory.consolereporter.ConsoleCampaignProgressionReporter
import io.qalipsis.core.head.model.CampaignExecutionDetails
import io.qalipsis.core.head.model.ScenarioExecutionDetails
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.lang.TestIdGenerator
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension

@WithMockk
internal class StandaloneCampaignReportStateKeeperImplTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    private val idGenerator = TestIdGenerator

    @RelaxedMockK
    private lateinit var consoleReporter: ConsoleCampaignProgressionReporter

    @Test
    internal fun `should start the scenario in all the campaigns`() = testDispatcherProvider.run {
        // given
        val campaignStateKeeper =
            StandaloneCampaignReportStateKeeperImpl(idGenerator, consoleReporter, Duration.ofSeconds(5))

        // when
        campaignStateKeeper.start("the campaign-1", "the scenario-1")
        campaignStateKeeper.start("the campaign-2", "the scenario-1")
        campaignStateKeeper.start("the campaign-2", "the scenario-2")

        // then
        assertThat(campaignStateKeeper.campaignStates().asMap()).isNotNull().all {
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
            StandaloneCampaignReportStateKeeperImpl(idGenerator, consoleReporter, Duration.ofSeconds(5))

        // when
        campaignStateKeeper.start("the campaign", "the scenario")

        // then
        assertThat(campaignStateKeeper.campaignStates().asMap().get("the campaign")).isNotNull().all {
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
            StandaloneCampaignReportStateKeeperImpl(idGenerator, consoleReporter, Duration.ofSeconds(5))
        campaignStateKeeper.start("the campaign", "the scenario")

        // when
        campaignStateKeeper.complete("the campaign", "the scenario")

        // then
        assertThat(campaignStateKeeper.campaignStates().asMap().get("the campaign")).isNotNull().all {
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
            StandaloneCampaignReportStateKeeperImpl(idGenerator, consoleReporter, Duration.ofSeconds(5))
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
        assertThat(campaignStateKeeper.campaignStates().asMap().get("the campaign")).isNotNull().all {
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
            StandaloneCampaignReportStateKeeperImpl(idGenerator, consoleReporter, Duration.ofSeconds(5))
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
        assertThat(campaignStateKeeper.campaignStates().asMap().get("the campaign")).isNotNull().all {
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
            StandaloneCampaignReportStateKeeperImpl(idGenerator, consoleReporter, Duration.ofSeconds(5))
        campaignStateKeeper.start("the campaign", "the scenario")

        // when
        campaignStateKeeper.recordStartedMinion("the campaign", "the scenario", 5)
        campaignStateKeeper.recordStartedMinion("the campaign", "the scenario", 3)

        // then
        assertThat(campaignStateKeeper.campaignStates().asMap().get("the campaign")).isNotNull().all {
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
            StandaloneCampaignReportStateKeeperImpl(idGenerator, consoleReporter, Duration.ofSeconds(5))
        campaignStateKeeper.start("the campaign", "the scenario")

        // when
        campaignStateKeeper.recordCompletedMinion("the campaign", "the scenario", 5)
        campaignStateKeeper.recordCompletedMinion("the campaign", "the scenario", 3)

        // then
        assertThat(campaignStateKeeper.campaignStates().asMap().get("the campaign")).isNotNull().all {
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
            StandaloneCampaignReportStateKeeperImpl(idGenerator, consoleReporter, Duration.ofSeconds(5))
        campaignStateKeeper.start("the campaign", "the scenario")

        // when
        campaignStateKeeper.recordSuccessfulStepExecution("the campaign", "the scenario", "the step", 5)
        campaignStateKeeper.recordSuccessfulStepExecution("the campaign", "the scenario", "the step", 3)
        campaignStateKeeper.recordSuccessfulStepExecution("the campaign", "the scenario", "the other step", 10)

        // then
        assertThat(campaignStateKeeper.campaignStates().asMap().get("the campaign")).isNotNull().all {
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
            StandaloneCampaignReportStateKeeperImpl(idGenerator, consoleReporter, Duration.ofSeconds(5))
        campaignStateKeeper.start("the campaign", "the scenario")

        // when
        val failure = TimeoutException()
        campaignStateKeeper.recordFailedStepExecution("the campaign", "the scenario", "the step", 5)
        campaignStateKeeper.recordFailedStepExecution("the campaign", "the scenario", "the step", 3, failure)
        campaignStateKeeper.recordFailedStepExecution("the campaign", "the scenario", "the other step", 10)

        // then
        assertThat(campaignStateKeeper.campaignStates().asMap().get("the campaign")).isNotNull().all {
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
    @Timeout(1)
    internal fun `should not generate a report while there are running scenarios`() = testDispatcherProvider.run {
        // given
        val campaignStateKeeper =
            StandaloneCampaignReportStateKeeperImpl(idGenerator, consoleReporter, Duration.ofSeconds(5))
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
            StandaloneCampaignReportStateKeeperImpl(idGenerator, consoleReporter, Duration.ofSeconds(5))

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
            StandaloneCampaignReportStateKeeperImpl(idGenerator, consoleReporter, Duration.ofSeconds(5))
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
                    Duration.ofSeconds(5)
                )
            campaignStateKeeper.start("the campaign", "the scenario 1")
            campaignStateKeeper.start("the campaign", "the scenario 2")
            campaignStateKeeper.complete("the campaign", "the scenario 1")
            campaignStateKeeper.complete("the campaign", "the scenario 2")
            val states = campaignStateKeeper.campaignStates().asMap().get("the campaign")!!
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
                    Duration.ofSeconds(5)
                )
            campaignStateKeeper.start("the campaign", "the scenario 1")
            campaignStateKeeper.start("the campaign", "the scenario 2")
            campaignStateKeeper.complete("the campaign", "the scenario 1")
            campaignStateKeeper.complete("the campaign", "the scenario 2")
            val states = campaignStateKeeper.campaignStates().asMap().get("the campaign")!!
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
            val campaignStateKeeper =
                StandaloneCampaignReportStateKeeperImpl(
                    idGenerator,
                    consoleReporter,
                    Duration.ofSeconds(5)
                )
            campaignStateKeeper.start("the campaign", "the scenario 1")
            campaignStateKeeper.start("the campaign", "the scenario 2")
            campaignStateKeeper.complete("the campaign", "the scenario 1")
            campaignStateKeeper.complete("the campaign", "the scenario 2")
            val states = campaignStateKeeper.campaignStates().asMap().get("the campaign")!!
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

    @Test
    @Timeout(1)
    internal fun `should generate a report for all the scenarios of the campaign as abort when there is one abort`() =
        testDispatcherProvider.run {
            // given
            val campaignStateKeeper =
                StandaloneCampaignReportStateKeeperImpl(
                    idGenerator,
                    consoleReporter,
                    Duration.ofSeconds(5)
                )
            campaignStateKeeper.start("the campaign", "the scenario 1")
            campaignStateKeeper.start("the campaign", "the scenario 2")
            campaignStateKeeper.complete("the campaign", "the scenario 1")
            campaignStateKeeper.complete("the campaign", "the scenario 2")
            val states = campaignStateKeeper.campaignStates().asMap()["the campaign"]!!
            val state1 = states["the scenario 1"]!!
            val state2 = states["the scenario 2"]!!

            state1.end = Instant.now().minusSeconds(123)
            state1.startedMinionsCounter.addAndGet(1231)
            state1.completedMinionsCounter.addAndGet(234)
            state1.successfulStepExecutionsCounter.addAndGet(7643)
            state1.failedStepExecutionsCounter.addAndGet(2345)
            state1.keyedMessages["the id 1"] =
                ReportMessage("step 1", "the id 1", ReportMessageSeverity.ABORT, " The message 1")
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
                prop(CampaignReport::status).isEqualTo(ExecutionStatus.ABORTED)
                prop(CampaignReport::startedMinions).isEqualTo(1231 + 765)
                prop(CampaignReport::completedMinions).isEqualTo(234 + 345)
                prop(CampaignReport::successfulExecutions).isEqualTo(7643 + 854)
                prop(CampaignReport::failedExecutions).isEqualTo(2345 + 3567)
                prop(CampaignReport::scenariosReports).hasSize(2)
            }
        }

    @Test
    @Timeout(1)
    internal fun `should retrieve the report details for a unique campaign`() = testDispatcherProvider.run {
        // given
        val campaignReportProvider =
            StandaloneCampaignReportStateKeeperImpl(idGenerator, consoleReporter, Duration.ofSeconds(5))
        campaignReportProvider.start("key-1", "the scenario 1")
        campaignReportProvider.start("key-1", "the scenario 2")
        campaignReportProvider.complete("key-1", "the scenario 1")
        campaignReportProvider.complete("key-1", "the scenario 2")
        val states1 = campaignReportProvider.campaignStates().asMap()["key-1"]!!
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
                prop(CampaignExecutionDetails::successfulExecutions).isEqualTo(7643 + 854)
                prop(CampaignExecutionDetails::failedExecutions).isEqualTo(2345 + 3567)
                prop(CampaignExecutionDetails::scenariosReports).all {
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
            StandaloneCampaignReportStateKeeperImpl(idGenerator, consoleReporter, Duration.ofSeconds(5))
        campaignReportProvider.start("key-1", "the scenario 1")
        campaignReportProvider.start("key-1", "the scenario 2")
        campaignReportProvider.start("key-2", "the scenario 1")
        campaignReportProvider.start("key-3", "the scenario 2")
        campaignReportProvider.complete("key-1", "the scenario 1")
        campaignReportProvider.complete("key-1", "the scenario 2")
        campaignReportProvider.complete("key-2", "the scenario 1")
        campaignReportProvider.complete("key-3", "the scenario 2")
        val states1 = campaignReportProvider.campaignStates().asMap()["key-1"]!!
        val state11 = states1["the scenario 1"]!!
        val state12 = states1["the scenario 2"]!!
        val states2 = campaignReportProvider.campaignStates().asMap()["key-2"]!!
        val state21 = states2["the scenario 1"]!!
        val states3 = campaignReportProvider.campaignStates().asMap()["key-3"]!!
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
                prop(CampaignExecutionDetails::successfulExecutions).isEqualTo(7643 + 854)
                prop(CampaignExecutionDetails::failedExecutions).isEqualTo(2345 + 3567)
                prop(CampaignExecutionDetails::scenariosReports).all {
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
                prop(CampaignExecutionDetails::successfulExecutions).isEqualTo(854)
                prop(CampaignExecutionDetails::failedExecutions).isEqualTo(3567)
                prop(CampaignExecutionDetails::scenariosReports).all {
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
                prop(CampaignExecutionDetails::successfulExecutions).isEqualTo(854)
                prop(CampaignExecutionDetails::failedExecutions).isEqualTo(3567)
                prop(CampaignExecutionDetails::scenariosReports).all {
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
            StandaloneCampaignReportStateKeeperImpl(idGenerator, null, Duration.ofSeconds(5))
        campaignReportProvider.start("key-1", "the scenario 1")
        campaignReportProvider.start("key-1", "the scenario 2")
        campaignReportProvider.start("key-3", "the scenario 2")
        campaignReportProvider.complete("key-1", "the scenario 1")
        campaignReportProvider.complete("key-1", "the scenario 2")
        campaignReportProvider.complete("key-3", "the scenario 2")
        val states1 = campaignReportProvider.campaignStates().asMap()["key-1"]!!
        val state11 = states1["the scenario 1"]!!
        val state12 = states1["the scenario 2"]!!
        val states3 = campaignReportProvider.campaignStates().asMap()["key-3"]!!
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
                prop(CampaignExecutionDetails::successfulExecutions).isEqualTo(854)
                prop(CampaignExecutionDetails::failedExecutions).isEqualTo(3567)
                prop(CampaignExecutionDetails::scenariosReports).all {
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
                prop(CampaignExecutionDetails::successfulExecutions).isEqualTo(7643 + 854)
                prop(CampaignExecutionDetails::failedExecutions).isEqualTo(2345 + 3567)
                prop(CampaignExecutionDetails::scenariosReports).all {
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
                StandaloneCampaignReportStateKeeperImpl(idGenerator, null, Duration.ofSeconds(5))

            // when
            val report =
                campaignReportProvider.retrieveCampaignsReports("my-tenant", listOf("keys-1", "campaign-1", "my-key"))

            // then
            assertThat(report.toList()).isEmpty()
        }
}
