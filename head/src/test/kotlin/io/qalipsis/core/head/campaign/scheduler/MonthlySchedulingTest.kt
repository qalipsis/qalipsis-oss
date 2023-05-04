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

package io.qalipsis.core.head.campaign.scheduler

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.qalipsis.core.configuration.ExecutionEnvironments
import io.qalipsis.test.coroutines.TestDispatcherProvider
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * @author Joël Valère
 */


@MicronautTest(startApplication = false, environments = [ExecutionEnvironments.HEAD])
internal class MonthlySchedulingTest {

    @RegisterExtension
    @JvmField
    val testDispatcherProvider = TestDispatcherProvider()

    @Test
    internal fun `should not accept monthly scheduling when the restriction set contains an integer less than -15`() =
        testDispatcherProvider.runTest {
            // when
            val exception = assertThrows<IllegalArgumentException> {
                MonthlyScheduling(
                    timeZone = "Africa/Douala",
                    restrictions = setOf(-16, 1, 3)
                )
            }

            // then
            assertThat(exception.message).isEqualTo("Monthly restrictions should be a set of -15 to -1-based or 1 to 31-based")
        }

    @Test
    internal fun `should not accept monthly scheduling when the restriction set contains an integer greater than 31`() =
        testDispatcherProvider.runTest {
            // when
            val exception = assertThrows<IllegalArgumentException> {
                MonthlyScheduling(
                    timeZone = "Africa/Douala",
                    restrictions = setOf(1, 32, 3)
                )
            }

            // then
            assertThat(exception.message).isEqualTo("Monthly restrictions should be a set of -15 to -1-based or 1 to 31-based")
        }

    @Test
    internal fun `should not accept monthly scheduling when the restriction set contains 0`() =
        testDispatcherProvider.runTest {
            // when
            val exception = assertThrows<IllegalArgumentException> {
                MonthlyScheduling(
                    timeZone = "Africa/Douala",
                    restrictions = setOf(0, 1, 3)
                )
            }

            // then
            assertThat(exception.message).isEqualTo("Monthly restrictions should be a set of -15 to -1-based or 1 to 31-based")
        }

    @Test
    internal fun `should schedule next instant when the restriction set contains the minimal value`() = testDispatcherProvider.runTest {
        // given
        val monthlyScheduling = MonthlyScheduling(
            timeZone = "Africa/Douala",
            restrictions = setOf(-15, 5, 3, 21)
        )
        val instant = Instant.parse("2023-04-06T12:57:12.601Z")
        assertThat(instant.atZone(ZoneId.of("Africa/Douala")))
            .isEqualTo(ZonedDateTime.parse("2023-04-06T13:57:12.601+01:00[Africa/Douala]"))

        // when
        val result = monthlyScheduling.nextSchedule(instant)

        // then
        assertThat(result).isEqualTo(Instant.parse("2023-04-16T12:57:12.601Z"))
        assertThat(result.atZone(ZoneId.of("Africa/Douala")))
            .isEqualTo(ZonedDateTime.parse("2023-04-16T13:57:12.601+01:00[Africa/Douala]"))
    }

    @Test
    internal fun `should schedule next instant when the restriction set contains the maximal value`() = testDispatcherProvider.runTest {
        // given
        val monthlyScheduling = MonthlyScheduling(
            timeZone = "Africa/Douala",
            restrictions = setOf(2, 5, 3, 31)
        )
        val instant = Instant.parse("2023-04-06T12:57:12.601Z")
        assertThat(instant.atZone(ZoneId.of("Africa/Douala")))
            .isEqualTo(ZonedDateTime.parse("2023-04-06T13:57:12.601+01:00[Africa/Douala]"))

        // when
        val result = monthlyScheduling.nextSchedule(instant)

        // then
        assertThat(result).isEqualTo(Instant.parse("2023-05-02T12:57:12.601Z"))
        assertThat(result.atZone(ZoneId.of("Africa/Douala")))
            .isEqualTo(ZonedDateTime.parse("2023-05-02T13:57:12.601+01:00[Africa/Douala]"))
    }

    @Test
    internal fun `should schedule next instant when the restriction set contains -1`() = testDispatcherProvider.runTest {
        // given
        val monthlyScheduling = MonthlyScheduling(
            timeZone = "Africa/Douala",
            restrictions = setOf(-1, 5, 3, 21)
        )
        val instant = Instant.parse("2023-04-06T12:57:12.601Z")
        assertThat(instant.atZone(ZoneId.of("Africa/Douala")))
            .isEqualTo(ZonedDateTime.parse("2023-04-06T13:57:12.601+01:00[Africa/Douala]"))

        // when
        val result = monthlyScheduling.nextSchedule(instant)

        // then
        assertThat(result).isEqualTo(Instant.parse("2023-04-21T12:57:12.601Z"))
        assertThat(result.atZone(ZoneId.of("Africa/Douala")))
            .isEqualTo(ZonedDateTime.parse("2023-04-21T13:57:12.601+01:00[Africa/Douala]"))
    }

    @Test
    internal fun `should schedule next instant when the restriction set contains 1`() = testDispatcherProvider.runTest {
        // given
        val monthlyScheduling = MonthlyScheduling(
            timeZone = "Africa/Douala",
            restrictions = setOf(1, 5, 3, 30)
        )
        val instant = Instant.parse("2023-04-06T12:57:12.601Z")
        assertThat(instant.atZone(ZoneId.of("Africa/Douala")))
            .isEqualTo(ZonedDateTime.parse("2023-04-06T13:57:12.601+01:00[Africa/Douala]"))

        // when
        val result = monthlyScheduling.nextSchedule(instant)

        // then
        assertThat(result).isEqualTo(Instant.parse("2023-04-30T12:57:12.601Z"))
        assertThat(result.atZone(ZoneId.of("Africa/Douala")))
            .isEqualTo(ZonedDateTime.parse("2023-04-30T13:57:12.601+01:00[Africa/Douala]"))
    }

    @Test
    internal fun `should schedule when the restriction set is empty`() = testDispatcherProvider.runTest {
        // given
        val monthlyScheduling = MonthlyScheduling(
            timeZone = "Africa/Douala",
            restrictions = setOf()
        )
        val instant = Instant.parse("2023-04-06T12:57:12.601Z")
        assertThat(instant.atZone(ZoneId.of("Africa/Douala")))
            .isEqualTo(ZonedDateTime.parse("2023-04-06T13:57:12.601+01:00[Africa/Douala]"))

        // when
        val result = monthlyScheduling.nextSchedule(instant)

        // then
        assertThat(result).isEqualTo(Instant.parse("2023-04-07T12:57:12.601Z"))
        assertThat(result.atZone(ZoneId.of("Africa/Douala")))
            .isEqualTo(ZonedDateTime.parse("2023-04-07T13:57:12.601+01:00[Africa/Douala]"))
    }

    @Test
    internal fun `should schedule when the offSet GMT-12H`() = testDispatcherProvider.runTest {
        // given
        val monthlyScheduling = MonthlyScheduling(
            timeZone = "Etc/GMT+12",
            restrictions = setOf(2, 7, 3, 1)
        )
        val instant = Instant.parse("2023-04-06T12:57:12.601Z")
        assertThat(instant.atZone(ZoneId.of("Etc/GMT+12")))
            .isEqualTo(ZonedDateTime.parse("2023-04-06T00:57:12.601-12:00[Etc/GMT+12]"))

        // when
        val result = monthlyScheduling.nextSchedule(instant)

        // then
        assertThat(result).isEqualTo(Instant.parse("2023-04-07T12:57:12.601Z"))
        assertThat(result.atZone(ZoneId.of("Etc/GMT+12")))
            .isEqualTo(ZonedDateTime.parse("2023-04-07T00:57:12.601-12:00[Etc/GMT+12]"))
    }

    @Test
    internal fun `should schedule when it matches the next month`() = testDispatcherProvider.runTest {
        // given
        val monthlyScheduling = MonthlyScheduling(
            timeZone = "Africa/Douala",
            restrictions = setOf(5, 2, 3)
        )
        val instant = Instant.parse("2023-04-06T12:57:12.601Z")
        assertThat(instant.atZone(ZoneId.of("Africa/Douala")))
            .isEqualTo(ZonedDateTime.parse("2023-04-06T13:57:12.601+01:00[Africa/Douala]"))

        // when
        val result = monthlyScheduling.nextSchedule(instant)

        // then
        assertThat(result).isEqualTo(Instant.parse("2023-05-02T12:57:12.601Z"))
        assertThat(result.atZone(ZoneId.of("Africa/Douala")))
            .isEqualTo(ZonedDateTime.parse("2023-05-02T13:57:12.601+01:00[Africa/Douala]"))
    }

    @Test
    internal fun `should schedule when the restriction set contains 31 and the month has 28 days`() =
        testDispatcherProvider.runTest {
            // given
            val monthlyScheduling = MonthlyScheduling(
                timeZone = "Africa/Douala",
                restrictions = setOf(5, 2, 31, 3, 28)
            )
            val instant = Instant.parse("2023-02-06T12:57:12.601Z")
            assertThat(instant.atZone(ZoneId.of("Africa/Douala")))
                .isEqualTo(ZonedDateTime.parse("2023-02-06T13:57:12.601+01:00[Africa/Douala]"))

            // when
            var result = monthlyScheduling.nextSchedule(instant)

            // then
            assertThat(result).isEqualTo(Instant.parse("2023-02-28T12:57:12.601Z"))
            assertThat(result.atZone(ZoneId.of("Africa/Douala")))
                .isEqualTo(ZonedDateTime.parse("2023-02-28T13:57:12.601+01:00[Africa/Douala]"))

            // when
            result = monthlyScheduling.nextSchedule(result)

            // then
            assertThat(result).isEqualTo(Instant.parse("2023-03-02T12:57:12.601Z"))
            assertThat(result.atZone(ZoneId.of("Africa/Douala")))
                .isEqualTo(ZonedDateTime.parse("2023-03-02T13:57:12.601+01:00[Africa/Douala]"))
        }

    @Test
    internal fun `should schedule when the restriction the set contains 31 and the month has 29 days`() =
        testDispatcherProvider.runTest {
            // given
            val monthlyScheduling = MonthlyScheduling(
                timeZone = "Africa/Douala",
                restrictions = setOf(5, 2, 31, 3, 29)
            )
            val instant = Instant.parse("2020-02-06T12:57:12.601Z")
            assertThat(instant.atZone(ZoneId.of("Africa/Douala")))
                .isEqualTo(ZonedDateTime.parse("2020-02-06T13:57:12.601+01:00[Africa/Douala]"))

            // when
            var result = monthlyScheduling.nextSchedule(instant)

            // then
            assertThat(result).isEqualTo(Instant.parse("2020-02-29T12:57:12.601Z"))
            assertThat(result.atZone(ZoneId.of("Africa/Douala")))
                .isEqualTo(ZonedDateTime.parse("2020-02-29T13:57:12.601+01:00[Africa/Douala]"))

            // when
            result = monthlyScheduling.nextSchedule(result)

            // then
            assertThat(result).isEqualTo(Instant.parse("2020-03-02T12:57:12.601Z"))
            assertThat(result.atZone(ZoneId.of("Africa/Douala")))
                .isEqualTo(ZonedDateTime.parse("2020-03-02T13:57:12.601+01:00[Africa/Douala]"))
        }

    @Test
    internal fun `should schedule when the restriction set contains 31 and the month has 30 days`() =
        testDispatcherProvider.runTest {
            // given
            val monthlyScheduling = MonthlyScheduling(
                timeZone = "Africa/Douala",
                restrictions = setOf(5, 2, 31, 30)
            )
            val instant = Instant.parse("2023-04-06T12:57:12.601Z")
            assertThat(instant.atZone(ZoneId.of("Africa/Douala")))
                .isEqualTo(ZonedDateTime.parse("2023-04-06T13:57:12.601+01:00[Africa/Douala]"))

            // when
            var result = monthlyScheduling.nextSchedule(instant)

            // then
            assertThat(result).isEqualTo(Instant.parse("2023-04-30T12:57:12.601Z"))
            assertThat(result.atZone(ZoneId.of("Africa/Douala")))
                .isEqualTo(ZonedDateTime.parse("2023-04-30T13:57:12.601+01:00[Africa/Douala]"))

            // when
            result = monthlyScheduling.nextSchedule(result)

            // then
            assertThat(result).isEqualTo(Instant.parse("2023-05-02T12:57:12.601Z"))
            assertThat(result.atZone(ZoneId.of("Africa/Douala")))
                .isEqualTo(ZonedDateTime.parse("2023-05-02T13:57:12.601+01:00[Africa/Douala]"))
        }

    @Test
    internal fun `should schedule when the restriction set contains 31 and the month has 31 days`() =
        testDispatcherProvider.runTest {
            // given
            val monthlyScheduling = MonthlyScheduling(
                timeZone = "Africa/Douala",
                restrictions = setOf(5, 2, 31, 3)
            )
            val instant = Instant.parse("2023-03-06T12:57:12.601Z")
            assertThat(instant.atZone(ZoneId.of("Africa/Douala")))
                .isEqualTo(ZonedDateTime.parse("2023-03-06T13:57:12.601+01:00[Africa/Douala]"))

            // when
            val result = monthlyScheduling.nextSchedule(instant)

            // then
            assertThat(result).isEqualTo(Instant.parse("2023-03-31T12:57:12.601Z"))
            assertThat(result.atZone(ZoneId.of("Africa/Douala")))
                .isEqualTo(ZonedDateTime.parse("2023-03-31T13:57:12.601+01:00[Africa/Douala]"))
        }

    @Test
    internal fun `should schedule when the restriction set contains negative values and the month has 28 days`() =
        testDispatcherProvider.runTest {
            // given
            val monthlyScheduling = MonthlyScheduling(
                timeZone = "Africa/Douala",
                restrictions = setOf(-1, -5, -11, -3)
            )
            val instant = Instant.parse("2023-02-06T12:57:12.601Z")
            assertThat(instant.atZone(ZoneId.of("Africa/Douala")))
                .isEqualTo(ZonedDateTime.parse("2023-02-06T13:57:12.601+01:00[Africa/Douala]"))

            // when
            var result = monthlyScheduling.nextSchedule(instant)

            // then
            assertThat(result).isEqualTo(Instant.parse("2023-02-18T12:57:12.601Z"))
            assertThat(result.atZone(ZoneId.of("Africa/Douala")))
                .isEqualTo(ZonedDateTime.parse("2023-02-18T13:57:12.601+01:00[Africa/Douala]"))

            // when
            result = monthlyScheduling.nextSchedule(result)

            // then
            assertThat(result).isEqualTo(Instant.parse("2023-02-24T12:57:12.601Z"))
            assertThat(result.atZone(ZoneId.of("Africa/Douala")))
                .isEqualTo(ZonedDateTime.parse("2023-02-24T13:57:12.601+01:00[Africa/Douala]"))
        }

    @Test
    internal fun `should schedule when the restriction set contains negative values and the month has 29 days`() =
        testDispatcherProvider.runTest {
            // given
            val monthlyScheduling = MonthlyScheduling(
                timeZone = "Africa/Douala",
                restrictions = setOf(-1, -5, -11, -3)
            )
            val instant = Instant.parse("2020-02-06T12:57:12.601Z")
            assertThat(instant.atZone(ZoneId.of("Africa/Douala")))
                .isEqualTo(ZonedDateTime.parse("2020-02-06T13:57:12.601+01:00[Africa/Douala]"))

            // when
            var result = monthlyScheduling.nextSchedule(instant)

            // then
            assertThat(result).isEqualTo(Instant.parse("2020-02-19T12:57:12.601Z"))
            assertThat(result.atZone(ZoneId.of("Africa/Douala")))
                .isEqualTo(ZonedDateTime.parse("2020-02-19T13:57:12.601+01:00[Africa/Douala]"))

            // when
            result = monthlyScheduling.nextSchedule(result)

            // then
            assertThat(result).isEqualTo(Instant.parse("2020-02-25T12:57:12.601Z"))
            assertThat(result.atZone(ZoneId.of("Africa/Douala")))
                .isEqualTo(ZonedDateTime.parse("2020-02-25T13:57:12.601+01:00[Africa/Douala]"))
        }

    @Test
    internal fun `should schedule when the restriction set contains negative values and the month has 30 days`() =
        testDispatcherProvider.runTest {
            // given
            val monthlyScheduling = MonthlyScheduling(
                timeZone = "Africa/Douala",
                restrictions = setOf(-1, -5, -11, -3)
            )
            val instant = Instant.parse("2020-04-06T12:57:12.601Z")
            assertThat(instant.atZone(ZoneId.of("Africa/Douala")))
                .isEqualTo(ZonedDateTime.parse("2020-04-06T13:57:12.601+01:00[Africa/Douala]"))

            // when
            var result = monthlyScheduling.nextSchedule(instant)

            // then
            assertThat(result).isEqualTo(Instant.parse("2020-04-20T12:57:12.601Z"))
            assertThat(result.atZone(ZoneId.of("Africa/Douala")))
                .isEqualTo(ZonedDateTime.parse("2020-04-20T13:57:12.601+01:00[Africa/Douala]"))

            // when
            result = monthlyScheduling.nextSchedule(result)

            // then
            assertThat(result).isEqualTo(Instant.parse("2020-04-26T12:57:12.601Z"))
            assertThat(result.atZone(ZoneId.of("Africa/Douala")))
                .isEqualTo(ZonedDateTime.parse("2020-04-26T13:57:12.601+01:00[Africa/Douala]"))
        }

    @Test
    internal fun `should schedule when the restriction set contains negative values and the month has 31 days`() =
        testDispatcherProvider.runTest {
            // given
            val monthlyScheduling = MonthlyScheduling(
                timeZone = "Africa/Douala",
                restrictions = setOf(-1, -5, -11, -3)
            )
            val instant = Instant.parse("2020-05-06T12:57:12.601Z")
            assertThat(instant.atZone(ZoneId.of("Africa/Douala")))
                .isEqualTo(ZonedDateTime.parse("2020-05-06T13:57:12.601+01:00[Africa/Douala]"))

            // when
            var result = monthlyScheduling.nextSchedule(instant)

            // then
            assertThat(result).isEqualTo(Instant.parse("2020-05-21T12:57:12.601Z"))
            assertThat(result.atZone(ZoneId.of("Africa/Douala")))
                .isEqualTo(ZonedDateTime.parse("2020-05-21T13:57:12.601+01:00[Africa/Douala]"))

            // when
            result = monthlyScheduling.nextSchedule(result)

            // then
            assertThat(result).isEqualTo(Instant.parse("2020-05-27T12:57:12.601Z"))
            assertThat(result.atZone(ZoneId.of("Africa/Douala")))
                .isEqualTo(ZonedDateTime.parse("2020-05-27T13:57:12.601+01:00[Africa/Douala]"))
        }

    @Test
    internal fun `should schedule when the restriction set contains negative and positive values`() =
        testDispatcherProvider.runTest {
            // given
            val monthlyScheduling = MonthlyScheduling(
                timeZone = "Africa/Douala",
                restrictions = setOf(-11, -5, -3, -1, 10, 20)
            )

            val instant = Instant.parse("2023-02-06T12:57:12.601Z")
            assertThat(instant.atZone(ZoneId.of("Africa/Douala")))
                .isEqualTo(ZonedDateTime.parse("2023-02-06T13:57:12.601+01:00[Africa/Douala]"))

            // when
            var result = monthlyScheduling.nextSchedule(instant)

            // then
            assertThat(result).isEqualTo(Instant.parse("2023-02-10T12:57:12.601Z"))
            assertThat(result.atZone(ZoneId.of("Africa/Douala")))
                .isEqualTo(ZonedDateTime.parse("2023-02-10T13:57:12.601+01:00[Africa/Douala]"))

            // when
            result = monthlyScheduling.nextSchedule(result)

            // then
            assertThat(result).isEqualTo(Instant.parse("2023-02-18T12:57:12.601Z"))
            assertThat(result.atZone(ZoneId.of("Africa/Douala")))
                .isEqualTo(ZonedDateTime.parse("2023-02-18T13:57:12.601+01:00[Africa/Douala]"))

            // when
            result = monthlyScheduling.nextSchedule(result)

            // then
            assertThat(result).isEqualTo(Instant.parse("2023-02-20T12:57:12.601Z"))
            assertThat(result.atZone(ZoneId.of("Africa/Douala")))
                .isEqualTo(ZonedDateTime.parse("2023-02-20T13:57:12.601+01:00[Africa/Douala]"))

            // when
            result = monthlyScheduling.nextSchedule(result)

            // then
            assertThat(result).isEqualTo(Instant.parse("2023-02-24T12:57:12.601Z"))
            assertThat(result.atZone(ZoneId.of("Africa/Douala")))
                .isEqualTo(ZonedDateTime.parse("2023-02-24T13:57:12.601+01:00[Africa/Douala]"))
        }
}