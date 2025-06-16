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
