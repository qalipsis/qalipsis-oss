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

package io.qalipsis.api.constraints

import javax.validation.Constraint
import javax.validation.Payload
import kotlin.reflect.KClass

/**
 * Constraint to validate that a [java.time.Duration] is positive or zero.
 *
 * @author Eric Jessé
 */
@Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.TYPE,
    AnnotationTarget.TYPE_PARAMETER,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.PROPERTY_GETTER
)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [PositiveOrZeroDurationValidator::class])
annotation class PositiveOrZeroDuration(

    val message: String = "duration should be positive or zero but was {validatedValue}",

    /**
     * Groups to control the order in which constraints are evaluated,
     * or to perform validation of the partial state of a JavaBean.
     */
    val groups: Array<KClass<*>> = [],

    /**
     * Payloads used by validation clients to associate some metadata information with a given constraint declaration
     */
    val payload: Array<KClass<out Payload>> = []
)