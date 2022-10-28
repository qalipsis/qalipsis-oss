package io.qalipsis.core.head.report

import java.time.Duration
import java.time.Instant

/**
 * Service to list values for dashboard UI widgets.
 *
 * @author Francisca Eze
 */
internal interface WidgetService {

    /**
     * Fetches the latest factory states per tenant.
     */
    suspend fun getFactoryStates(tenant: String): FactoryState

    /**
     * Aggregates the campaign count and results within the specified window.
     */
    suspend fun aggregateCampaignResult(
        tenant: String,
        from: Instant?,
        until: Instant?,
        timeOffset: Float,
        aggregationTimeframe: Duration?
    ): List<CampaignSummaryResult>?
}