package io.qalipsis.core.head.campaign.scheduler

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

/**
 * Service in charge of computing instants for periodic execution of actions.
 *
 * @author Joël Valère
 */
@Schema(
    name = "Scheduling",
    title = "Details of scheduling",
    allOf = [
        HourlyScheduling::class,
        DailyScheduling::class,
        MonthlyScheduling::class
    ]
)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "scheduling")
@JsonSubTypes(
    JsonSubTypes.Type(value = HourlyScheduling::class, name = "HOURLY"),
    JsonSubTypes.Type(value = DailyScheduling::class, name = "DAILY"),
    JsonSubTypes.Type(value = MonthlyScheduling::class, name = "MONTHLY")
)
internal interface Scheduling {

    val timeZone: String

    val restrictions: Set<Int>

    /**
     * Computes the next instant to execute a scheduled action.
     *
     * @param from the instant from with the calculation is done
     */
    fun nextSchedule(from: Instant = Instant.now()): Instant
}