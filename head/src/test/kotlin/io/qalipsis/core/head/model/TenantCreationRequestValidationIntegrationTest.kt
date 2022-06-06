package io.qalipsis.core.head.model

import assertk.all
import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.each
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isIn
import assertk.assertions.prop
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.validation.validator.Validator
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import javax.validation.ConstraintViolation
import javax.validation.Path


@MicronautTest
internal class TenantCreationRequestValidationIntegrationTest {

    @Inject
    private lateinit var validator: Validator

    @Test
    fun `should accept a tenant with a null reference`() {
        // given
        val request = TenantCreationRequest(null, "foo")

        // when
        val result = validator.validate(request)

        // then
        assertThat(result).isEmpty()
    }

    @Test
    fun `should accept a tenant with an empty reference`() {
        // given
        val request = TenantCreationRequest("", "foo")

        // when
        val result = validator.validate(request)

        // then
        assertThat(result).isEmpty()
    }

    @Test
    internal fun `should deny when the reference is too long`() {
        // given
        val request = TenantCreationRequest("a".repeat(51), "foo")

        // when
        val result = validator.validate(request)

        // then
        assertThat(result).all {
            hasSize(1)
            any {
                it.all {
                    prop(ConstraintViolation<TenantCreationRequest>::getPropertyPath).prop(Path::toString)
                        .isEqualTo(TenantCreationRequest::reference.name)
                    prop(ConstraintViolation<TenantCreationRequest>::getMessage).isEqualTo("size must be between 0 and 50")
                }
            }
        }
    }

    @Test
    internal fun `should deny when the name is too long`() {
        // given
        val request = TenantCreationRequest(null, "a".repeat(201))

        // when
        val result = validator.validate(request)

        // then
        assertThat(result).all {
            hasSize(1)
            any {
                it.all {
                    prop(ConstraintViolation<TenantCreationRequest>::getPropertyPath).prop(Path::toString)
                        .isEqualTo(TenantCreationRequest::displayName.name)
                    prop(ConstraintViolation<TenantCreationRequest>::getMessage).isEqualTo("size must be between 1 and 200")
                }
            }
        }
    }

    @Test
    internal fun `should deny when the name is blank`() {
        // given
        val request = TenantCreationRequest(null, "    ")

        // when
        val result = validator.validate(request)

        // then
        assertThat(result).all {
            hasSize(1)
            any {
                it.all {
                    prop(ConstraintViolation<TenantCreationRequest>::getPropertyPath).prop(Path::toString)
                        .isEqualTo(TenantCreationRequest::displayName.name)
                    prop(ConstraintViolation<TenantCreationRequest>::getMessage).isEqualTo("must not be blank")
                }
            }
        }
    }

    @Test
    internal fun `should deny when the name is empty`() {
        // given
        val request = TenantCreationRequest(null, "")

        // when
        val result = validator.validate(request)

        // then
        assertThat(result).all {
            hasSize(2)
            each {
                it.all {
                    prop(ConstraintViolation<TenantCreationRequest>::getPropertyPath).prop(Path::toString)
                        .isEqualTo(TenantCreationRequest::displayName.name)
                    prop(ConstraintViolation<TenantCreationRequest>::getMessage).isIn(
                        "must not be blank",
                        "size must be between 1 and 200"
                    )
                }
            }
        }
    }
}
