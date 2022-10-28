/*
 * QALIPSIS
 * Copyright (C) 2022 AERIS IT Solutions GmbH
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

package io.qalipsis.core.head.model.series

import assertk.all
import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.validation.validator.Validator
import io.qalipsis.api.query.QueryAggregationOperator
import io.qalipsis.api.query.QueryClauseOperator
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.model.DataSeries
import io.qalipsis.core.head.model.DataSeriesFilter
import io.qalipsis.core.head.report.DataType
import jakarta.inject.Inject
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import javax.validation.ConstraintViolation

@MicronautTest(environments = [ExecutionEnvironments.HEAD, ExecutionEnvironments.SINGLE_HEAD], startApplication = false)
internal class DataSeriesValidationIntegrationTest {

    @Inject
    private lateinit var validator: Validator

    @Test
    internal fun `should accept a complete valid instance`() {
        // given
        val dataSeries = DataSeries(
            displayName = "the-name",
            dataType = DataType.EVENTS,
            valueName = "the-value-name",
            color = "#FF761C",
            filters = setOf(DataSeriesFilter("field-1", QueryClauseOperator.IS_IN, "A,B")),
            fieldName = "the-field",
            aggregationOperation = QueryAggregationOperator.AVERAGE,
            timeframeUnit = Duration.ofSeconds(2),
            displayFormat = "#0.000"
        )

        // when
        val violations = validator.validate(dataSeries)

        // then
        assertThat(violations).isEmpty()
    }

    @Test
    internal fun `should accept a minimal valid instance`() {
        // given
        val dataSeries = DataSeries(
            displayName = "the-name",
            dataType = DataType.EVENTS,
                    valueName = "the-value-name",
        )

        // when
        val violations = validator.validate(dataSeries)

        // then
        assertThat(violations).isEmpty()
    }

    @Test
    internal fun `should deny a blank display name`() {
        // given
        val dataSeries = DataSeries(
            displayName = "   ",
            dataType = DataType.EVENTS,
            valueName = "the-value-name",
        )

        // when
        val violations = validator.validate(dataSeries)

        // then
        assertThat(violations).all {
            hasSize(1)
            transform { it.first() }.all {
                prop(ConstraintViolation<*>::getPropertyPath).any { it.name.equals("displayName") }
                prop(ConstraintViolation<*>::getMessage).isEqualTo("must not be blank")
            }
        }
    }

    @Test
    internal fun `should deny a short display name`() {
        // given
        val dataSeries = DataSeries(
            displayName = "aa",
            dataType = DataType.EVENTS,
            valueName = "the-value-name",
        )

        // when
        val violations = validator.validate(dataSeries)

        // then
        assertThat(violations).all {
            hasSize(1)
            transform { it.first() }.all {
                prop(ConstraintViolation<*>::getPropertyPath).any { it.name.equals("displayName") }
                prop(ConstraintViolation<*>::getMessage).isEqualTo("size must be between 3 and 200")
            }
        }
    }

    @Test
    internal fun `should deny a long display name`() {
        // given
        val dataSeries = DataSeries(
            displayName = "a".repeat(201),
            dataType = DataType.EVENTS,
            valueName = "the-value-name",
        )

        // when
        val violations = validator.validate(dataSeries)

        // then
        assertThat(violations).all {
            hasSize(1)
            transform { it.first() }.all {
                prop(ConstraintViolation<*>::getPropertyPath).any { it.name.equals("displayName") }
                prop(ConstraintViolation<*>::getMessage).isEqualTo("size must be between 3 and 200")
            }
        }
    }

    @Test
    internal fun `should deny a non hexadecimal color`() {
        // given
        val dataSeries = DataSeries(
            displayName = "the-name",
            dataType = DataType.EVENTS,
            valueName = "the-value-name",
            color = "12345abcdef"
        )

        // when
        var violations = validator.validate(dataSeries)

        // then
        assertThat(violations).all {
            hasSize(1)
            transform { it.first() }.all {
                prop(ConstraintViolation<*>::getPropertyPath).any { it.name.equals("color") }
                prop(ConstraintViolation<*>::getMessage).isEqualTo("must match \"^#[0-9a-fA-F]{6}\$\"")
            }
        }

        // when
        violations = validator.validate(dataSeries.copy(color = "#FF761G"))

        // then
        assertThat(violations).all {
            hasSize(1)
            transform { it.first() }.all {
                prop(ConstraintViolation<*>::getPropertyPath).any { it.name.equals("color") }
                prop(ConstraintViolation<*>::getMessage).isEqualTo("must match \"^#[0-9a-fA-F]{6}\$\"")
            }
        }
    }

    @Test
    internal fun `should deny a long field name`() {
        // given
        val dataSeries = DataSeries(
            displayName = "the-name",
            dataType = DataType.EVENTS,
            valueName = "the-value-name",
            fieldName = "a".repeat(61)
        )

        // when
        val violations = validator.validate(dataSeries)

        // then
        assertThat(violations).all {
            hasSize(1)
            transform { it.first() }.all {
                prop(ConstraintViolation<*>::getPropertyPath).any { it.name.equals("fieldName") }
                prop(ConstraintViolation<*>::getMessage).isEqualTo("size must be between 0 and 60")
            }
        }
    }

    @Test
    internal fun `should deny a zero or negative time fame unit`() {
        // given
        val dataSeries = DataSeries(
            displayName = "the-name",
            dataType = DataType.EVENTS,
            valueName = "the-value-name",
            timeframeUnit = Duration.ofSeconds(-1)
        )

        // when
        var violations = validator.validate(dataSeries)

        // then
        assertThat(violations).all {
            hasSize(1)
            transform { it.first() }.all {
                prop(ConstraintViolation<*>::getPropertyPath).any { it.name.equals("timeframeUnit") }
                prop(ConstraintViolation<*>::getMessage).isEqualTo("duration should be strictly positive but was PT-1S")
            }
        }

        // when
        violations = validator.validate(dataSeries.copy(timeframeUnit = Duration.ZERO))

        // then
        assertThat(violations).all {
            hasSize(1)
            transform { it.first() }.all {
                prop(ConstraintViolation<*>::getPropertyPath).any { it.name.equals("timeframeUnit") }
                prop(ConstraintViolation<*>::getMessage).isEqualTo("duration should be strictly positive but was PT0S")
            }
        }
    }

    @Test
    internal fun `should deny a long display format`() {
        // given
        val dataSeries = DataSeries(
            displayName = "the-name",
            dataType = DataType.EVENTS,
            valueName = "the-value-name",
            displayFormat = "a".repeat(21),
        )

        // when
        val violations = validator.validate(dataSeries)

        // then
        assertThat(violations).all {
            hasSize(1)
            transform { it.first() }.all {
                prop(ConstraintViolation<*>::getPropertyPath).any { it.name.equals("displayFormat") }
                prop(ConstraintViolation<*>::getMessage).isEqualTo("size must be between 0 and 20")
            }
        }
    }

    @Test
    @Disabled("FIXME: the @Valid annotation of the property filters is not applied, despite the Kotlin compiler option -Xemit-jvm-type-annotations")
    internal fun `should deny with an invalid filter`() {
        // given
        val dataSeries = DataSeries(
            displayName = "the-name",
            dataType = DataType.EVENTS,
            valueName = "the-value-name",
            filters = setOf(DataSeriesFilter("", QueryClauseOperator.IS_IN, "A,B")),
        )

        // when
        val violations = validator.validate(dataSeries)

        // then
        assertThat(violations).all {
            hasSize(1)
            transform { it.first() }.all {
                prop(ConstraintViolation<*>::getPropertyPath).any { it.name.equals("name") }
                prop(ConstraintViolation<*>::getMessage).isEqualTo("size must not be blank")
            }
        }
    }

    @Nested
    inner class DataSeriesFilter {

        @Test
        internal fun `should deny a filter with blank name`() {
            // given
            val filter = DataSeriesFilter("   ", QueryClauseOperator.IS_IN, "A,B")

            // when
            val violations = validator.validate(filter)

            // then
            assertThat(violations).all {
                hasSize(1)
                transform { it.first() }.all {
                    prop(ConstraintViolation<*>::getPropertyPath).any { it.name.equals("name") }
                    prop(ConstraintViolation<*>::getMessage).isEqualTo("must not be blank")
                }
            }
        }

        @Test
        internal fun `should deny a filter with long name`() {
            // given
            val filter = DataSeriesFilter("A".repeat(61), QueryClauseOperator.IS_IN, "A,B")

            // when
            val violations = validator.validate(filter)

            // then
            assertThat(violations).all {
                hasSize(1)
                transform { it.first() }.all {
                    prop(ConstraintViolation<*>::getPropertyPath).any { it.name.equals("name") }
                    prop(ConstraintViolation<*>::getMessage).isEqualTo("size must be between 1 and 60")
                }
            }
        }

        @Test
        internal fun `should deny a filter with blank value`() {
            // given
            val filter = DataSeriesFilter("the-name", QueryClauseOperator.IS_IN, "   ")

            // when
            val violations = validator.validate(filter)

            // then
            assertThat(violations).all {
                hasSize(1)
                transform { it.first() }.all {
                    prop(ConstraintViolation<*>::getPropertyPath).any { it.name.equals("value") }
                    prop(ConstraintViolation<*>::getMessage).isEqualTo("must not be blank")
                }
            }
        }

        @Test
        internal fun `should deny a filter with long value`() {
            // given
            val filter = DataSeriesFilter("the-name", QueryClauseOperator.IS_IN, "A".repeat(201))

            // when
            val violations = validator.validate(filter)

            // then
            assertThat(violations).all {
                hasSize(1)
                transform { it.first() }.all {
                    prop(ConstraintViolation<*>::getPropertyPath).any { it.name.equals("value") }
                    prop(ConstraintViolation<*>::getMessage).isEqualTo("size must be between 1 and 200")
                }
            }
        }
    }
}