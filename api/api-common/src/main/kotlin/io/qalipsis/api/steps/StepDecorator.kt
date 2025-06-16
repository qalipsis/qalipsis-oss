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

import io.qalipsis.api.context.CompletionContext
import io.qalipsis.api.context.MinionId
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.context.StepStartStopContext
import io.qalipsis.api.runtime.Minion

/**
 * Interface for any implementation of step decorator.
 *
 * @author Eric Jessé
 */
interface StepDecorator<I, O> : Step<I, O> {

    val decorated: Step<I, O>

    override fun addNext(nextStep: Step<*, *>) {
        decorated.addNext(nextStep)
    }

    override suspend fun init() {
        decorated.init()
        super.init()
    }

    override suspend fun destroy() {
        decorated.destroy()
        super.destroy()
    }

    override suspend fun start(context: StepStartStopContext) {
        decorated.start(context)
        super.start(context)
    }

    override suspend fun stop(context: StepStartStopContext) {
        decorated.stop(context)
        super.stop(context)
    }

    override suspend fun execute(minion: Minion, context: StepContext<I, O>) {
        decorated.execute(minion, context)
    }

    override suspend fun execute(context: StepContext<I, O>) {
        decorated.execute(context)
    }

    override suspend fun complete(completionContext: CompletionContext) {
        decorated.complete(completionContext)
    }

    override suspend fun discard(minionIds: Collection<MinionId>) {
        decorated.discard(minionIds)
    }
}
