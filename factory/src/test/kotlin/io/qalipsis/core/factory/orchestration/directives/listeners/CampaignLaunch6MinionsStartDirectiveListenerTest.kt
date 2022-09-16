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
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.verify
import io.qalipsis.core.directives.MinionStartDefinition
import io.qalipsis.core.directives.MinionsStartDirective
import io.qalipsis.core.directives.TestDescriptiveDirective
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.orchestration.LocalAssignmentStore
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

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var localAssignmentStore: LocalAssignmentStore

    @RelaxedMockK
    private lateinit var minionsKeeper: MinionsKeeper

    @RelaxedMockK
    private lateinit var factoryChannel: FactoryChannel

    @InjectMockKs
    private lateinit var processor: CampaignLaunch6MinionsStartDirectiveListener

    @Test
    @Timeout(1)
    internal fun `should accept minions start directive`() {
        val directive = MinionsStartDirective("my-campaign", "my-scenario", listOf())
        every { localAssignmentStore.hasMinionsAssigned("my-scenario") } returns true

        Assertions.assertTrue(processor.accept(directive))
        verify { localAssignmentStore.hasMinionsAssigned("my-scenario") }
    }

    @Test
    @Timeout(1)
    internal fun `should not accept minions start directive when there is no assignment`() {
        val directive = MinionsStartDirective("my-campaign", "my-scenario", listOf())
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
        val directive = MinionsStartDirective(
            "my-campaign",
            "my-scenario",
            listOf(
                MinionStartDefinition("my-minion-1", 123),
                MinionStartDefinition("my-minion-2", 456),
                MinionStartDefinition("my-minion-3", 789)
            )
        )
        every { localAssignmentStore.hasRootUnderLoadLocally("my-scenario", "my-minion-1") } returns true
        every { localAssignmentStore.hasRootUnderLoadLocally("my-scenario", "my-minion-2") } returns false
        every { localAssignmentStore.hasRootUnderLoadLocally("my-scenario", "my-minion-3") } returns true

        // when
        processor.notify(directive)

        // then
        coVerify {
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
            minionsKeeper.scheduleMinionStart("my-minion-1", Instant.ofEpochMilli(123))
            minionsKeeper.scheduleMinionStart("my-minion-3", Instant.ofEpochMilli(789))
            factoryChannel.publishFeedback(
                MinionsStartFeedback(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario",
                    status = FeedbackStatus.COMPLETED
                )
            )
        }
        confirmVerified(minionsKeeper, localAssignmentStore, factoryChannel)
    }

    @Test
    @Timeout(1)
    internal fun `should do nothing when no minion executes the root DAG under load locally`() =
        testCoroutineDispatcher.run {
            // given
            val directive = MinionsStartDirective(
                "my-campaign",
                "my-scenario",
                listOf(
                    MinionStartDefinition("my-minion-1", 123),
                    MinionStartDefinition("my-minion-2", 456),
                    MinionStartDefinition("my-minion-3", 789)
                )
            )
            every { localAssignmentStore.hasRootUnderLoadLocally("my-scenario", any()) } returns false

            // when
            processor.notify(directive)

            // then
            coVerify {
                localAssignmentStore.hasRootUnderLoadLocally("my-scenario", "my-minion-1")
                localAssignmentStore.hasRootUnderLoadLocally("my-scenario", "my-minion-2")
                localAssignmentStore.hasRootUnderLoadLocally("my-scenario", "my-minion-3")
                factoryChannel.publishFeedback(
                    MinionsStartFeedback(
                        campaignKey = "my-campaign",
                        scenarioName = "my-scenario",
                        status = FeedbackStatus.IGNORED
                    )
                )
            }
            confirmVerified(minionsKeeper, localAssignmentStore, factoryChannel)
        }


    @Test
    @Timeout(1)
    internal fun `should fail to start when a problem occurs`() = testCoroutineDispatcher.run {
        // given
        val directive = MinionsStartDirective(
            "my-campaign",
            "my-scenario",
            listOf(
                MinionStartDefinition("my-minion-1", 123),
                MinionStartDefinition("my-minion-2", 456),
                MinionStartDefinition("my-minion-3", 789)
            )
        )
        every { localAssignmentStore.hasRootUnderLoadLocally("my-scenario", any()) } returns true
        coEvery {
            minionsKeeper.scheduleMinionStart(
                "my-minion-1",
                Instant.ofEpochMilli(123)
            )
        } throws RuntimeException("A problem occurred")

        // when
        processor.notify(directive)

        // then
        coVerify {
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
            minionsKeeper.scheduleMinionStart("my-minion-1", Instant.ofEpochMilli(123))
            factoryChannel.publishFeedback(
                MinionsStartFeedback(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario",
                    status = FeedbackStatus.FAILED,
                    error = "A problem occurred"
                )
            )
        }
        confirmVerified(minionsKeeper, localAssignmentStore, factoryChannel)
    }
}
