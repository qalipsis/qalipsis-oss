package io.qalipsis.core.head.campaign.scheduler

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.inject.Singleton
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.validation.constraints.NotBlank

/**
 * Hourly implementation of [Scheduling].
 *
 * @author Joël Valère
 */

@Singleton
internal class HourlyScheduling(
    @field:Schema(
        description = "The time zone ID to use for the scheduling",
        required = true,
        example = "Africa/Douala"
    )
    @field:NotBlank
    private val timeZone: String,
    @field:Schema(
        description = "The set of 0-23-based integers that restrict the hours in the day, when the campaign can be executed; 0 stands for 12AM, 23 for 11PM",
        required = true,
        example = "[0, 17, 11, 9]"
    )
    private val restrictions: Set<Int>
) : Scheduling {

    init {
        require(restrictions.all { it in DAILY_HOURS }) {
            "Hourly restrictions should be a set of 0-23 based"
        }
    }

    override fun nextSchedule(from: Instant): Instant {
        return if (restrictions.isEmpty()) {
            // Schedule to the next hour.
            from.plus(1, ChronoUnit.HOURS)
        } else {
            // We need to sort the set in ascending order.
            val sortedRestrictions = restrictions.sorted()
            // The current hour of the day.
            val currentHour = from.atZone(ZoneId.of(timeZone)).hour
            // The next hour of execution : if there are hours in the restriction set greater than the current hour, take the smallest ; otherwise take the first hour of the restriction set, that hour is in the next day.
            val nextHour = sortedRestrictions.firstOrNull { currentHour < it } ?: (sortedRestrictions.first() + 24)
            // The difference in hours, between the current hour and the next hour of execution.
            val difference = (nextHour - currentHour).toLong()

            from.plus(difference, ChronoUnit.HOURS)
        }
    }

    private companion object {

        val DAILY_HOURS = 0..23
    }
}