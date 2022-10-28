package io.qalipsis.core.head.report

import io.qalipsis.api.report.ExecutionStatus
import io.qalipsis.core.head.jdbc.entity.FactoryStateValue
import io.qalipsis.core.head.jdbc.repository.CampaignRepository
import io.qalipsis.core.head.jdbc.repository.FactoryStateRepository
import jakarta.inject.Singleton
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

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
        val factoryStateCount = factoriesStateRepository.findLatestFactoryStates(tenant)
        val factoryStateMap: Map<FactoryStateValue, Int> = factoryStateCount.associate { Pair(it.state, it.count) }
        return FactoryState.convertToWrapperClass(factoryStateMap)
    }

    override suspend fun aggregateCampaignResult(
        tenant: String,
        from: Instant?,
        until: Instant?,
        timeOffset: Float,
        aggregationTimeframe: Duration?
    ): List<CampaignSummaryResult> {
        val minutes = if (timeOffset % 1 > 0) ((timeOffset % 1) * 100).roundToLong() else 0
        val calcOffset = Duration.of(timeOffset.toLong(), ChronoUnit.HOURS).plusMinutes(minutes)
        val start = from?.minus(calcOffset) ?: Instant.now().truncatedTo(ChronoUnit.DAYS).minus(calcOffset)
        val end = until?.minus(calcOffset) ?: start.minus(7, ChronoUnit.DAYS)
        val result = mutableListOf<CampaignSummaryResult>()
        val campaignResultList =
            campaignRepository.aggregate(tenant, start, end, aggregationTimeframe ?: Duration.ofHours(24))
        campaignResultList.groupBy { it.seriesStart }.map { it ->
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
