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

import io.qalipsis.api.context.StepOutput
import io.qalipsis.api.context.StepStartStopContext
import java.util.concurrent.atomic.AtomicLong

/**
 * Converts a value read from a datasource into a data that can be forwarded to next steps and sends it to the output channel.
 *
 * @param R type of the object to convert
 * @param O type of the result
 *
 * @author Eric Jessé
 */
interface DatasourceObjectConverter<R, O> {

    fun start(context: StepStartStopContext) = Unit

    fun stop(context: StepStartStopContext) = Unit

    /**
     * Sends [value] to the [output] channel in any form.
     *
     * @param offset an reference to the offset, it is up to the implementation to increment it
     * @param value input value to send after any conversion to the output
     * @param output channel to received the data after conversion
     */
    suspend fun supply(offset: AtomicLong, value: R, output: StepOutput<O>)

}
