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

package io.qalipsis.api.steps.datasource

import io.qalipsis.api.context.StepStartStopContext

/**
 * Reads objects from a datasource in an iterative way.
 *
 * @param R the type of the object read and returned
 *
 * @author Eric Jessé
 */
interface DatasourceIterativeReader<R> {

    fun start(context: StepStartStopContext) = Unit

    fun stop(context: StepStartStopContext) = Unit

    /**
     * Returns `true` if the iteration has more elements.
     */
    suspend operator fun hasNext(): Boolean

    /**
     * Returns the next element in the iteration.
     */
    suspend operator fun next(): R
}
