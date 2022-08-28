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

package io.qalipsis.api.context

/**
 * Representation of an error in a step execution.
 *
 * @author Eric Jessé
 */
data class StepError(val message: String, var stepName: String = "") {
    constructor(cause: Throwable, stepName: String = "") : this(cause.message?.let {
        if (it.length > 1000) {
            it.take(1000) + "... (too long messages are truncated)"
        } else {
            it
        }
    } ?: "<No message>", stepName)
}
