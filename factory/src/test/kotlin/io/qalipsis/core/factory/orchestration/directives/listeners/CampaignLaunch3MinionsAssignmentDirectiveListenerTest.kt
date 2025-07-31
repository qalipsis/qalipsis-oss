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

package io.qalipsis.core.factory.orchestration.directives.listeners

import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.core.directives.MinionsAssignmentDirective
import io.qalipsis.core.directives.TestDescriptiveDirective
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.factory.orchestration.MinionAssignmentKeeper
import io.qalipsis.core.factory.orchestration.MinionsKeeper
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.MinionsAssignmentFeedback
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.verifyOnce
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * @author Eric Jessé
 */
@WithMockk
internal class CampaignLaunch3MinionsAssignmentDirectiveListenerTest {

    @field:RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var minionsKeeper: MinionsKeeper

    @RelaxedMockK
    private lateinit var minionAssignmentKeeper: MinionAssignmentKeeper

    @RelaxedMockK
    private lateinit var factoryChannel: FactoryChannel

    @RelaxedMockK
    private lateinit var factoryCampaignManager: FactoryCampaignManager

    @InjectMockKs
    private lateinit var processor: CampaignLaunch3MinionsAssignmentDirectiveListener

    @Test
    @Timeout(1)
    fun `should accept minions assignment directive`() {
        val directive = MinionsAssignmentDirective("my-campaign", "my-scenario")
        every { factoryCampaignManager.isLocallyExecuted("my-campaign", "my-scenario") } returns true

        Assertions.assertTrue(processor.accept(directive))

        verifyOnce { factoryCampaignManager.isLocallyExecuted("my-campaign", "my-scenario") }
    }

    @Test
    @Timeout(1)
    fun `should not accept not minions assignment directive`() {
        Assertions.assertFalse(processor.accept(TestDescriptiveDirective()))
        confirmVerified(factoryCampaignManager)
    }

    @Test
    @Timeout(1)
    fun `should not accept minions assignment directive for unknown scenario`() {
        val directive = MinionsAssignmentDirective("my-campaign", "my-scenario")
        every { factoryCampaignManager.isLocallyExecuted("my-campaign", "my-scenario") } returns false

        Assertions.assertFalse(processor.accept(directive))

        verifyOnce { factoryCampaignManager.isLocallyExecuted("my-campaign", "my-scenario") }
    }

    @Test
    @Timeout(1)
    fun `should assign and create the minions successfully`() = testCoroutineDispatcher.run {
        // given
        val directive = MinionsAssignmentDirective("my-campaign", "my-scenario")
        coEvery { minionAssignmentKeeper.assign("my-campaign", "my-scenario") } returns mapOf(
            "minion-1" to listOf("dag-1", "dag-2"),
            "minion-2" to listOf("dag-2", "dag-3")
        )

        // when
        processor.notify(directive)

        // then
        coVerifyOrder {
            factoryChannel.publishFeedback(
                MinionsAssignmentFeedback(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario",
                    status = FeedbackStatus.IN_PROGRESS
                )
            )
            minionAssignmentKeeper.assign("my-campaign", "my-scenario")
            minionsKeeper.create("my-campaign", "my-scenario", listOf("dag-1", "dag-2"), "minion-1")
            minionsKeeper.create("my-campaign", "my-scenario", listOf("dag-2", "dag-3"), "minion-2")
            factoryChannel.publishFeedback(
                MinionsAssignmentFeedback(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario",
                    status = FeedbackStatus.COMPLETED
                )
            )
        }
        confirmVerified(minionAssignmentKeeper, minionsKeeper, factoryChannel, factoryCampaignManager)
    }


    @Test
    @Timeout(1)
    fun `should assign and ignore when no minion was assigned`() = testCoroutineDispatcher.run {
        // given
        val directive = MinionsAssignmentDirective("my-campaign", "my-scenario")
        coEvery { minionAssignmentKeeper.assign("my-campaign", "my-scenario") } returns emptyMap()

        // when
        processor.notify(directive)

        // then
        coVerifyOrder {
            factoryChannel.publishFeedback(
                MinionsAssignmentFeedback(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario",
                    status = FeedbackStatus.IN_PROGRESS
                )
            )
            minionAssignmentKeeper.assign("my-campaign", "my-scenario")
            factoryChannel.publishFeedback(
                MinionsAssignmentFeedback(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario",
                    status = FeedbackStatus.IGNORED
                )
            )
        }
        confirmVerified(minionAssignmentKeeper, minionsKeeper, factoryChannel, factoryCampaignManager)
    }


    @Test
    @Timeout(1)
    fun `should fail to assign and create the minions`() = testCoroutineDispatcher.run {
        // given
        val directive = MinionsAssignmentDirective("my-campaign", "my-scenario")
        coEvery {
            minionAssignmentKeeper.assign(
                "my-campaign",
                "my-scenario"
            )
        } throws RuntimeException("A problem occurred")

        // when
        processor.notify(directive)

        // then
        coVerifyOrder {
            factoryChannel.publishFeedback(
                MinionsAssignmentFeedback(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario",
                    status = FeedbackStatus.IN_PROGRESS
                )
            )
            minionAssignmentKeeper.assign("my-campaign", "my-scenario")
            factoryChannel.publishFeedback(
                MinionsAssignmentFeedback(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario",
                    status = FeedbackStatus.FAILED,
                    errorMessage = "A problem occurred"
                )
            )
        }
        confirmVerified(minionAssignmentKeeper, minionsKeeper, factoryChannel, factoryCampaignManager)
    }

}
