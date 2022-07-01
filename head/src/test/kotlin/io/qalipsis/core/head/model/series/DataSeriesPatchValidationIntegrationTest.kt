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
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.core.head.model.ColorDataSeriesPatch
import io.qalipsis.core.head.model.DisplayFormatDataSeriesPatch
import io.qalipsis.core.head.model.DisplayNameDataSeriesPatch
import io.qalipsis.core.head.model.FieldNameDataSeriesPatch
import io.qalipsis.core.head.model.TimeframeUnitDataSeriesPatch
import jakarta.inject.Inject
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import javax.validation.ConstraintViolation

@MicronautTest(environments = [ExecutionEnvironments.HEAD, ExecutionEnvironments.SINGLE_HEAD], startApplication = false)
internal class DataSeriesPatchValidationIntegrationTest {

    @Inject
    private lateinit var validator: Validator

    @Nested
    inner class `Display name` {

        @Test
        internal fun `should accept valid display name patch`() {
            // given
            val patch = DisplayNameDataSeriesPatch("the-name")

            // when
            val violations = validator.validate(patch)

            // then
            assertThat(violations).isEmpty()
        }

        @Test
        internal fun `should deny a blank display name`() {
            // given
            val patch = DisplayNameDataSeriesPatch("   ")

            // when
            val violations = validator.validate(patch)

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
            val patch = DisplayNameDataSeriesPatch("aa")

            // when
            val violations = validator.validate(patch)

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
            val patch = DisplayNameDataSeriesPatch("a".repeat(201))

            // when
            val violations = validator.validate(patch)

            // then
            assertThat(violations).all {
                hasSize(1)
                transform { it.first() }.all {
                    prop(ConstraintViolation<*>::getPropertyPath).any { it.name.equals("displayName") }
                    prop(ConstraintViolation<*>::getMessage).isEqualTo("size must be between 3 and 200")
                }
            }
        }
    }

    @Nested
    inner class `Color` {

        @Test
        internal fun `should accept valid color patch`() {
            // given
            val patch = ColorDataSeriesPatch("#FF761C")

            // when
            val violations = validator.validate(patch)

            // then
            assertThat(violations).isEmpty()
        }

        @Test
        internal fun `should deny a non hexadecimal color`() {
            // given
            val patch = ColorDataSeriesPatch("12345abcdef")

            // when
            var violations = validator.validate(patch)

            // then
            assertThat(violations).all {
                hasSize(1)
                transform { it.first() }.all {
                    prop(ConstraintViolation<*>::getPropertyPath).any { it.name.equals("color") }
                    prop(ConstraintViolation<*>::getMessage).isEqualTo("must match \"^#[0-9a-fA-F]{6}\$\"")
                }
            }

            // when
            violations = validator.validate(ColorDataSeriesPatch("#FF761G"))

            // then
            assertThat(violations).all {
                hasSize(1)
                transform { it.first() }.all {
                    prop(ConstraintViolation<*>::getPropertyPath).any { it.name.equals("color") }
                    prop(ConstraintViolation<*>::getMessage).isEqualTo("must match \"^#[0-9a-fA-F]{6}\$\"")
                }
            }
        }
    }


    @Nested
    inner class `Field name` {

        @Test
        internal fun `should accept valid field name patch`() {
            // given
            val patch = FieldNameDataSeriesPatch("the-name")

            // when
            val violations = validator.validate(patch)

            // then
            assertThat(violations).isEmpty()
        }

        @Test
        internal fun `should deny a blank field name`() {
            // given
            val patch = FieldNameDataSeriesPatch("   ")

            // when
            val violations = validator.validate(patch)

            // then
            assertThat(violations).all {
                hasSize(1)
                transform { it.first() }.all {
                    prop(ConstraintViolation<*>::getPropertyPath).any { it.name.equals("fieldName") }
                    prop(ConstraintViolation<*>::getMessage).isEqualTo("must not be blank")
                }
            }
        }

        @Test
        internal fun `should deny a long field name`() {
            // given
            val patch = FieldNameDataSeriesPatch("a".repeat(61))

            // when
            val violations = validator.validate(patch)

            // then
            assertThat(violations).all {
                hasSize(1)
                transform { it.first() }.all {
                    prop(ConstraintViolation<*>::getPropertyPath).any { it.name.equals("fieldName") }
                    prop(ConstraintViolation<*>::getMessage).isEqualTo("size must be between 0 and 60")
                }
            }
        }
    }

    @Nested
    inner class `Time Frame Unit` {

        @Test
        internal fun `should accept valid time frame unit patch`() {
            // given
            val patch = TimeframeUnitDataSeriesPatch(Duration.ofSeconds(1))

            // when
            val violations = validator.validate(patch)

            // then
            assertThat(violations).isEmpty()
        }

        @Test
        internal fun `should deny a zero or negative time fame unit`() {
            // given
            val patch = TimeframeUnitDataSeriesPatch(Duration.ofSeconds(-1))

            // when
            var violations = validator.validate(patch)

            // then
            assertThat(violations).all {
                hasSize(1)
                transform { it.first() }.all {
                    prop(ConstraintViolation<*>::getPropertyPath).any { it.name.equals("timeframeUnit") }
                    prop(ConstraintViolation<*>::getMessage).isEqualTo("duration should be strictly positive but was PT-1S")
                }
            }

            // when
            violations = validator.validate(TimeframeUnitDataSeriesPatch(Duration.ZERO))

            // then
            assertThat(violations).all {
                hasSize(1)
                transform { it.first() }.all {
                    prop(ConstraintViolation<*>::getPropertyPath).any { it.name.equals("timeframeUnit") }
                    prop(ConstraintViolation<*>::getMessage).isEqualTo("duration should be strictly positive but was PT0S")
                }
            }
        }
    }

    @Nested
    inner class `Display format` {

        @Test
        internal fun `should accept valid display format patch`() {
            // given
            val patch = DisplayFormatDataSeriesPatch("the-format")

            // when
            val violations = validator.validate(patch)

            // then
            assertThat(violations).isEmpty()
        }

        @Test
        internal fun `should deny a long display format`() {
            // given
            val patch = DisplayFormatDataSeriesPatch("a".repeat(21))

            // when
            val violations = validator.validate(patch)

            // then
            assertThat(violations).all {
                hasSize(1)
                transform { it.first() }.all {
                    prop(ConstraintViolation<*>::getPropertyPath).any { it.name.equals("displayFormat") }
                    prop(ConstraintViolation<*>::getMessage).isEqualTo("size must be between 0 and 20")
                }
            }
        }
    }


}