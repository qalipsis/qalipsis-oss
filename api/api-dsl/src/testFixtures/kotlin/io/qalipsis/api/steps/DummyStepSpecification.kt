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
