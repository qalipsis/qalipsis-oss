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

import assertk.assertThat
import assertk.assertions.isInstanceOf
import io.qalipsis.api.context.StepContext
import io.qalipsis.test.steps.StepTestHelper
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * @author Eric Jessé
 */
internal class CatchExhaustedContextStepSpecificationTest {

    @Test
    internal fun `should add exhausted context catcher as next`() {
        val previousStep = DummyStepSpecification()
        val specification: suspend (context: StepContext<*, String>) -> Unit = { _ -> }
        previousStep.catchExhaustedContext(specification)

        assertEquals(CatchExhaustedContextStepSpecification(specification), previousStep.nextSteps[0])
    }

    @Test
    internal fun `should add recover step as next`() = runBlockingTest {
        val previousStep = DummyStepSpecification()
        previousStep.recover()

        assertThat(previousStep.nextSteps[0]).isInstanceOf(CatchExhaustedContextStepSpecification::class)

        @Suppress("UNCHECKED_CAST") val stepSpecification =
            (previousStep.nextSteps[0] as CatchExhaustedContextStepSpecification).block as (suspend (context: StepContext<*, Unit>) -> Unit)
        val stepContext = StepTestHelper.createStepContext<Any?, Unit>(
            minionId = "",
            scenarioName = "",
            stepName = "",
            isExhausted = true
        )
        stepSpecification(stepContext as StepContext<*, Unit>)

        assertFalse(stepContext.isExhausted)
    }
}
