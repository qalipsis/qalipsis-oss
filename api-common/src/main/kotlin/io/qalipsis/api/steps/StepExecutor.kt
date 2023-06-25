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

package io.qalipsis.api.steps

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.runtime.Minion

/**
 * Default interface for the components executing a step either directly or with its retry policy.
 *
 * @author Eric Jessé
 */
interface StepExecutor {

    /**
     * Executes a step either using its retry policy if defined or directly otherwise.
     */
    suspend fun <I, O> executeStep(minion: Minion, step: Step<I, O>, stepContext: StepContext<I, O>) {
        val retryPolicy = step.retryPolicy
        if (retryPolicy != null) {
            log.trace { "Executing the step ${step.name} with retry policy of type ${retryPolicy.javaClass.canonicalName} and context $stepContext" }
            retryPolicy.execute(stepContext) { step.execute(minion, it) }
        } else {
            log.trace { "Executing the step ${step.name} without retry policy and with context $stepContext" }
            step.execute(minion, stepContext)
        }
    }

    private companion object {

        val log = logger()

    }

}
