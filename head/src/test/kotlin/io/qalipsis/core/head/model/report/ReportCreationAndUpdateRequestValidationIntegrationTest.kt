package io.qalipsis.core.head.model.report

import assertk.all
import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.validation.validator.Validator
import io.qalipsis.core.head.model.DiagramCreationAndUpdateRequest
import io.qalipsis.core.head.model.ReportCreationAndUpdateRequest
import io.qalipsis.core.head.model.DataTableCreationAndUpdateRequest
import io.qalipsis.core.head.report.SharingMode
import jakarta.inject.Inject
import org.junit.jupiter.api.Test
import javax.validation.ConstraintViolation

/**
 * @author Joël Valère
 */

@MicronautTest(startApplication = false)
internal class ReportCreationAndUpdateRequestValidationIntegrationTest {

    @Inject
    private lateinit var validator: Validator

    @Test
    internal fun `should accept a report for creation with only display name`() {
        val reportCreationAndUpdateRequest = ReportCreationAndUpdateRequest(
            displayName = "report-name"
        )

        // when
        val violations = validator.validate(reportCreationAndUpdateRequest)

        // then
        assertThat(violations).isEmpty()
    }

    @Test
    internal fun `should accept a report for creation with all fields`() {
        val reportCreationAndUpdateRequest = ReportCreationAndUpdateRequest(
            displayName = "report-name",
            sharingMode = SharingMode.WRITE,
            campaignKeys = listOf("campaign-key1", "campaign-key2"),
            campaignNamesPatterns = listOf("*", "\\w"),
            scenarioNamesPatterns = listOf("\\w"),
            dataComponents = listOf(
                DiagramCreationAndUpdateRequest(
                    dataSeriesReferences = listOf(
                        "series-ref-1"
                    )
                ),
                DataTableCreationAndUpdateRequest(
                    dataSeriesReferences = listOf(
                        "series-ref-2"
                    )
                )
            )
        )

        // when
        val violations = validator.validate(reportCreationAndUpdateRequest)

        // then
        assertThat(violations).isEmpty()
    }

    @Test
    internal fun `should deny a report for creation with blank display name`() {
        // given
        val reportCreationAndUpdateRequest = ReportCreationAndUpdateRequest(
            displayName = "   ",
        )

        // when
        val violations = validator.validate(reportCreationAndUpdateRequest)

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
    internal fun `should deny a report for creation with short display name`() {
        val reportCreationAndUpdateRequest = ReportCreationAndUpdateRequest(
            displayName = ""
        )

        // when
        val violations = validator.validate(reportCreationAndUpdateRequest)

        // then
        assertThat(
            violations.map {
                it.message
            }
        ).all {
            hasSize(2)
            containsExactlyInAnyOrder("must not be blank", "size must be between 1 and 200")
        }
    }

    @Test
    internal fun `should deny a report for creation with long display name`() {
        val reportCreationAndUpdateRequest = ReportCreationAndUpdateRequest(
            displayName = "a".repeat(201)
        )

        // when
        val violations = validator.validate(reportCreationAndUpdateRequest)

        // then
        assertThat(violations).all {
            hasSize(1)
            transform { it.first() }.all {
                prop(ConstraintViolation<*>::getPropertyPath).any { it.name.equals("displayName") }
                prop(ConstraintViolation<*>::getMessage).isEqualTo("size must be between 1 and 200")
            }
        }
    }
}