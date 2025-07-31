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

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepError
import io.qalipsis.api.context.StepName
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.retry.RetryPolicy
import io.qalipsis.api.steps.AbstractStep

/**
 *
 * Step in charge of generating errors for each record not matching the specification.
 *
 * The step forward the input to the output to let the processing going on. It is generally associated
 * to a {@link CatchErrorStep} in order to analyze the errors and decide what to do with the record.
 *
 * @author Eric Jessé
 */
class ValidationStep<I>(
    id: StepName,
    retryPolicy: RetryPolicy?,
    private val specification: ((input: I) -> List<StepError>)
) : AbstractStep<I, I>(id, retryPolicy) {

    override suspend fun execute(context: StepContext<I, I>) {
        val input = context.receive()
        val errors = specification(input)
        errors.forEach(context::addError)
        context.send(input)
    }

    companion object {
        @JvmStatic
        private val log = logger()
    }
}
