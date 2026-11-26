/*
 * QALIPSIS
 * Copyright (C) 2026 AERIS IT Solutions GmbH
 *
 * This program is dual-licensed: you may use, modify and distribute it under the terms of either
 *
 *   1. the GNU Affero General Public License as published by the Free Software Foundation,
 *      either version 3 of the License, or (at your option) any later version; or
 *   2. a commercial license agreement concluded with AERIS IT Solutions GmbH.
 *
 * Under the GNU Affero General Public License, this program is distributed in the hope that it
 * will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * For the commercial license, see LICENSE-COMMERCIAL.md at the root of the repository.
 *
 */

package io.qalipsis.core.factory.steps.evolution

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameAs
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.qalipsis.api.context.StepContext
import io.qalipsis.api.steps.StepCreationContext
import io.qalipsis.api.steps.StepCreationContextImpl
import io.qalipsis.api.steps.verification.EvolutionDirection
import io.qalipsis.api.steps.verification.EvolutionVerificationStepSpecificationImpl
import io.qalipsis.test.assertk.prop
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.steps.AbstractStepSpecificationConverterTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Duration

@Suppress("UNCHECKED_CAST")
internal class EvolutionVerificationStepSpecificationConverterTest :
    AbstractStepSpecificationConverterTest<EvolutionVerificationStepSpecificationConverter>() {

    @RelaxedMockK
    private lateinit var countsRepository: EvolutionCountsRepository

    @RelaxedMockK
    private lateinit var configuration: EvolutionVerificationConfiguration

    @Test
    override fun `should support expected spec`() {
        assertThat(converter.support(relaxedMockk<EvolutionVerificationStepSpecificationImpl<*, *>>())).isTrue()
    }

    @Test
    override fun `should not support unexpected spec`() {
        assertThat(converter.support(relaxedMockk())).isFalse()
    }

    @Test
    fun `given a specification with an expectation when converting then the step is created`() = runTest {
        val keyExtractor: (StepContext<Int, Int>, Int) -> Any? = { _, input -> input % 2 }
        val valueExtractor: (Int) -> Int? = { input -> input }
        val spec = EvolutionVerificationStepSpecificationImpl(EvolutionDirection.DECREASING, keyExtractor, valueExtractor)
        spec.name = "my-step"
        spec.shouldSatisfy { percentile(97.5) }
        every { configuration.linger } returns Duration.ofMillis(500)
        val creationContext = StepCreationContextImpl(scenarioSpecification, directedAcyclicGraph, spec)

        converter.convert<Int, Int>(
            creationContext as StepCreationContext<EvolutionVerificationStepSpecificationImpl<*, *>>
        )

        assertThat(creationContext.createdStep!!).isInstanceOf(EvolutionVerificationStep::class).all {
            prop(EvolutionVerificationStep<*>::name).isEqualTo("my-step")
            prop("direction").isEqualTo(EvolutionDirection.DECREASING)
            prop("keyExtractor").isSameAs(keyExtractor)
            prop("valueExtractor").isSameAs(valueExtractor)
            prop("expectedPercentile").isEqualTo(97.5)
            prop("countsRepository").isSameAs(countsRepository)
            prop("linger").isEqualTo(Duration.ofMillis(500))
        }
    }
}
