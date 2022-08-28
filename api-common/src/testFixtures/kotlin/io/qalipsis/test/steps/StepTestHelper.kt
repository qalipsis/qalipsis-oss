/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
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
        campaignKey: CampaignKey = "",
        scenarioName: ScenarioName = "",
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
