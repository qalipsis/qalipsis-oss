/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
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

package io.qalipsis.test.steps

import io.qalipsis.api.context.CampaignKey
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.ScenarioName
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepError
import io.qalipsis.api.context.StepName
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel

/**
 *
 * @author Eric Jessé
 */
object StepTestHelper {

    @SuppressWarnings("kotlin:S107")
    fun <IN, OUT> createStepContext(
        input: IN? = null,
        outputChannel: SendChannel<StepContext.StepOutputRecord<OUT>> = Channel(100),
        errors: MutableList<StepError> = mutableListOf(),
        minionId: MinionId = "my-minion",
        campaignKey: CampaignKey = "my-campaign",
        scenarioName: ScenarioName = "my-scenario",
        previousStepName: StepName = "my-previous-step",
        stepName: StepName = "my-step",
        stepIterationIndex: Long = 0,
        isExhausted: Boolean = false,
        isTail: Boolean = false
    ): TestStepContext<IN, OUT> {
        val inputChannel = Channel<IN>(1)
        input?.let {
            inputChannel.trySend(it)
        }
        return TestStepContext(
            inputChannel,
            outputChannel,
            errors,
            campaignKey,
            minionId,
            scenarioName,
            previousStepName,
            stepName,
            "",
            "",
            stepIterationIndex,
            isExhausted,
            isTail
        )
    }

}
