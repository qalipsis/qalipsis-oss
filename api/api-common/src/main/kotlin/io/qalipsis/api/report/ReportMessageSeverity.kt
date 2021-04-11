package io.qalipsis.api.report

/**
 * Severity of a [ReportMessage].
 *
 * @author Eric Jessé
 */
enum class ReportMessageSeverity {
    /**
     * Severity for messages that have no impact on the final result and are just for user information.
     */
    INFO,

    /**
     * Severity for issues that have no impact on the final result but could potentially have negative side effect.
     */
    WARN,

    /**
     * Severity for issues that will let the campaign continue until the end but will make the camnpaign fail.
     */
    ERROR,

    /**
     * Severity for issues that will immediately abort the campaign.
     */
    ABORT

}
