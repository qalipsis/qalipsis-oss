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
