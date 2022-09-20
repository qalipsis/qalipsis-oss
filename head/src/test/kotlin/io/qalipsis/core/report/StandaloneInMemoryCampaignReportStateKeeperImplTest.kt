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
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.key
import assertk.assertions.prop
import io.qalipsis.api.report.CampaignReport
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ReportMessage
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.core.head.inmemory.InMemoryScenarioReportingExecutionState
import io.qalipsis.core.head.inmemory.StandaloneInMemoryCampaignReportStateKeeperImpl
import io.qalipsis.core.head.inmemory.catadioptre.campaignStates
import io.qalipsis.core.head.model.CampaignExecutionDetails
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.lang.TestIdGenerator
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Duration
import java.time.Instant

internal class StandaloneInMemoryCampaignReportStateKeeperImplTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    private val idGenerator = TestIdGenerator

    @Test
    internal fun `should start the scenario in all the campaigns`() = testDispatcherProvider.run {
        // given
        val campaignStateKeeper = StandaloneInMemoryCampaignReportStateKeeperImpl(idGenerator, Duration.ofSeconds(5))

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
        val campaignStateKeeper = StandaloneInMemoryCampaignReportStateKeeperImpl(idGenerator, Duration.ofSeconds(5))

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
    }

    @Test
    internal fun `should complete the started scenario`() = testDispatcherProvider.run {
        // given
        val campaignStateKeeper = StandaloneInMemoryCampaignReportStateKeeperImpl(idGenerator, Duration.ofSeconds(5))
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
    }

    @Test
    internal fun `should add a message to the started scenario`() = testDispatcherProvider.runTest {
        // given
        val campaignStateKeeper = StandaloneInMemoryCampaignReportStateKeeperImpl(idGenerator, Duration.ofSeconds(5))
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
                    key(messageId).all {
                        prop(ReportMessage::messageId).isEqualTo(messageId)
                        prop(ReportMessage::stepName).isEqualTo("the step")
                        prop(ReportMessage::severity).isEqualTo(ReportMessageSeverity.INFO)
                        prop(ReportMessage::message).isEqualTo("The message")
                    }
                }
            }
        }
    }

    @Test
    internal fun `should delete a message from the started scenario`() = testDispatcherProvider.runTest {
        // given
        val campaignStateKeeper = StandaloneInMemoryCampaignReportStateKeeperImpl(idGenerator, Duration.ofSeconds(5))
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
    }

    @Test
    internal fun `should record started minions to the started scenario`() = testDispatcherProvider.runTest {
        // given
        val campaignStateKeeper = StandaloneInMemoryCampaignReportStateKeeperImpl(idGenerator, Duration.ofSeconds(5))
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
    }

    @Test
    internal fun `should record completed minions to the started scenario`() = testDispatcherProvider.runTest {
        // given
        val campaignStateKeeper = StandaloneInMemoryCampaignReportStateKeeperImpl(idGenerator, Duration.ofSeconds(5))
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
    }

    @Test
    internal fun `should successful steps to the started scenario`() = testDispatcherProvider.runTest {
        // given
        val campaignStateKeeper = StandaloneInMemoryCampaignReportStateKeeperImpl(idGenerator, Duration.ofSeconds(5))
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
    }

    @Test
    internal fun `should record failed steps to the started scenario`() = testDispatcherProvider.runTest {
        // given
        val campaignStateKeeper = StandaloneInMemoryCampaignReportStateKeeperImpl(idGenerator, Duration.ofSeconds(5))
        campaignStateKeeper.start("the campaign", "the scenario")

        // when
        campaignStateKeeper.recordFailedStepExecution("the campaign", "the scenario", "the step", 5)
        campaignStateKeeper.recordFailedStepExecution("the campaign", "the scenario", "the step", 3)
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
    }

    @Test
    @Timeout(1)
    internal fun `should not generate a report while there are running scenarios`() = testDispatcherProvider.run {
        // given
        val campaignStateKeeper = StandaloneInMemoryCampaignReportStateKeeperImpl(idGenerator, Duration.ofSeconds(5))
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
        val campaignStateKeeper = StandaloneInMemoryCampaignReportStateKeeperImpl(idGenerator, Duration.ofSeconds(5))

        // when + then
        assertThrows<NoSuchElementException> {
            campaignStateKeeper.generateReport("the campaign")
        }
    }

    @Test
    @Timeout(1)
    internal fun `should allow the report when the campaign started and is aborted`() = testDispatcherProvider.run {
        // given
        val campaignStateKeeper = StandaloneInMemoryCampaignReportStateKeeperImpl(idGenerator, Duration.ofSeconds(5))
        campaignStateKeeper.start("the campaign", "the scenario 1")
        campaignStateKeeper.abort("the campaign")

        // when
        val report = campaignStateKeeper.generateReport("the campaign")

        // then
        assertThat(report).all {
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
                StandaloneInMemoryCampaignReportStateKeeperImpl(idGenerator, Duration.ofSeconds(5))
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
            state1.failedStepExecutionsCounter.addAndGet(2345)
            state1.messages["the id 1"] =
                ReportMessage("step 1", "the id 1", ReportMessageSeverity.INFO, " The message 1")
            state1.messages["the id 2"] =
                ReportMessage("step 2", "the id 2", ReportMessageSeverity.INFO, " The message 2")

            state2.end = Instant.now().minusSeconds(123)
            state2.startedMinionsCounter.addAndGet(765)
            state2.completedMinionsCounter.addAndGet(345)
            state2.successfulStepExecutionsCounter.addAndGet(854)
            state2.failedStepExecutionsCounter.addAndGet(3567)
            state2.messages["the id 3"] =
                ReportMessage("step 1", "the id 3", ReportMessageSeverity.INFO, " The message 3")
            state2.messages["the id 4"] =
                ReportMessage("step 2", "the id 4", ReportMessageSeverity.INFO, " The message 4")
            campaignStateKeeper.complete("the campaign")

            // when
            val report = campaignStateKeeper.generateReport("the campaign")

            // then
            assertThat(report).all {
                prop(CampaignReport::campaignKey).isEqualTo("the campaign")
                prop(CampaignReport::start).isEqualTo(state1.start)
                prop(CampaignReport::end).isEqualTo(state1.end)
                prop(CampaignReport::status).isEqualTo(ExecutionStatus.SUCCESSFUL)
                prop(CampaignReport::startedMinions).isEqualTo(1231 + 765)
                prop(CampaignReport::completedMinions).isEqualTo(234 + 345)
                prop(CampaignReport::successfulExecutions).isEqualTo(7643 + 854)
                prop(CampaignReport::failedExecutions).isEqualTo(2345 + 3567)
                prop(CampaignReport::scenariosReports).hasSize(2)
            }
        }

    @Test
    @Timeout(1)
    internal fun `should generate a report for all the scenarios of the campaign as warning when there are warning`() =
        testDispatcherProvider.run {
            // given
            val campaignStateKeeper =
                StandaloneInMemoryCampaignReportStateKeeperImpl(idGenerator, Duration.ofSeconds(5))
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
            state1.failedStepExecutionsCounter.addAndGet(2345)
            state1.messages["the id 1"] =
                ReportMessage("step 1", "the id 1", ReportMessageSeverity.INFO, " The message 1")
            state1.messages["the id 2"] =
                ReportMessage("step 2", "the id 2", ReportMessageSeverity.WARN, " The message 2")

            state2.end = Instant.now().minusSeconds(123)
            state2.startedMinionsCounter.addAndGet(765)
            state2.completedMinionsCounter.addAndGet(345)
            state2.successfulStepExecutionsCounter.addAndGet(854)
            state2.failedStepExecutionsCounter.addAndGet(3567)
            state2.messages["the id 3"] =
                ReportMessage("step 1", "the id 3", ReportMessageSeverity.INFO, " The message 3")
            state2.messages["the id 4"] =
                ReportMessage("step 2", "the id 4", ReportMessageSeverity.WARN, " The message 4")
            campaignStateKeeper.complete("the campaign")

            // when
            val report = campaignStateKeeper.generateReport("the campaign")

            // then
            assertThat(report).all {
                prop(CampaignReport::campaignKey).isEqualTo("the campaign")
                prop(CampaignReport::start).isEqualTo(state1.start)
                prop(CampaignReport::end).isEqualTo(state1.end)
                prop(CampaignReport::status).isEqualTo(ExecutionStatus.WARNING)
                prop(CampaignReport::startedMinions).isEqualTo(1231 + 765)
                prop(CampaignReport::completedMinions).isEqualTo(234 + 345)
                prop(CampaignReport::successfulExecutions).isEqualTo(7643 + 854)
                prop(CampaignReport::failedExecutions).isEqualTo(2345 + 3567)
                prop(CampaignReport::scenariosReports).hasSize(2)
            }
        }

    @Test
    @Timeout(1)
    internal fun `should generate a report for all the scenarios of the campaign as error when there is one error`() =
        testDispatcherProvider.run {
            // given
            val campaignStateKeeper =
                StandaloneInMemoryCampaignReportStateKeeperImpl(idGenerator, Duration.ofSeconds(5))
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
            state1.messages["the id 1"] =
                ReportMessage("step 1", "the id 1", ReportMessageSeverity.INFO, " The message 1")
            state1.messages["the id 2"] =
                ReportMessage("step 2", "the id 2", ReportMessageSeverity.WARN, " The message 2")

            state2.end = Instant.now()
            state2.startedMinionsCounter.addAndGet(765)
            state2.completedMinionsCounter.addAndGet(345)
            state2.successfulStepExecutionsCounter.addAndGet(854)
            state2.failedStepExecutionsCounter.addAndGet(3567)
            state2.messages["the id 3"] =
                ReportMessage("step 1", "the id 3", ReportMessageSeverity.ERROR, " The message 3")
            state2.messages["the id 4"] =
                ReportMessage("step 2", "the id 4", ReportMessageSeverity.WARN, " The message 4")
            campaignStateKeeper.complete("the campaign")

            // when
            val report = campaignStateKeeper.generateReport("the campaign")

            // then
            assertThat(report).all {
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
                StandaloneInMemoryCampaignReportStateKeeperImpl(idGenerator, Duration.ofSeconds(5))
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
            state1.messages["the id 1"] =
                ReportMessage("step 1", "the id 1", ReportMessageSeverity.ABORT, " The message 1")
            state1.messages["the id 2"] =
                ReportMessage("step 2", "the id 2", ReportMessageSeverity.WARN, " The message 2")

            state2.end = Instant.now()
            state2.startedMinionsCounter.addAndGet(765)
            state2.completedMinionsCounter.addAndGet(345)
            state2.successfulStepExecutionsCounter.addAndGet(854)
            state2.failedStepExecutionsCounter.addAndGet(3567)
            state2.messages["the id 3"] =
                ReportMessage("step 1", "the id 3", ReportMessageSeverity.ERROR, " The message 3")
            state2.messages["the id 4"] =
                ReportMessage("step 2", "the id 4", ReportMessageSeverity.WARN, " The message 4")
            campaignStateKeeper.complete("the campaign")

            // when
            val report = campaignStateKeeper.generateReport("the campaign")

            // then
            assertThat(report).all {
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
    internal fun `should retrieve a report for all the scenarios of the campaign`() =
        testDispatcherProvider.run {
            // given
            val campaignReportProvider =
                StandaloneInMemoryCampaignReportStateKeeperImpl(idGenerator, Duration.ofSeconds(5))
            campaignReportProvider.start("the campaign", "the scenario 1")
            campaignReportProvider.start("the campaign", "the scenario 2")
            campaignReportProvider.complete("the campaign", "the scenario 1")
            campaignReportProvider.complete("the campaign", "the scenario 2")
            val states = campaignReportProvider.campaignStates().asMap()["the campaign"]!!
            val state1 = states["the scenario 1"]!!
            val state2 = states["the scenario 2"]!!

            state1.end = Instant.now().minusSeconds(123)
            state1.startedMinionsCounter.addAndGet(1231)
            state1.completedMinionsCounter.addAndGet(234)
            state1.successfulStepExecutionsCounter.addAndGet(7643)
            state1.failedStepExecutionsCounter.addAndGet(2345)
            state1.messages["the id 1"] =
                ReportMessage("step 1", "the id 1", ReportMessageSeverity.ABORT, " The message 1")
            state1.messages["the id 2"] =
                ReportMessage("step 2", "the id 2", ReportMessageSeverity.WARN, " The message 2")

            state2.end = Instant.now()
            state2.startedMinionsCounter.addAndGet(765)
            state2.completedMinionsCounter.addAndGet(345)
            state2.successfulStepExecutionsCounter.addAndGet(854)
            state2.failedStepExecutionsCounter.addAndGet(3567)
            state2.messages["the id 3"] =
                ReportMessage("step 1", "the id 3", ReportMessageSeverity.ERROR, " The message 3")
            state2.messages["the id 4"] =
                ReportMessage("step 2", "the id 4", ReportMessageSeverity.WARN, " The message 4")
            campaignReportProvider.complete("the campaign")

            // when
            val report = campaignReportProvider.retrieveCampaignReport("my-tenant", "the campaign")

            // then
            assertThat(report).all {
                prop(CampaignExecutionDetails::key).isEqualTo("the campaign")
                prop(CampaignExecutionDetails::name).isEqualTo("the campaign")
                prop(CampaignExecutionDetails::start).isEqualTo(state1.start)
                prop(CampaignExecutionDetails::end).isEqualTo(state2.end)
                prop(CampaignExecutionDetails::status).isEqualTo(ExecutionStatus.ABORTED)
                prop(CampaignExecutionDetails::startedMinions).isEqualTo(1231 + 765)
                prop(CampaignExecutionDetails::completedMinions).isEqualTo(234 + 345)
                prop(CampaignExecutionDetails::successfulExecutions).isEqualTo(7643 + 854)
                prop(CampaignExecutionDetails::failedExecutions).isEqualTo(2345 + 3567)
                prop(CampaignExecutionDetails::scenariosReports).hasSize(2)
            }
        }
}
