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

package io.qalipsis.core.factory.steps.singleton

import io.qalipsis.api.context.StepName
import io.qalipsis.api.steps.Step
import io.qalipsis.api.steps.StepDecorator

/**
 * Decorator for a step on which no more step step can be added.
 *
 * @author Eric Jessé
 */
class NoMoreNextStepDecorator<I, O>(
    override val decorated: Step<I, O>
) : Step<I, O>, StepDecorator<I, O> {

    override val name: StepName = decorated.name

    override var retryPolicy = decorated.retryPolicy

    override val next: List<Step<O, *>>
        get() = decorated.next

    override fun addNext(nextStep: Step<*, *>) {
        // Do nothing.
    }

}
