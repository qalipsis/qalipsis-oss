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
import io.qalipsis.api.report.query.QueryAggregationOperator
import io.qalipsis.api.report.query.QueryClauseOperator
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.report.DataType
import io.qalipsis.core.head.model.DataSeries
import io.qalipsis.core.head.model.DataSeriesFilter
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
            dataType = DataType.EVENTS
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
            dataType = DataType.EVENTS
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
            dataType = DataType.EVENTS
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
            dataType = DataType.EVENTS
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