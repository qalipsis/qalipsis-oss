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

import assertk.all
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import io.qalipsis.test.mockk.relaxedMockk
import org.junit.jupiter.api.Test
import java.time.Duration

internal class AbstractStepSpecificationTest {

    @Test
    internal fun `should build a default step`() {
        // when
        val specification = TestAbstractStepSpecification()

        // then
        assertThat(specification).all {
            prop(TestAbstractStepSpecification::retryPolicy).isNull()
            prop(TestAbstractStepSpecification::reporting).prop(StepReportingSpecification::reportErrors).isTrue()
            prop(TestAbstractStepSpecification::tags).isEmpty()
            prop(TestAbstractStepSpecification::iterations).isEqualTo(1)
            prop(TestAbstractStepSpecification::iterationPeriods).isEqualTo(Duration.ZERO)
            prop(TestAbstractStepSpecification::timeout).isNull()
            prop(TestAbstractStepSpecification::nextSteps).isEmpty()
        }
    }

    @Test
    internal fun `should disable the report`() {
        // given
        val specification = TestAbstractStepSpecification()

        // when
        specification.configure {
            report {
                reportErrors = false
            }
        }

        // then
        assertThat(specification).all {
            prop(TestAbstractStepSpecification::retryPolicy).isNull()
            prop(TestAbstractStepSpecification::reporting).prop(StepReportingSpecification::reportErrors).isFalse()
            prop(TestAbstractStepSpecification::tags).isEmpty()
            prop(TestAbstractStepSpecification::iterations).isEqualTo(1L)
            prop(TestAbstractStepSpecification::iterationPeriods).isEqualTo(Duration.ZERO)
            prop(TestAbstractStepSpecification::timeout).isNull()
            prop(TestAbstractStepSpecification::nextSteps).isEmpty()
        }
    }

    @Test
    internal fun `should enable the tags as map`() {
        // when
        val specification = TestAbstractStepSpecification()
        specification.tag(mapOf("key1" to "value1", "key2" to "value2"))

        // then
        assertThat(specification).all {
            prop(TestAbstractStepSpecification::retryPolicy).isNull()
            prop(TestAbstractStepSpecification::reporting).prop(StepReportingSpecification::reportErrors).isTrue()
            prop(TestAbstractStepSpecification::tags).isEqualTo(mapOf("key1" to "value1", "key2" to "value2"))
            prop(TestAbstractStepSpecification::iterations).isEqualTo(1L)
            prop(TestAbstractStepSpecification::iterationPeriods).isEqualTo(Duration.ZERO)
            prop(TestAbstractStepSpecification::timeout).isNull()
            prop(TestAbstractStepSpecification::nextSteps).isEmpty()
        }
    }

    @Test
    internal fun `should enable the tags as vararg of pairs`() {
        // when
        val specification = TestAbstractStepSpecification()
        specification.tag("key1" to "value1", "key2" to "value2")

        // then
        assertThat(specification).all {
            prop(TestAbstractStepSpecification::retryPolicy).isNull()
            prop(TestAbstractStepSpecification::reporting).prop(StepReportingSpecification::reportErrors).isTrue()
            prop(TestAbstractStepSpecification::tags).isEqualTo(mapOf("key1" to "value1", "key2" to "value2"))
            prop(TestAbstractStepSpecification::iterations).isEqualTo(1L)
            prop(TestAbstractStepSpecification::iterationPeriods).isEqualTo(Duration.ZERO)
            prop(TestAbstractStepSpecification::timeout).isNull()
            prop(TestAbstractStepSpecification::nextSteps).isEmpty()
        }
    }

    @Test
    internal fun `should enable the iteration`() {
        // when
        val specification = TestAbstractStepSpecification()
        specification.iterate(123, Duration.ofSeconds(2))

        // then
        assertThat(specification).all {
            prop(TestAbstractStepSpecification::retryPolicy).isNull()
            prop(TestAbstractStepSpecification::reporting).prop(StepReportingSpecification::reportErrors).isTrue()
            prop(TestAbstractStepSpecification::tags).isEmpty()
            prop(TestAbstractStepSpecification::iterations).isEqualTo(123L)
            prop(TestAbstractStepSpecification::iterationPeriods).isEqualTo(Duration.ofSeconds(2))
            prop(TestAbstractStepSpecification::timeout).isNull()
            prop(TestAbstractStepSpecification::nextSteps).isEmpty()
        }
    }

    @Test
    internal fun `should enable the timeout`() {
        // when
        val specification = TestAbstractStepSpecification()
        specification.timeout(2344)

        // then
        assertThat(specification).all {
            prop(TestAbstractStepSpecification::retryPolicy).isNull()
            prop(TestAbstractStepSpecification::reporting).prop(StepReportingSpecification::reportErrors).isTrue()
            prop(TestAbstractStepSpecification::tags).isEmpty()
            prop(TestAbstractStepSpecification::iterations).isEqualTo(1)
            prop(TestAbstractStepSpecification::iterationPeriods).isEqualTo(Duration.ZERO)
            prop(TestAbstractStepSpecification::timeout).isEqualTo(Duration.ofMillis(2344))
            prop(TestAbstractStepSpecification::nextSteps).isEmpty()
        }
    }

    @Test
    internal fun `should inherit the tags when not set in the next one`() {
        // when
        val specification = TestAbstractStepSpecification()
        specification.scenario = relaxedMockk()
        specification.tag("key1" to "value1", "key2" to "value2")
        val next = TestAbstractStepSpecification()
        specification.add(next)

        // then
        assertThat(next).prop(TestAbstractStepSpecification::tags)
            .isEqualTo(mapOf("key1" to "value1", "key2" to "value2"))
    }

    @Test
    internal fun `should not inherit the tags when already set in the next one`() {
        // when
        val specification = TestAbstractStepSpecification()
        specification.scenario = relaxedMockk()
        specification.tag("key1" to "value1", "key2" to "value2")
        val next = TestAbstractStepSpecification()
        next.tag("key3" to "value3", "key4" to "value4")
        specification.add(next)

        // then
        assertThat(next).prop(TestAbstractStepSpecification::tags)
            .isEqualTo(mapOf("key3" to "value3", "key4" to "value4"))
    }

    class TestAbstractStepSpecification : AbstractStepSpecification<Int, String, TestAbstractStepSpecification>()
}