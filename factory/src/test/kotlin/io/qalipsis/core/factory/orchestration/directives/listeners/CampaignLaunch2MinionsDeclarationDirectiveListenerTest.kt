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

import assertk.all
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.slot
import io.qalipsis.api.lang.IdGenerator
import io.qalipsis.core.directives.MinionsAssignmentDirective
import io.qalipsis.core.directives.MinionsDeclarationDirective
import io.qalipsis.core.directives.MinionsDeclarationDirectiveReference
import io.qalipsis.core.directives.TestDescriptiveDirective
import io.qalipsis.core.factory.communication.FactoryChannel
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.core.factory.orchestration.MinionAssignmentKeeper
import io.qalipsis.core.factory.orchestration.ScenarioRegistry
import io.qalipsis.core.factory.testDag
import io.qalipsis.core.factory.testScenario
import io.qalipsis.core.feedbacks.DirectiveFeedback
import io.qalipsis.core.feedbacks.FeedbackStatus
import io.qalipsis.core.feedbacks.MinionsDeclarationFeedback
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.verifyOnce
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * @author Eric Jessé
 */
@WithMockk
internal class CampaignLaunch2MinionsDeclarationDirectiveListenerTest {

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var scenarioRegistry: ScenarioRegistry

    @RelaxedMockK
    private lateinit var factoryChannel: FactoryChannel

    @RelaxedMockK
    private lateinit var idGenerator: IdGenerator

    @RelaxedMockK
    private lateinit var minionAssignmentKeeper: MinionAssignmentKeeper

    @RelaxedMockK
    private lateinit var factoryCampaignManager: FactoryCampaignManager

    @InjectMockKs
    private lateinit var processor: CampaignLaunch2MinionsDeclarationDirectiveListener

    @Test
    @Timeout(1)
    fun `should accept minions creation preparation directive`() {
        val directive = MinionsDeclarationDirectiveReference(
            "my-directive",
            "my-campaign",
            "my-scenario"
        )
        every { factoryCampaignManager.isLocallyExecuted("my-campaign", "my-scenario") } returns true

        assertTrue(processor.accept(directive))

        verifyOnce { factoryCampaignManager.isLocallyExecuted("my-campaign", "my-scenario") }
        confirmVerified(factoryCampaignManager)
    }

    @Test
    @Timeout(1)
    fun `should not accept not minions creation preparation directive`() {
        assertFalse(processor.accept(TestDescriptiveDirective()))
        confirmVerified(factoryCampaignManager)
    }

    @Test
    @Timeout(1)
    fun `should not accept minions creation preparation directive for unknown scenario`() {
        val directive = MinionsDeclarationDirectiveReference(
            "my-directive",
            "my-campaign",
            "my-scenario"
        )
        every { factoryCampaignManager.isLocallyExecuted("my-campaign", "my-scenario") } returns false

        assertFalse(processor.accept(directive))

        verifyOnce { factoryCampaignManager.isLocallyExecuted("my-campaign", "my-scenario") }
    }

    @Test
    @Timeout(5)
    fun `should declare and register all the minions for the scenario`() = testCoroutineDispatcher.runTest {
        // given
        val directive = MinionsDeclarationDirective(
            "my-campaign",
            "my-scenario",
            123,
            ""
        )

        val scenario = testScenario("my-scenario", minionsCount = 2) {
            this.createIfAbsent("my-dag-1") { testDag("my-dag-1", this, isUnderLoad = true) }
            this.createIfAbsent("my-dag-2") {
                testDag("my-dag-2", this, isUnderLoad = true, isSingleton = true)
            }
            this.createIfAbsent("my-dag-3") {
                testDag("my-dag-3", this, isUnderLoad = false, isSingleton = false)
            }
        }
        every { scenarioRegistry.get("my-scenario") } returns scenario
        val assignmentDirectiveSlot = slot<MinionsAssignmentDirective>()
        coJustRun { factoryChannel.publishDirective(capture(assignmentDirectiveSlot)) }
        val feedbacks = mutableListOf<DirectiveFeedback>()
        coJustRun { factoryChannel.publishFeedback(capture(feedbacks)) }

        // when
        processor.notify(directive)

        // then
        coVerifyOrder {
            factoryChannel.publishFeedback(
                MinionsDeclarationFeedback(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario",
                    status = FeedbackStatus.IN_PROGRESS
                )
            )
            scenarioRegistry["my-scenario"]
            minionAssignmentKeeper.registerMinionsToAssign(
                "my-campaign",
                "my-scenario",
                listOf("my-dag-2"),
                match { it.size == 1 && it.first().startsWith("my-scenario-lonely-") },
                false
            )
            minionAssignmentKeeper.registerMinionsToAssign(
                "my-campaign",
                "my-scenario",
                listOf("my-dag-3"),
                match { it.size == 1 && it.first().startsWith("my-scenario-lonely-") },
                false
            )
            minionAssignmentKeeper.registerMinionsToAssign(
                "my-campaign",
                "my-scenario",
                listOf("my-dag-1"),
                match { with(it) { size == 123 && all { it.startsWith("my-scenario-") } && none { it.startsWith("my-scenario-lonely-") } } },
                true
            )
            minionAssignmentKeeper.completeUnassignedMinionsRegistration("my-campaign", "my-scenario")
            factoryChannel.publishDirective(
                MinionsAssignmentDirective(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario",
                )
            )
            factoryChannel.publishFeedback(
                MinionsDeclarationFeedback(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario",
                    status = FeedbackStatus.COMPLETED
                )
            )
        }
        assertThat(assignmentDirectiveSlot.captured).all {
            prop(MinionsAssignmentDirective::scenarioName).isEqualTo("my-scenario")
            prop(MinionsAssignmentDirective::campaignKey).isEqualTo("my-campaign")
            prop(MinionsAssignmentDirective::channel).isEmpty()
        }

        confirmVerified(scenarioRegistry, factoryChannel, minionAssignmentKeeper, factoryCampaignManager)
    }

    @Test
    @Timeout(5)
    fun `should fail to declare and register all the minions for the scenario`() = testCoroutineDispatcher.runTest {
        // given
        val directive = MinionsDeclarationDirective(
            "my-campaign",
            "my-scenario",
            123,
            ""
        )
        val scenario = testScenario("my-scenario", minionsCount = 2) {
            this.createIfAbsent("my-dag-1") { testDag("my-dag-1", this, isUnderLoad = true) }
            this.createIfAbsent("my-dag-2") {
                testDag("my-dag-2", this, isUnderLoad = true, isSingleton = true)
            }
            this.createIfAbsent("my-dag-3") {
                testDag("my-dag-3", this, isUnderLoad = false, isSingleton = false)
            }
        }
        every { scenarioRegistry.get("my-scenario") } returns scenario
        coEvery {
            minionAssignmentKeeper.registerMinionsToAssign(
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } throws RuntimeException("A problem occurred")

        // when
        processor.notify(directive)

        // then
        coVerifyOrder {
            factoryChannel.publishFeedback(
                MinionsDeclarationFeedback(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario",
                    status = FeedbackStatus.IN_PROGRESS
                )
            )
            scenarioRegistry["my-scenario"]
            minionAssignmentKeeper.registerMinionsToAssign(any(), any(), any(), any(), any())
            factoryChannel.publishFeedback(
                MinionsDeclarationFeedback(
                    campaignKey = "my-campaign",
                    scenarioName = "my-scenario",
                    status = FeedbackStatus.FAILED,
                    error = "A problem occurred"
                )
            )
        }

        confirmVerified(scenarioRegistry, factoryChannel, minionAssignmentKeeper, factoryCampaignManager)
    }
}
