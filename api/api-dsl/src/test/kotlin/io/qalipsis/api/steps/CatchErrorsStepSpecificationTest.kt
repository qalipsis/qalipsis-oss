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

import io.aerisconsulting.catadioptre.getProperty
import io.mockk.every
import io.mockk.verifyOrder
import io.qalipsis.api.context.StepError
import io.qalipsis.test.mockk.WithMockk
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.Logger

/**
 * @author Eric Jessé
 */
@WithMockk
internal class CatchErrorsStepSpecificationTest {

    @Test
    internal fun `should add error catcher as next`() {
        val previousStep = DummyStepSpecification()
        val specification: (error: Collection<StepError>) -> Unit = { _ -> }
        previousStep.catchErrors(specification)

        assertEquals(CatchErrorsStepSpecification<Int>(specification), previousStep.nextSteps[0])
    }

    @Test
    internal fun `should add error logger as next`() {
        val previousStep = DummyStepSpecification()
        val logger = relaxedMockk<Logger> { }
        previousStep.logErrors(logger)

        assertTrue(previousStep.nextSteps[0] is CatchErrorsStepSpecification<*>)
        val specification = previousStep.nextSteps[0].getProperty<(Collection<StepError>) -> Unit>("block")
        val exceptions = listOf<Exception>(relaxedMockk {
            every { message } returns "Message-1"
        }, relaxedMockk {
            every { message } returns "Message-2"
        })
        val errors = exceptions.map { StepError(it) }
        specification(errors)
        verifyOrder {
            logger.error(eq("Message-1"))
            logger.error(eq("Message-2"))
        }
    }

}