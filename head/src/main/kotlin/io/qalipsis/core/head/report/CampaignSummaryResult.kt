package io.qalipsis.core.head.report

import io.micronaut.core.annotation.Introspected
import java.time.Instant

/**
 * Campaign status summary class to be returned for frontend use.
 *
 * @author Francisca Eze
 */
@Introspected
internal data class CampaignSummaryResult(val start: Instant, val successful: Int = 0, val failed: Int = 0)