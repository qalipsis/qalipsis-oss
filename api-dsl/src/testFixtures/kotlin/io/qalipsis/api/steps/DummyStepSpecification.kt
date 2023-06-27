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

import io.qalipsis.api.scenario.StepSpecificationRegistry
import io.qalipsis.api.scenario.TestScenarioFactory
import kotlin.reflect.KClass

/**
 * Step specification used to capture the type of the input data.
 *
 * For test only.
 *
 * @author Eric Jessé
 */
class InputTypeCaptor<I : Any, O : Any>(
    val inputClass: KClass<I>
) : AbstractStepSpecification<I, O, InputTypeCaptor<I, O>>() {

    override fun add(step: StepSpecification<*, *, *>) {
        step.scenario = this.scenario
        super.add(step)
    }

}

/**
 * Creates a [InputTypeCaptor] having [outputClass] as output type.
 *
 */
inline fun <reified INPUT : Any, OUTPUT : Any> StepSpecification<*, INPUT, *>.captureInputAndReturn(
    @Suppress("UNUSED_PARAMETER") outputClass: KClass<OUTPUT>
): InputTypeCaptor<INPUT, OUTPUT> {
    val step = InputTypeCaptor<INPUT, OUTPUT>(INPUT::class)
    this.add(step)
    return step
}

/**
 * Step specification of a step as root of scenario, generating integers.
 *
 * For test only.
 *
 * @author Eric Jessé
 */
class DummyStepSpecification : AbstractStepSpecification<Unit, Int, DummyStepSpecification>() {

    init {
        scenario = TestScenarioFactory.scenario() as StepSpecificationRegistry
    }

    override fun add(step: StepSpecification<*, *, *>) {
        step.scenario = this.scenario
        super.add(step)
    }
}

/**
 * Creates a [DummyStepSpecification] as next step.
 */
fun <INPUT> StepSpecification<*, INPUT, *>.dummy(name: String? = null): DummyStepSpecification {
    val step = DummyStepSpecification()
    name?.let { step.name = name }
    this.add(step)
    return step
}
