package io.qalipsis.core.head.campaign.scheduler

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.inject.Singleton
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.validation.constraints.NotBlank

/**
 * Daily implementation of [Scheduling].
 *
 * @author Joël Valère
 */

@Singleton
internal class DailyScheduling(
    @field:Schema(
        description = "The time zone ID to use to schedule the test",
        required = true,
        example = "Africa/Douala"
    )
    @field:NotBlank
    private val timeZone: String,
    @field:Schema(
        description = "The set of 1-7-based integers that restrict the days in the week, when the campaign can be executed; 1 stands for Monday, 7 for Sunday",
        required = true,
        example = "[1, 7, 3]"
    )
    private val restrictions: Set<Int>
) : Scheduling {

    init {
        require(restrictions.all { it in WEEK_DAYS }) {
            "Daily restrictions should be a set of 1-7 based"
        }
    }

    override fun nextSchedule(from: Instant): Instant {
        return if (restrictions.isEmpty()) {
            // Schedule to the next day.
            from.plus(1, ChronoUnit.DAYS)
        } else {
            // We need to sort the set in ascending order.
            val sortedRestrictions = restrictions.sorted()
            // The current day of the week.
            val currentDay = from.atZone(ZoneId.of(timeZone)).dayOfWeek.value
            // The next day of execution : if there are days in the restriction set greater than the current day, take the smallest ; otherwise take the first day of the restriction set, that day is in the next week.
            val nextDay = sortedRestrictions.firstOrNull { currentDay < it } ?: (sortedRestrictions.first() + 7)
            // The difference in days, between the current day and the next day of execution.
            val difference = (nextDay - currentDay).toLong()

            from.plus(difference, ChronoUnit.DAYS)
        }
    }

    private companion object {

        val WEEK_DAYS = 1..7
    }

}