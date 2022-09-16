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

package io.qalipsis.core.factory.context

import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepName

/**
 * Helper class to build step contexts.
 *
 * @author Eric Jessé
 */
internal object StepContextBuilder {

    /**
     * Creates a context for next step when an input value is to be provided.
     */
    fun <A, I, O> next(input: I, ctx: StepContext<A, I>, stepName: StepName): StepContext<I, O> {
        return ctx.next(input, stepName)
    }

    /**
     * Creates a context for next step without input value.
     */
    @Suppress("UNCHECKED_CAST")
    fun <I, O> next(ctx: StepContext<I, O>, stepName: StepName): StepContext<Unit, O> {
        return ctx.next<O>(stepName) as StepContext<Unit, O>
    }
}
