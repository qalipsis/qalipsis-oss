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

import io.qalipsis.api.context.StepName
import io.qalipsis.api.retry.RetryPolicy

/**
 * Simple super class of steps in order to perform generic operations without redundancy.
 *
 * @author Eric Jessé
 */
abstract class AbstractStep<I, O>(override val name: StepName, override var retryPolicy: RetryPolicy?) : Step<I, O> {

    override val next: MutableList<Step<O, *>> = mutableListOf()

    override fun addNext(nextStep: Step<*, *>) {
        @Suppress("UNCHECKED_CAST")
        next.add(nextStep as Step<O, *>)
    }
}
