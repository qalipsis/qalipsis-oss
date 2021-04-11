package io.qalipsis.api.steps

/**
 * Specification to for the reporting of a step execution.
 *
 * @property reportErrors marks the campaign as failed if there are execution failures, defaults to false
 *
 * @author Eric Jessé
 */
data class StepReportingSpecification(
    var reportErrors: Boolean = false
)
