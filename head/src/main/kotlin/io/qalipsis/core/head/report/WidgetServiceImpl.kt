package io.qalipsis.core.head.report

import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.head.jdbc.entity.FactoryStateValue
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.FactoryStateRepository
import jakarta.inject.Singleton
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Implementation of [WidgetService] interface.
 *
 * @author Francisca Eze
 */
@Singleton
internal class WidgetServiceImpl(
    private val factoriesStateRepository: FactoryStateRepository,
    private val campaignRepository: CampaignRepository
) : WidgetService {
    override suspend fun getFactoryStates(tenant: String): FactoryState {
        val factoryStateCount = factoriesStateRepository.countCurrentFactoryStatesByTenant(tenant)
        val factoryStateMap: Map<FactoryStateValue, Int> = factoryStateCount.associate { Pair(it.state, it.count) }
        return FactoryState(
            factoryStateMap.getOrDefault(FactoryStateValue.IDLE, 0),
            factoryStateMap.getOrDefault(FactoryStateValue.REGISTERED, 0),
            factoryStateMap.getOrDefault(FactoryStateValue.UNHEALTHY, 0),
            factoryStateMap.getOrDefault(FactoryStateValue.OFFLINE, 0)
        )
    }

    override suspend fun aggregateCampaignResult(
        tenant: String,
        from: Instant,
        until: Instant?,
        timeOffset: Float,
        aggregationTimeframe: Duration?
    ): List<CampaignSummaryResult> {
        // convert time offsets to minutes
        val timezoneOffset = Duration.of((timeOffset * 60).toLong(), ChronoUnit.MINUTES)
        val start = from.minus(timezoneOffset)
        val end = until?.minus(timezoneOffset) ?: Instant.now()
        val result = mutableListOf<CampaignSummaryResult>()
        val campaignResultList =
            campaignRepository.retrieveCampaignsStatusHistogram(
                tenant,
                start,
                end,
                aggregationTimeframe ?: Duration.ofHours(24)
            )
        campaignResultList.groupBy { it.seriesStart }.forEach { it ->
            var failureCounter = 0
            var successCounter = 0
            it.value.map {
                if (it.status == ExecutionStatus.SUCCESSFUL) {
                    successCounter = it.count
                } else {
                    failureCounter += it.count
                }
            }
            result.add(CampaignSummaryResult(start = it.key, successful = successCounter, failed = failureCounter))
        }
        return result
    }
}
