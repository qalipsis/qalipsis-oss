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
import io.aerisconsulting.catadioptre.getProperty
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.report.CampaignReport
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ReportMessage
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.core.head.inmemory.InMemoryScenarioReportingExecutionState
import io.qalipsis.core.head.inmemory.StandaloneInMemoryCampaignReportStateKeeperImpl
import io.qalipsis.core.head.inmemory.catadioptre.scenarioStates
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.lang.TestIdGenerator
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant

internal class StandaloneInMemoryCampaignReportStateKeeperImplTest {

    @JvmField
    @RegisterExtension
    val testDispatcherProvider = TestDispatcherProvider()

    private val idGenerator = TestIdGenerator

    @Test
    internal fun `should start the campaign`() = testDispatcherProvider.run {
        // given
        val campaignStateKeeper = StandaloneInMemoryCampaignReportStateKeeperImpl(idGenerator)

        // when
        campaignStateKeeper.start("the campaign", "the scenario")

        // then
        assertThat(campaignStateKeeper.getProperty<Map<ScenarioName, InMemoryScenarioReportingExecutionState>>("scenarioStates")).all {
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
    internal fun `should complete the started campaign`() = testDispatcherProvider.run {
        // given
        val campaignStateKeeper = StandaloneInMemoryCampaignReportStateKeeperImpl(idGenerator)
        campaignStateKeeper.start("the campaign", "the scenario")

        // when
        campaignStateKeeper.complete("the campaign", "the scenario")

        // then
        assertThat(campaignStateKeeper.getProperty<Map<ScenarioName, InMemoryScenarioReportingExecutionState>>("scenarioStates")).all {
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
    internal fun `should add a message to the started campaign`() = testDispatcherProvider.runTest {
        // given
        val campaignStateKeeper = StandaloneInMemoryCampaignReportStateKeeperImpl(idGenerator)
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
        assertThat(campaignStateKeeper.getProperty<Map<ScenarioName, InMemoryScenarioReportingExecutionState>>("scenarioStates")).all {
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
    internal fun `should delete a message from the started campaign`() = testDispatcherProvider.runTest {
        // given
        val campaignStateKeeper = StandaloneInMemoryCampaignReportStateKeeperImpl(idGenerator)
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
        assertThat(campaignStateKeeper.getProperty<Map<ScenarioName, InMemoryScenarioReportingExecutionState>>("scenarioStates")).all {
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
    internal fun `should record started minions to the started campaign`() = testDispatcherProvider.runTest {
        // given
        val campaignStateKeeper = StandaloneInMemoryCampaignReportStateKeeperImpl(idGenerator)
        campaignStateKeeper.start("the campaign", "the scenario")

        // when
        campaignStateKeeper.recordStartedMinion("the campaign", "the scenario", 5)
        campaignStateKeeper.recordStartedMinion("the campaign", "the scenario", 3)

        // then
        assertThat(campaignStateKeeper.getProperty<Map<ScenarioName, InMemoryScenarioReportingExecutionState>>("scenarioStates")).all {
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
    internal fun `should record completed minions to the started campaign`() = testDispatcherProvider.runTest {
        // given
        val campaignStateKeeper = StandaloneInMemoryCampaignReportStateKeeperImpl(idGenerator)
        campaignStateKeeper.start("the campaign", "the scenario")

        // when
        campaignStateKeeper.recordCompletedMinion("the campaign", "the scenario", 5)
        campaignStateKeeper.recordCompletedMinion("the campaign", "the scenario", 3)

        // then
        assertThat(campaignStateKeeper.getProperty<Map<ScenarioName, InMemoryScenarioReportingExecutionState>>("scenarioStates")).all {
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
    internal fun `should successful steps to the started campaign`() = testDispatcherProvider.runTest {
        // given
        val campaignStateKeeper = StandaloneInMemoryCampaignReportStateKeeperImpl(idGenerator)
        campaignStateKeeper.start("the campaign", "the scenario")

        // when
        campaignStateKeeper.recordSuccessfulStepExecution("the campaign", "the scenario", "the step", 5)
        campaignStateKeeper.recordSuccessfulStepExecution("the campaign", "the scenario", "the step", 3)
        campaignStateKeeper.recordSuccessfulStepExecution("the campaign", "the scenario", "the other step", 10)

        // then
        assertThat(campaignStateKeeper.getProperty<Map<ScenarioName, InMemoryScenarioReportingExecutionState>>("scenarioStates")).all {
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
    internal fun `should record failed steps to the started campaign`() = testDispatcherProvider.runTest {
        // given
        val campaignStateKeeper = StandaloneInMemoryCampaignReportStateKeeperImpl(idGenerator)
        campaignStateKeeper.start("the campaign", "the scenario")

        // when
        campaignStateKeeper.recordFailedStepExecution("the campaign", "the scenario", "the step", 5)
        campaignStateKeeper.recordFailedStepExecution("the campaign", "the scenario", "the step", 3)
        campaignStateKeeper.recordFailedStepExecution("the campaign", "the scenario", "the other step", 10)

        // then
        assertThat(campaignStateKeeper.getProperty<Map<ScenarioName, InMemoryScenarioReportingExecutionState>>("scenarioStates")).all {
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
        val campaignStateKeeper = StandaloneInMemoryCampaignReportStateKeeperImpl(idGenerator)
        campaignStateKeeper.start("the campaign", "the scenario 1")

        // when + then
        assertThrows<TimeoutCancellationException> {
            withTimeout(300) {
                campaignStateKeeper.report("the campaign")
            }
        }
    }

    @Test
    @Timeout(1)
    internal fun `should not generate a report when nothing started`() = testDispatcherProvider.run {
        // given
        val campaignStateKeeper = StandaloneInMemoryCampaignReportStateKeeperImpl(idGenerator)

        // when + then
        assertThrows<TimeoutCancellationException> {
            withTimeout(300) {
                campaignStateKeeper.report("the campaign")
            }
        }
    }

    @Test
    @Timeout(1)
    internal fun `should allow the report when the campaign started and is aborted`() = testDispatcherProvider.run {
        // given
        val campaignStateKeeper = StandaloneInMemoryCampaignReportStateKeeperImpl(idGenerator)
        campaignStateKeeper.start("the campaign", "the scenario 1")
        campaignStateKeeper.abort("the campaign")

        // when
        val report = campaignStateKeeper.report("the campaign")

        // then
        assertThat(report).all {
            prop(CampaignReport::campaignName).isEqualTo("the campaign")
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
            val campaignStateKeeper = StandaloneInMemoryCampaignReportStateKeeperImpl(idGenerator)
            campaignStateKeeper.start("the campaign", "the scenario 1")
            campaignStateKeeper.start("the campaign", "the scenario 2")
            campaignStateKeeper.complete("the campaign", "the scenario 1")
            campaignStateKeeper.complete("the campaign", "the scenario 2")
            val states =
                campaignStateKeeper.getProperty<Map<ScenarioName, InMemoryScenarioReportingExecutionState>>("scenarioStates")
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
            val report = campaignStateKeeper.report("the campaign")

            // then
            assertThat(report).all {
                prop(CampaignReport::campaignName).isEqualTo("the campaign")
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
            val campaignStateKeeper = StandaloneInMemoryCampaignReportStateKeeperImpl(idGenerator)
            campaignStateKeeper.start("the campaign", "the scenario 1")
            campaignStateKeeper.start("the campaign", "the scenario 2")
            campaignStateKeeper.complete("the campaign", "the scenario 1")
            campaignStateKeeper.complete("the campaign", "the scenario 2")
            val states =
                campaignStateKeeper.getProperty<Map<ScenarioName, InMemoryScenarioReportingExecutionState>>(
                    "scenarioStates"
                )
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
            val report = campaignStateKeeper.report("the campaign")

            // then
            assertThat(report).all {
                prop(CampaignReport::campaignName).isEqualTo("the campaign")
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
            val campaignStateKeeper = StandaloneInMemoryCampaignReportStateKeeperImpl(idGenerator)
            campaignStateKeeper.start("the campaign", "the scenario 1")
            campaignStateKeeper.start("the campaign", "the scenario 2")
            campaignStateKeeper.complete("the campaign", "the scenario 1")
            campaignStateKeeper.complete("the campaign", "the scenario 2")
            val states =
                campaignStateKeeper.getProperty<Map<ScenarioName, InMemoryScenarioReportingExecutionState>>(
                    "scenarioStates"
                )
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
            val report = campaignStateKeeper.report("the campaign")

            // then
            assertThat(report).all {
                prop(CampaignReport::campaignName).isEqualTo("the campaign")
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
            val campaignStateKeeper = StandaloneInMemoryCampaignReportStateKeeperImpl(idGenerator)
            campaignStateKeeper.start("the campaign", "the scenario 1")
            campaignStateKeeper.start("the campaign", "the scenario 2")
            campaignStateKeeper.complete("the campaign", "the scenario 1")
            campaignStateKeeper.complete("the campaign", "the scenario 2")
            val states = campaignStateKeeper.scenarioStates()
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
            val report = campaignStateKeeper.report("the campaign")

            // then
            assertThat(report).all {
                prop(CampaignReport::campaignName).isEqualTo("the campaign")
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
}
