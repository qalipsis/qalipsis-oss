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
import io.qalipsis.api.context.CampaignId
import io.qalipsis.api.context.ScenarioId
import io.qalipsis.api.report.CampaignReport
import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.api.report.ReportMessage
import io.qalipsis.api.report.ReportMessageSeverity
import io.qalipsis.test.lang.TestIdGenerator
import org.junit.jupiter.api.Test
import java.time.Instant

internal class StandaloneInMemoryCampaignStateKeeperImplTest {

    val idGenerator = TestIdGenerator

    @Test
    internal fun `should start the campaign`() {
        // given
        val campaignStateKeeper = StandaloneInMemoryCampaignStateKeeperImpl(idGenerator)

        // when
        campaignStateKeeper.start("the campaign", "the scenario")

        // then
        assertThat(
            campaignStateKeeper.getProperty<Map<CampaignId, Map<ScenarioId, StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign>>>(
                "runningCampaigns"
            )
        ).all {
            hasSize(1)
            key("the campaign").all {
                hasSize(1)
                key("the scenario").all {
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::campaignId).isEqualTo("the campaign")
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::scenarioId).isEqualTo("the scenario")
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::start).isNotNull()
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::end).isNull()
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::startedMinions).transform { it.get() }.isEqualTo(0)
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::completedMinions).transform { it.get() }.isEqualTo(0)
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::successfulStepExecutions).transform { it.get() }
                        .isEqualTo(0)
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::failedStepExecutions).transform { it.get() }
                        .isEqualTo(0)
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::messages).isEmpty()
                }
            }
        }
    }

    @Test
    internal fun `should complete the started campaign`() {
        // given
        val campaignStateKeeper = StandaloneInMemoryCampaignStateKeeperImpl(idGenerator)
        campaignStateKeeper.start("the campaign", "the scenario")

        // when
        campaignStateKeeper.complete("the campaign", "the scenario")

        // then
        assertThat(
            campaignStateKeeper.getProperty<Map<CampaignId, Map<ScenarioId, StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign>>>(
                "runningCampaigns"
            )
        ).all {
            hasSize(1)
            key("the campaign").all {
                hasSize(1)
                key("the scenario").all {
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::campaignId).isEqualTo("the campaign")
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::scenarioId).isEqualTo("the scenario")
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::start).isNotNull()
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::end).isNotNull()
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::startedMinions).transform { it.get() }.isEqualTo(0)
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::completedMinions).transform { it.get() }.isEqualTo(0)
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::successfulStepExecutions).transform { it.get() }
                        .isEqualTo(0)
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::failedStepExecutions).transform { it.get() }
                        .isEqualTo(0)
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::messages).isEmpty()
                }
            }
        }
    }

    @Test
    internal fun `should add a message to the started campaign`() {
        // given
        val campaignStateKeeper = StandaloneInMemoryCampaignStateKeeperImpl(idGenerator)
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
        assertThat(
            campaignStateKeeper.getProperty<Map<CampaignId, Map<ScenarioId, StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign>>>(
                "runningCampaigns"
            )
        ).all {
            hasSize(1)
            key("the campaign").all {
                hasSize(1)
                key("the scenario").all {
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::campaignId).isEqualTo("the campaign")
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::scenarioId).isEqualTo("the scenario")
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::start).isNotNull()
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::end).isNull()
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::startedMinions).transform { it.get() }.isEqualTo(0)
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::completedMinions).transform { it.get() }.isEqualTo(0)
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::successfulStepExecutions).transform { it.get() }
                        .isEqualTo(0)
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::failedStepExecutions).transform { it.get() }
                        .isEqualTo(0)
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::messages).all {
                        hasSize(1)
                        key(messageId).all {
                            prop(ReportMessage::messageId).isEqualTo(messageId)
                            prop(ReportMessage::stepId).isEqualTo("the step")
                            prop(ReportMessage::severity).isEqualTo(ReportMessageSeverity.INFO)
                            prop(ReportMessage::message).isEqualTo("The message")
                        }
                    }
                }
            }
        }
    }

    @Test
    internal fun `should delete a message from the started campaign`() {
        // given
        val campaignStateKeeper = StandaloneInMemoryCampaignStateKeeperImpl(idGenerator)
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
        assertThat(
            campaignStateKeeper.getProperty<Map<CampaignId, Map<ScenarioId, StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign>>>(
                "runningCampaigns"
            )
        ).all {
            hasSize(1)
            key("the campaign").all {
                hasSize(1)
                key("the scenario").all {
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::campaignId).isEqualTo("the campaign")
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::scenarioId).isEqualTo("the scenario")
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::start).isNotNull()
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::end).isNull()
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::startedMinions).transform { it.get() }.isEqualTo(0)
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::completedMinions).transform { it.get() }.isEqualTo(0)
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::successfulStepExecutions).transform { it.get() }
                        .isEqualTo(0)
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::failedStepExecutions).transform { it.get() }
                        .isEqualTo(0)
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::messages).isEmpty()
                }
            }
        }
    }

    @Test
    internal fun `should record started minions to the started campaign`() {
        // given
        val campaignStateKeeper = StandaloneInMemoryCampaignStateKeeperImpl(idGenerator)
        campaignStateKeeper.start("the campaign", "the scenario")

        // when
        campaignStateKeeper.recordStartedMinion("the campaign", "the scenario", 5)
        campaignStateKeeper.recordStartedMinion("the campaign", "the scenario", 3)

        // then
        assertThat(
            campaignStateKeeper.getProperty<Map<CampaignId, Map<ScenarioId, StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign>>>(
                "runningCampaigns"
            )
        ).all {
            hasSize(1)
            key("the campaign").all {
                hasSize(1)
                key("the scenario").all {
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::campaignId).isEqualTo("the campaign")
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::scenarioId).isEqualTo("the scenario")
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::start).isNotNull()
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::end).isNull()
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::startedMinions).transform { it.get() }.isEqualTo(8)
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::completedMinions).transform { it.get() }.isEqualTo(0)
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::successfulStepExecutions).transform { it.get() }
                        .isEqualTo(0)
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::failedStepExecutions).transform { it.get() }
                        .isEqualTo(0)
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::messages).isEmpty()
                }
            }
        }
    }

    @Test
    internal fun `should record completed minions to the started campaign`() {
        // given
        val campaignStateKeeper = StandaloneInMemoryCampaignStateKeeperImpl(idGenerator)
        campaignStateKeeper.start("the campaign", "the scenario")

        // when
        campaignStateKeeper.recordCompletedMinion("the campaign", "the scenario", 5)
        campaignStateKeeper.recordCompletedMinion("the campaign", "the scenario", 3)

        // then
        assertThat(
            campaignStateKeeper.getProperty<Map<CampaignId, Map<ScenarioId, StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign>>>(
                "runningCampaigns"
            )
        ).all {
            hasSize(1)
            key("the campaign").all {
                hasSize(1)
                key("the scenario").all {
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::campaignId).isEqualTo("the campaign")
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::scenarioId).isEqualTo("the scenario")
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::start).isNotNull()
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::end).isNull()
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::startedMinions).transform { it.get() }.isEqualTo(0)
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::completedMinions).transform { it.get() }.isEqualTo(8)
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::successfulStepExecutions).transform { it.get() }
                        .isEqualTo(0)
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::failedStepExecutions).transform { it.get() }
                        .isEqualTo(0)
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::messages).isEmpty()
                }
            }
        }
    }

    @Test
    internal fun `should successful steps to the started campaign`() {
        // given
        val campaignStateKeeper = StandaloneInMemoryCampaignStateKeeperImpl(idGenerator)
        campaignStateKeeper.start("the campaign", "the scenario")

        // when
        campaignStateKeeper.recordSuccessfulStepExecution("the campaign", "the scenario", "the step", 5)
        campaignStateKeeper.recordSuccessfulStepExecution("the campaign", "the scenario", "the step", 3)
        campaignStateKeeper.recordSuccessfulStepExecution("the campaign", "the scenario", "the other step", 10)

        // then
        assertThat(
            campaignStateKeeper.getProperty<Map<CampaignId, Map<ScenarioId, StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign>>>(
                "runningCampaigns"
            )
        ).all {
            hasSize(1)
            key("the campaign").all {
                hasSize(1)
                key("the scenario").all {
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::campaignId).isEqualTo("the campaign")
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::scenarioId).isEqualTo("the scenario")
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::start).isNotNull()
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::end).isNull()
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::startedMinions).transform { it.get() }.isEqualTo(0)
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::completedMinions).transform { it.get() }.isEqualTo(0)
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::successfulStepExecutions).transform { it.get() }
                        .isEqualTo(18)
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::failedStepExecutions).transform { it.get() }
                        .isEqualTo(0)
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::messages).isEmpty()
                }
            }
        }
    }

    @Test
    internal fun `should record failed steps to the started campaign`() {
        // given
        val campaignStateKeeper = StandaloneInMemoryCampaignStateKeeperImpl(idGenerator)
        campaignStateKeeper.start("the campaign", "the scenario")

        // when
        campaignStateKeeper.recordFailedStepExecution("the campaign", "the scenario", "the step", 5)
        campaignStateKeeper.recordFailedStepExecution("the campaign", "the scenario", "the step", 3)
        campaignStateKeeper.recordFailedStepExecution("the campaign", "the scenario", "the other step", 10)

        // then
        assertThat(
            campaignStateKeeper.getProperty<Map<CampaignId, Map<ScenarioId, StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign>>>(
                "runningCampaigns"
            )
        ).all {
            hasSize(1)
            key("the campaign").all {
                hasSize(1)
                key("the scenario").all {
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::campaignId).isEqualTo("the campaign")
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::scenarioId).isEqualTo("the scenario")
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::start).isNotNull()
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::end).isNull()
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::startedMinions).transform { it.get() }.isEqualTo(0)
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::completedMinions).transform { it.get() }.isEqualTo(0)
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::successfulStepExecutions).transform { it.get() }
                        .isEqualTo(0)
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::failedStepExecutions).transform { it.get() }
                        .isEqualTo(18)
                    prop(StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign::messages).isEmpty()
                }
            }
        }
    }


    @Test
    internal fun `should generate a report for all the scenarios of the campaign as successful when there are info only`() {
        // given
        val campaignStateKeeper = StandaloneInMemoryCampaignStateKeeperImpl(idGenerator)
        campaignStateKeeper.start("the campaign", "the scenario 1")
        campaignStateKeeper.start("the campaign", "the scenario 2")
        val states = campaignStateKeeper.getProperty<Map<CampaignId, Map<ScenarioId, StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign>>>(
            "runningCampaigns"
        )
        val state1 = states["the campaign"]!!["the scenario 1"]!!
        val state2 = states["the campaign"]!!["the scenario 2"]!!

        state1.end = Instant.now()
        state1.startedMinions.addAndGet(1231)
        state1.completedMinions.addAndGet(234)
        state1.successfulStepExecutions.addAndGet(7643)
        state1.failedStepExecutions.addAndGet(2345)
        state1.messages["the id 1"] = ReportMessage("step 1", "the id 1", ReportMessageSeverity.INFO, " The message 1")
        state1.messages["the id 2"] = ReportMessage("step 2", "the id 2", ReportMessageSeverity.INFO, " The message 2")

        state2.end = Instant.now().minusSeconds(123)
        state2.startedMinions.addAndGet(765)
        state2.completedMinions.addAndGet(345)
        state2.successfulStepExecutions.addAndGet(854)
        state2.failedStepExecutions.addAndGet(3567)
        state2.messages["the id 3"] = ReportMessage("step 1", "the id 3", ReportMessageSeverity.INFO, " The message 3")
        state2.messages["the id 4"] = ReportMessage("step 2", "the id 4", ReportMessageSeverity.INFO, " The message 4")

        // when
        val report = campaignStateKeeper.report("the campaign")

        // then
        assertThat(report).all {
            prop(CampaignReport::campaignId).isEqualTo("the campaign")
            prop(CampaignReport::start).isEqualTo(state1.start)
            prop(CampaignReport::end).isEqualTo(state1.end)
            prop(CampaignReport::status).isEqualTo(ExecutionStatus.SUCCESSFUL)
            prop(CampaignReport::configuredMinionsCount).isEqualTo(1231 + 765)
            prop(CampaignReport::executedMinionsCount).isEqualTo(234 + 345)
            prop(CampaignReport::successfulExecutions).isEqualTo(7643 + 854)
            prop(CampaignReport::failedExecutions).isEqualTo(2345 + 3567)
            prop(CampaignReport::scenariosReports).hasSize(2)
        }
    }

    @Test
    internal fun `should generate a report for all the scenarios of the campaign as warning when there are warning`() {
        // given
        val campaignStateKeeper = StandaloneInMemoryCampaignStateKeeperImpl(idGenerator)
        campaignStateKeeper.start("the campaign", "the scenario 1")
        campaignStateKeeper.start("the campaign", "the scenario 2")
        val states = campaignStateKeeper.getProperty<Map<CampaignId, Map<ScenarioId, StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign>>>(
            "runningCampaigns"
        )
        val state1 = states["the campaign"]!!["the scenario 1"]!!
        val state2 = states["the campaign"]!!["the scenario 2"]!!

        state1.end = Instant.now()
        state1.startedMinions.addAndGet(1231)
        state1.completedMinions.addAndGet(234)
        state1.successfulStepExecutions.addAndGet(7643)
        state1.failedStepExecutions.addAndGet(2345)
        state1.messages["the id 1"] = ReportMessage("step 1", "the id 1", ReportMessageSeverity.INFO, " The message 1")
        state1.messages["the id 2"] = ReportMessage("step 2", "the id 2", ReportMessageSeverity.WARN, " The message 2")

        state2.end = Instant.now().minusSeconds(123)
        state2.startedMinions.addAndGet(765)
        state2.completedMinions.addAndGet(345)
        state2.successfulStepExecutions.addAndGet(854)
        state2.failedStepExecutions.addAndGet(3567)
        state2.messages["the id 3"] = ReportMessage("step 1", "the id 3", ReportMessageSeverity.INFO, " The message 3")
        state2.messages["the id 4"] = ReportMessage("step 2", "the id 4", ReportMessageSeverity.WARN, " The message 4")

        // when
        val report = campaignStateKeeper.report("the campaign")

        // then
        assertThat(report).all {
            prop(CampaignReport::campaignId).isEqualTo("the campaign")
            prop(CampaignReport::start).isEqualTo(state1.start)
            prop(CampaignReport::end).isEqualTo(state1.end)
            prop(CampaignReport::status).isEqualTo(ExecutionStatus.WARNING)
            prop(CampaignReport::configuredMinionsCount).isEqualTo(1231 + 765)
            prop(CampaignReport::executedMinionsCount).isEqualTo(234 + 345)
            prop(CampaignReport::successfulExecutions).isEqualTo(7643 + 854)
            prop(CampaignReport::failedExecutions).isEqualTo(2345 + 3567)
            prop(CampaignReport::scenariosReports).hasSize(2)
        }
    }

    @Test
    internal fun `should generate a report for all the scenarios of the campaign as error when there is one error`() {
        // given
        val campaignStateKeeper = StandaloneInMemoryCampaignStateKeeperImpl(idGenerator)
        campaignStateKeeper.start("the campaign", "the scenario 1")
        campaignStateKeeper.start("the campaign", "the scenario 2")
        val states = campaignStateKeeper.getProperty<Map<CampaignId, Map<ScenarioId, StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign>>>(
            "runningCampaigns"
        )
        val state1 = states["the campaign"]!!["the scenario 1"]!!
        val state2 = states["the campaign"]!!["the scenario 2"]!!

        state1.end = Instant.now().minusSeconds(123)
        state1.startedMinions.addAndGet(1231)
        state1.completedMinions.addAndGet(234)
        state1.successfulStepExecutions.addAndGet(7643)
        state1.failedStepExecutions.addAndGet(2345)
        state1.messages["the id 1"] = ReportMessage("step 1", "the id 1", ReportMessageSeverity.INFO, " The message 1")
        state1.messages["the id 2"] = ReportMessage("step 2", "the id 2", ReportMessageSeverity.WARN, " The message 2")

        state2.end = Instant.now()
        state2.startedMinions.addAndGet(765)
        state2.completedMinions.addAndGet(345)
        state2.successfulStepExecutions.addAndGet(854)
        state2.failedStepExecutions.addAndGet(3567)
        state2.messages["the id 3"] = ReportMessage("step 1", "the id 3", ReportMessageSeverity.ERROR, " The message 3")
        state2.messages["the id 4"] = ReportMessage("step 2", "the id 4", ReportMessageSeverity.WARN, " The message 4")

        // when
        val report = campaignStateKeeper.report("the campaign")

        // then
        assertThat(report).all {
            prop(CampaignReport::campaignId).isEqualTo("the campaign")
            prop(CampaignReport::start).isEqualTo(state1.start)
            prop(CampaignReport::end).isEqualTo(state2.end)
            prop(CampaignReport::status).isEqualTo(ExecutionStatus.FAILED)
            prop(CampaignReport::configuredMinionsCount).isEqualTo(1231 + 765)
            prop(CampaignReport::executedMinionsCount).isEqualTo(234 + 345)
            prop(CampaignReport::successfulExecutions).isEqualTo(7643 + 854)
            prop(CampaignReport::failedExecutions).isEqualTo(2345 + 3567)
            prop(CampaignReport::scenariosReports).hasSize(2)
        }
    }


    @Test
    internal fun `should generate a report for all the scenarios of the campaign as abort when there is one abort`() {
        // given
        val campaignStateKeeper = StandaloneInMemoryCampaignStateKeeperImpl(idGenerator)
        campaignStateKeeper.start("the campaign", "the scenario 1")
        campaignStateKeeper.start("the campaign", "the scenario 2")
        val states = campaignStateKeeper.getProperty<Map<CampaignId, Map<ScenarioId, StandaloneInMemoryCampaignStateKeeperImpl.RunningCampaign>>>(
            "runningCampaigns"
        )
        val state1 = states["the campaign"]!!["the scenario 1"]!!
        val state2 = states["the campaign"]!!["the scenario 2"]!!

        state1.end = Instant.now().minusSeconds(123)
        state1.startedMinions.addAndGet(1231)
        state1.completedMinions.addAndGet(234)
        state1.successfulStepExecutions.addAndGet(7643)
        state1.failedStepExecutions.addAndGet(2345)
        state1.messages["the id 1"] = ReportMessage("step 1", "the id 1", ReportMessageSeverity.ABORT, " The message 1")
        state1.messages["the id 2"] = ReportMessage("step 2", "the id 2", ReportMessageSeverity.WARN, " The message 2")

        state2.end = Instant.now()
        state2.startedMinions.addAndGet(765)
        state2.completedMinions.addAndGet(345)
        state2.successfulStepExecutions.addAndGet(854)
        state2.failedStepExecutions.addAndGet(3567)
        state2.messages["the id 3"] = ReportMessage("step 1", "the id 3", ReportMessageSeverity.ERROR, " The message 3")
        state2.messages["the id 4"] = ReportMessage("step 2", "the id 4", ReportMessageSeverity.WARN, " The message 4")

        // when
        val report = campaignStateKeeper.report("the campaign")

        // then
        assertThat(report).all {
            prop(CampaignReport::campaignId).isEqualTo("the campaign")
            prop(CampaignReport::start).isEqualTo(state1.start)
            prop(CampaignReport::end).isEqualTo(state2.end)
            prop(CampaignReport::status).isEqualTo(ExecutionStatus.ABORTED)
            prop(CampaignReport::configuredMinionsCount).isEqualTo(1231 + 765)
            prop(CampaignReport::executedMinionsCount).isEqualTo(234 + 345)
            prop(CampaignReport::successfulExecutions).isEqualTo(7643 + 854)
            prop(CampaignReport::failedExecutions).isEqualTo(2345 + 3567)
            prop(CampaignReport::scenariosReports).hasSize(2)
        }
    }
}
