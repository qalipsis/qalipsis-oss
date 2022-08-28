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
import java.util.concurrent.atomic.AtomicLong

/**
 * Validates and/or transform an object read from a datasource.
 *
 * @param R type of the object to process
 * @param O type of the result
 *
 * @author Eric Jessé
 */
interface DatasourceObjectProcessor<R, O> {

    fun start(context: StepStartStopContext) = Unit

    fun stop(context: StepStartStopContext) = Unit

    /**
     * Validates and/or transform an object and returns either the object itself or a different representation of it.
     *
     * @param offset an reference to the offset of the current value, it should not be changed by the implementation
     * @param readObject input directly received from the reader.
     */
    fun process(offset: AtomicLong, readObject: R): O

}
