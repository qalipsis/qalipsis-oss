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

package io.qalipsis.core.head.report

import io.aerisconsulting.catadioptre.KTestable
import io.micronaut.context.annotation.Requires
import io.qalipsis.api.logging.LoggerHelper.logger
import io.qalipsis.api.query.AggregationQueryExecutionContext
import io.qalipsis.api.query.DataRetrievalQueryExecutionContext
import io.qalipsis.api.query.Page
import io.qalipsis.api.report.TimeSeriesAggregationResult
import io.qalipsis.api.report.TimeSeriesDataProvider
import io.qalipsis.api.report.TimeSeriesRecord
import io.qalipsis.core.annotations.LogInput
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.CampaignsInstantsAndDuration
import io.qalipsis.core.head.jdbc.repository.DataSeriesRepository
import jakarta.inject.Singleton
import java.time.Duration

/**
 * Implementation of [TimeSeriesDataQueryService] used when no bean of [TimeSeriesDataProvider] can be found.
 *
 * @author Eric Jessé
 */
@Singleton
@Requires(beans = [TimeSeriesDataProvider::class])
internal class TimeSeriesDataQueryServiceImpl(
    private val timeSeriesDataProvider: TimeSeriesDataProvider,
    private val dataSeriesRepository: DataSeriesRepository,
    private val campaignRepository: CampaignRepository,
) : TimeSeriesDataQueryService {

    @LogInput
    override suspend fun render(
        tenant: String,
        dataSeriesReferences: Set<String>,
        queryExecutionRequest: AggregationQueryExecutionRequest
    ): Map<String, List<TimeSeriesAggregationResult>> {
        val dataSeries =
            dataSeriesRepository.findAllByTenantAndReferences(tenant, dataSeriesReferences)
                .filterNot { it.query.isNullOrBlank() }
        return if (dataSeries.isNotEmpty()) {
            campaignRepository.findInstantsAndDuration(tenant, queryExecutionRequest.campaignsReferences)
                ?.takeIf { it.hasValues }
                ?.let { campaignsInstantsAndDuration ->
                    val actualContext = sanitizeAggregationRequest(queryExecutionRequest, campaignsInstantsAndDuration)
                    log.debug { "Actual normalized query context: $actualContext" }

                    timeSeriesDataProvider.executeAggregations(
                        dataSeries.associate { it.reference to it.query!! },
                        actualContext
                    )
                }.orEmpty()
        } else {
            log.info { "No data series with prepared queries could be found in tenant $tenant with the specified references $dataSeriesReferences" }
            emptyMap()
        }
    }

    /**
     * Sanitizes the aggregation context, to ensure that the properties are not overreaching the allowed values.
     *
     * @param queryExecutionRequest original execution context to sanitize
     * @param campaignsInstantsAndDuration bound instants and durations of the campaigns to aggregate the data on
     */
    @KTestable
    private fun sanitizeAggregationRequest(
        queryExecutionRequest: AggregationQueryExecutionRequest,
        campaignsInstantsAndDuration: CampaignsInstantsAndDuration
    ): AggregationQueryExecutionContext {
        val minAggregationTimeframe =
            calculateMinimumAggregationTimeframe(campaignsInstantsAndDuration.maxDuration!!)
        return AggregationQueryExecutionContext(
            campaignsReferences = queryExecutionRequest.campaignsReferences,
            scenariosNames = queryExecutionRequest.scenariosNames,
            from = queryExecutionRequest.from?.coerceAtLeast(campaignsInstantsAndDuration.minStart!!)
                ?: campaignsInstantsAndDuration.minStart!!,
            until = queryExecutionRequest.until?.coerceAtMost(campaignsInstantsAndDuration.maxEnd!!)
                ?: campaignsInstantsAndDuration.maxEnd!!,
            aggregationTimeframe = queryExecutionRequest.aggregationTimeframe
                ?.coerceAtLeast(minAggregationTimeframe) ?: minAggregationTimeframe,
        )
    }

    @LogInput
    override suspend fun search(
        tenant: String,
        dataSeriesReferences: Set<String>,
        queryExecutionRequest: DataRetrievalQueryExecutionRequest
    ): Map<String, Page<TimeSeriesRecord>> {
        val dataSeries =
            dataSeriesRepository.findAllByTenantAndReferences(tenant, dataSeriesReferences).filter { it.query != null }
        return if (dataSeries.isNotEmpty()) {
            campaignRepository.findInstantsAndDuration(tenant, queryExecutionRequest.campaignsReferences)
                ?.takeIf { it.hasValues }
                ?.let { campaignsInstantsAndDuration ->
                    val actualContext = sanitizeRetrievalRequest(queryExecutionRequest, campaignsInstantsAndDuration)
                    log.debug { "Actual normalized query context: $actualContext" }

                    timeSeriesDataProvider.retrieveRecords(
                        dataSeries.associate { it.reference to it.query!! },
                        actualContext
                    )
                }.orEmpty()
        } else {
            log.info { "No data series with prepared queries could be found in tenant $tenant with the specified references $dataSeriesReferences" }
            emptyMap()
        }
    }

    /**
     * Sanitizes the retrieval context, to ensure that the properties are not overreaching the allowed values.
     *
     * @param queryExecutionContext original execution context to sanitize
     * @param campaignsInstantsAndDuration bound instants and durations of the campaigns to retrieve the data on
     */
    @KTestable
    private fun sanitizeRetrievalRequest(
        queryExecutionRequest: DataRetrievalQueryExecutionRequest,
        campaignsInstantsAndDuration: CampaignsInstantsAndDuration
    ): DataRetrievalQueryExecutionContext {
        val minAggregationTimeframe =
            calculateMinimumAggregationTimeframe(campaignsInstantsAndDuration.maxDuration!!)
        return DataRetrievalQueryExecutionContext(
            campaignsReferences = queryExecutionRequest.campaignsReferences,
            scenariosNames = queryExecutionRequest.scenariosNames,
            from = queryExecutionRequest.from.coerceAtLeast(campaignsInstantsAndDuration.minStart!!),
            until = queryExecutionRequest.until.coerceAtMost(campaignsInstantsAndDuration.maxEnd!!),
            aggregationTimeframe = queryExecutionRequest.aggregationTimeframe
                ?.coerceAtLeast(minAggregationTimeframe) ?: minAggregationTimeframe,
            page = queryExecutionRequest.page,
            size = queryExecutionRequest.size
        )
    }

    /**
     * Calculates the minimum allowed size of time-buckets for the aggregations.
     *
     * @param campaignMaxDuration the maximum known duration of campaigns to request
     *
     */
    @KTestable
    private fun calculateMinimumAggregationTimeframe(campaignMaxDuration: Duration): Duration {
        val referenceTimeframe = campaignMaxDuration.dividedBy(AGGREGATION_MAX_BUCKETS)
        var minTimeframe: Duration = Duration.ZERO
        val aggregationStagesIterator = AGGREGATION_STAGES.iterator()

        while (aggregationStagesIterator.hasNext() && minTimeframe < referenceTimeframe) {
            minTimeframe = aggregationStagesIterator.next()
        }
        return minTimeframe
    }

    private companion object {

        val log = logger()

        /**
         * Maximal number of buckets/points to use as aggregations ranges.
         */
        const val AGGREGATION_MAX_BUCKETS = 400L

        /**
         * Stages to force the aggregation when the user-specified timeframe is too small.
         */
        val AGGREGATION_STAGES =
            listOf(
                Duration.ofSeconds(1),
                Duration.ofSeconds(5),
                Duration.ofSeconds(10),
                Duration.ofSeconds(30),
                Duration.ofMinutes(1),
                Duration.ofMinutes(5),
                Duration.ofMinutes(10),
                Duration.ofMinutes(30),
                Duration.ofHours(1),
                Duration.ofHours(2),
                Duration.ofHours(4),
                Duration.ofHours(6),
                Duration.ofHours(12),
                Duration.ofDays(1),
                Duration.ofDays(2),
                Duration.ofDays(7),
            )

    }
}