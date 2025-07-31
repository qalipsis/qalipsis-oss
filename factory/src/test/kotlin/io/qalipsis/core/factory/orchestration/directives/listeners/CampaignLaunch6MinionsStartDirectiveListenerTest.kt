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
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verify
import io.qalipsis.core.directives.MinionsStartDirective
import io.qalipsis.core.directives.TestDescriptiveDirective
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.orchestration.LocalAssignmentStore
import io.qalipsis.core.factory.orchestration.MinionAssignmentKeeper
import io.qalipsis.core.factory.orchestration.MinionsKeeper
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.MinionsStartFeedback
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant

/**
 * @author Eric Jessé
 */
@WithMockk
internal class CampaignLaunch6MinionsStartDirectiveListenerTest {

    @field:RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @MockK
    private lateinit var localAssignmentStore: LocalAssignmentStore

    @RelaxedMockK
    private lateinit var minionsKeeper: MinionsKeeper

    @RelaxedMockK
    private lateinit var factoryChannel: FactoryChannel

    @MockK
    private lateinit var minionsAssignmentKeeper: MinionAssignmentKeeper

    @InjectMockKs
    private lateinit var processor: CampaignLaunch6MinionsStartDirectiveListener

    @Test
    @Timeout(1)
    internal fun `should accept minions start directive`() {
        val directive = MinionsStartDirective("my-campaign", "my-scenario", Instant.now(), "the-channel")
        every { localAssignmentStore.hasMinionsAssigned("my-scenario") } returns true

        Assertions.assertTrue(processor.accept(directive))
        verify { localAssignmentStore.hasMinionsAssigned("my-scenario") }
    }

    @Test
    @Timeout(1)
    internal fun `should not accept minions start directive when there is no assignment`() {
        val directive = MinionsStartDirective("my-campaign", "my-scenario", Instant.now(), "the-channel")
        every { localAssignmentStore.hasMinionsAssigned("my-scenario") } returns false

        Assertions.assertFalse(processor.accept(directive))
        verify { localAssignmentStore.hasMinionsAssigned("my-scenario") }
    }

    @Test
    @Timeout(1)
    internal fun `should not accept not minions start directive`() {
        Assertions.assertFalse(processor.accept(TestDescriptiveDirective()))
    }

    @Test
    @Timeout(1)
    internal fun `should start the minions executing the root DAG under load locally`() = testCoroutineDispatcher.run {
        // given
        val start = Instant.now().plusMillis(123)
        val directive = MinionsStartDirective("my-campaign", "my-scenario", start, "the-channel")
        every { localAssignmentStore.hasRootUnderLoadLocally("my-scenario", "my-minion-1") } returns true
        every { localAssignmentStore.hasRootUnderLoadLocally("my-scenario", "my-minion-2") } returns true
        every { localAssignmentStore.hasRootUnderLoadLocally("my-scenario", "my-minion-3") } returns true
        every { localAssignmentStore.hasRootUnderLoadLocally("my-scenario", "my-minion-4") } returns false
        coEvery { minionsAssignmentKeeper.readSchedulePlan("my-campaign", "my-scenario") } returns mapOf(
            6789L to listOf("my-minion-1", "my-minion-3"),
            4567L to listOf("my-minion-4"),
            1234L to listOf("my-minion-2")
        )

        // when
        processor.notify(directive)

        // then
        coVerifyOrder {
            minionsKeeper.startSingletons("my-scenario")
            minionsAssignmentKeeper.readSchedulePlan("my-campaign", "my-scenario")
            localAssignmentStore.hasRootUnderLoadLocally("my-scenario", "my-minion-1")
            localAssignmentStore.hasRootUnderLoadLocally("my-scenario", "my-minion-3")
            localAssignmentStore.hasRootUnderLoadLocally("my-scenario", "my-minion-4")
            localAssignmentStore.hasRootUnderLoadLocally("my-scenario", "my-minion-2")
            factoryChannel.publishFeedback(
                MinionsStartFeedback(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario",
                    status = FeedbackStatus.IN_PROGRESS
                )
            )
            minionsKeeper.scheduleMinionStart(start.plusMillis(1234L), listOf("my-minion-2"))
            minionsKeeper.scheduleMinionStart(start.plusMillis(6789L), listOf("my-minion-1", "my-minion-3"))
            factoryChannel.publishFeedback(
                MinionsStartFeedback(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario",
                    status = FeedbackStatus.COMPLETED
                )
            )
        }
        confirmVerified(minionsKeeper, localAssignmentStore, factoryChannel, minionsAssignmentKeeper)
    }

    @Test
    @Timeout(1)
    internal fun `should do nothing when no minion executes the root DAG under load locally`() =
        testCoroutineDispatcher.run {
            // given
            val start = Instant.now().plusMillis(123)
            val directive = MinionsStartDirective("my-campaign", "my-scenario", start, "the-channel")
            every { localAssignmentStore.hasRootUnderLoadLocally("my-scenario", any()) } returns false
            coEvery { minionsAssignmentKeeper.readSchedulePlan("my-campaign", "my-scenario") } returns mapOf(
                6789L to listOf("my-minion-1", "my-minion-3"),
                4567L to listOf("my-minion-4"),
                1234L to listOf("my-minion-2")
            )

            // when
            processor.notify(directive)

            // then
            coVerify {
                minionsKeeper.startSingletons("my-scenario")
                minionsAssignmentKeeper.readSchedulePlan("my-campaign", "my-scenario")
                localAssignmentStore.hasRootUnderLoadLocally("my-scenario", "my-minion-1")
                localAssignmentStore.hasRootUnderLoadLocally("my-scenario", "my-minion-3")
                localAssignmentStore.hasRootUnderLoadLocally("my-scenario", "my-minion-4")
                localAssignmentStore.hasRootUnderLoadLocally("my-scenario", "my-minion-2")
                factoryChannel.publishFeedback(
                    MinionsStartFeedback(
                        campaignKey = "my-campaign",
                        scenarioName = "my-scenario",
                        status = FeedbackStatus.IGNORED
                    )
                )
            }
            confirmVerified(minionsKeeper, localAssignmentStore, factoryChannel, minionsAssignmentKeeper)
        }


    @Test
    @Timeout(1)
    internal fun `should fail to start when a problem occurs`() = testCoroutineDispatcher.run {
        // given
        val start = Instant.now().plusMillis(123)
        val directive = MinionsStartDirective("my-campaign", "my-scenario", start, "the-channel")
        every { localAssignmentStore.hasRootUnderLoadLocally("my-scenario", any()) } returns true
        coEvery { minionsAssignmentKeeper.readSchedulePlan("my-campaign", "my-scenario") } returns mapOf(
            6789L to listOf("my-minion-1", "my-minion-3"),
            1234L to listOf("my-minion-2")
        )
        coEvery {
            minionsKeeper.scheduleMinionStart(any(), listOf("my-minion-1", "my-minion-3"))
        } throws RuntimeException("A problem occurred")

        // when
        processor.notify(directive)

        // then
        coVerify {
            minionsKeeper.startSingletons("my-scenario")
            minionsAssignmentKeeper.readSchedulePlan("my-campaign", "my-scenario")
            localAssignmentStore.hasRootUnderLoadLocally("my-scenario", "my-minion-1")
            localAssignmentStore.hasRootUnderLoadLocally("my-scenario", "my-minion-2")
            localAssignmentStore.hasRootUnderLoadLocally("my-scenario", "my-minion-3")
            factoryChannel.publishFeedback(
                MinionsStartFeedback(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario",
                    status = FeedbackStatus.IN_PROGRESS
                )
            )
            minionsKeeper.scheduleMinionStart(start.plusMillis(1234L), listOf("my-minion-2"))
            minionsKeeper.scheduleMinionStart(start.plusMillis(6789L), listOf("my-minion-1", "my-minion-3"))
            factoryChannel.publishFeedback(
                MinionsStartFeedback(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario",
                    status = FeedbackStatus.FAILED,
                    errorMessage = "A problem occurred"
                )
            )
        }
        confirmVerified(minionsKeeper, localAssignmentStore, factoryChannel, minionsAssignmentKeeper)
    }
}
