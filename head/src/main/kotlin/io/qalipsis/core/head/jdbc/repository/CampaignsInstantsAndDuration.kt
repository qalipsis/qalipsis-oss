package io.qalipsis.core.head.jdbc.repository

import io.micronaut.core.annotation.Introspected
import java.time.Duration
import java.time.Instant

/**
 * Non-mapped entity used to search for boundaries to query time-series data on campaigns.
 */
@Introspected
data class CampaignsInstantsAndDuration(
    val minStart: Instant?,
    val maxEnd: Instant?,
    val maxDurationSec: Long?,
) {
    val maxDuration = maxDurationSec?.let(Duration::ofSeconds)

    val hasValues = minStart != null && maxEnd != null && maxDurationSec != null
}