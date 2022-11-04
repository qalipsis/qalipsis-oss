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

package io.qalipsis.core.factory.steps

import io.mockk.confirmVerified
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.context.DefaultCompletionContext
import io.qalipsis.core.factory.orchestration.FactoryCampaignManager
import io.qalipsis.test.coroutines.TestDispatcherProvider
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant

@WithMockk
internal class DeadEndStepTest {

    @JvmField
    @RegisterExtension
    val testCoroutineDispatcher = TestDispatcherProvider()

    @RelaxedMockK
    private lateinit var factoryCampaignManager: FactoryCampaignManager

    @Test
    @Timeout(1)
    internal fun `should do nothing when executing`() =
        testCoroutineDispatcher.runTest {
            // given
            val step = DeadEndStep<Int>("", "this-is-my-dag", factoryCampaignManager)
            val ctx = StepTestHelper.createStepContext<Int, Unit>(
                input = 1,
                campaignKey = "my-campaign",
                scenarioName = "my-scenario",
                minionId = "my-minion"
            )

            // when
            step.execute(ctx)

            //then
            Assertions.assertTrue((ctx.input as Channel).isEmpty)
            Assertions.assertTrue((ctx.output as Channel).isEmpty)
            Assertions.assertFalse((ctx.output as Channel).isClosedForReceive)
            confirmVerified(factoryCampaignManager)
        }

    @Test
    @Timeout(1)
    internal fun `should notify DAG completion when complete is called`() =
        testCoroutineDispatcher.runTest {
            // given
            val step = DeadEndStep<Int>("", "this-is-my-dag", factoryCampaignManager)
            val ctx = DefaultCompletionContext(
                campaignKey = "my-campaign",
                scenarioName = "my-scenario",
                minionId = "my-minion",
                minionStart = 4353443L,
                lastExecutedStepName = "step-1",
                errors = emptyList()
            )

            // when
            step.complete(ctx)

            //then
            coVerifyOnce {
                factoryCampaignManager.notifyCompleteMinion(
                    "my-minion",
                    Instant.ofEpochMilli(4353443L),
                    "my-campaign",
                    "my-scenario",
                    "this-is-my-dag"
                )
            }
            confirmVerified(factoryCampaignManager)
        }
}