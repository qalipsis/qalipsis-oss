package io.qalipsis.core.head.campaign.scheduler

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.inject.Singleton
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.validation.constraints.NotBlank

/**
 * Monthly implementation of [Scheduling].
 *
 * @author Joël Valère
 */

@Singleton
internal class MonthlyScheduling(
    @field:Schema(
        description = "The time zone ID to use to schedule the test",
        required = true,
        example = "Africa/Douala"
    )
    @field:NotBlank
    override val timeZone: String,
    @field:Schema(
        description = "The set of -15 to 31-based integers that restrict the hours in the day, when the campaign can be executed; for negative values, the restriction is calculated from the 1st of the next month; otherwise, 1 stands for the first day of the month and 31 for the thirty-first day of the month, if there is one",
        required = true,
        example = "[1, 17, 11, 9]"
    )
    override val restrictions: Set<Int>
) : Scheduling {

    init {
        require(restrictions.all { it in MONTH_DAYS_NEGATIVE_VALUES  || it in MONTH_DAYS_POSITIVE_VALUES}) {
            "Monthly restrictions should be a set of -15 to -1-based or 1 to 31-based"
        }
    }

    override fun nextSchedule(from: Instant): Instant {
        return if (restrictions.isEmpty()) {
            // Schedule to the next day.
            from.plus(1, ChronoUnit.DAYS)
        } else {
            val lastDayOfTheCurrentMonth = from.atZone(ZoneId.of(timeZone))
                .plusMonths(1).withDayOfMonth(1).minusDays(1).dayOfMonth

            // We need to sort the set in ascending order.
            val sortedRestrictions =
                (restrictions.filter { it > 0 }
                    .plus(restrictions.filter { it < 0 }.map { lastDayOfTheCurrentMonth + it + 1 })).toSortedSet() // After mapping negative values to their corresponding positive values.
            // The current day of the month.
            val currentDay = from.atZone(ZoneId.of(timeZone)).dayOfMonth
            // The next day of execution : if there are days in the restriction set greater than the current day and less than the last day of the current month, take the smallest ; otherwise take the first day of the restriction set, that day is in the next month.
            val nextDay = sortedRestrictions.firstOrNull { it in (currentDay + 1)..lastDayOfTheCurrentMonth }
                ?: (sortedRestrictions.first() + lastDayOfTheCurrentMonth)
            // The difference in days, between the current day and the next day of execution.
            val difference = (nextDay - currentDay).toLong()

            from.plus(difference, ChronoUnit.DAYS)
        }
    }

    private companion object {

        // For negative values, the restriction is calculated from the 1st of the next month.
        val MONTH_DAYS_NEGATIVE_VALUES = -15..-1
        val MONTH_DAYS_POSITIVE_VALUES = 1..31
    }
}