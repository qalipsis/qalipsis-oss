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
internal class HourlySchedulingTest {

    @RegisterExtension
    @JvmField
    val testDispatcherProvider = TestDispatcherProvider()

    @Test
    internal fun `should not accept hourly scheduling when the restriction set contains an integer less than 0`() =
        testDispatcherProvider.runTest {// when
            val exception = assertThrows<IllegalArgumentException> {
                HourlyScheduling(
                    timeZone = "Africa/Douala",
                    restrictions = setOf(-2, 0, 9, 11)
                )
            }

            // then
            assertThat(exception.message).isEqualTo("Hourly restrictions should be a set of 0-23 based")
        }

    @Test
    internal fun `should not accept hourly scheduling when the restriction set contains an integer greater than 23`() =
        testDispatcherProvider.runTest {
            // when
            val exception = assertThrows<IllegalArgumentException> {
                HourlyScheduling(
                    timeZone = "Africa/Douala",
                    restrictions = setOf(0, 9, 11, 24)
                )
            }

            // then
            assertThat(exception.message).isEqualTo("Hourly restrictions should be a set of 0-23 based")
        }

    @Test
    internal fun `should schedule next instant when the restriction set contains the minimal value`() = testDispatcherProvider.runTest {
        // given
        val hourlyScheduling = HourlyScheduling(
            timeZone = "Africa/Douala",
            restrictions = setOf(0, 9, 11, 17)
        )

        val instant =
            Instant.parse("2023-04-06T12:57:12.601Z")
        assertThat(instant.atZone(ZoneId.of("Africa/Douala")))
            .isEqualTo(ZonedDateTime.parse("2023-04-06T13:57:12.601+01:00[Africa/Douala]"))

        // when
        val result = hourlyScheduling.nextSchedule(instant)

        // then
        assertThat(result).isEqualTo(Instant.parse("2023-04-06T16:57:12.601Z"))
        assertThat(result.atZone(ZoneId.of("Africa/Douala")))
            .isEqualTo(ZonedDateTime.parse("2023-04-06T17:57:12.601+01:00[Africa/Douala]"))
    }

    @Test
    internal fun `should schedule next instant when the restriction set contains the maximal value`() = testDispatcherProvider.runTest {
        // given
        val hourlyScheduling = HourlyScheduling(
            timeZone = "Africa/Douala",
            restrictions = setOf(4, 9, 11, 23)
        )

        val instant =
            Instant.parse("2023-04-06T12:57:12.601Z")
        assertThat(instant.atZone(ZoneId.of("Africa/Douala")))
            .isEqualTo(ZonedDateTime.parse("2023-04-06T13:57:12.601+01:00[Africa/Douala]"))

        // when
        val result = hourlyScheduling.nextSchedule(instant)

        // then
        assertThat(result).isEqualTo(Instant.parse("2023-04-06T22:57:12.601Z"))
        assertThat(result.atZone(ZoneId.of("Africa/Douala")))
            .isEqualTo(ZonedDateTime.parse("2023-04-06T23:57:12.601+01:00[Africa/Douala]"))
    }

    @Test
    internal fun `should schedule when the restriction set is empty`() = testDispatcherProvider.runTest {
        // given
        val hourlyScheduling = HourlyScheduling(
            timeZone = "Africa/Douala",
            restrictions = setOf()
        )

        val instant =
            Instant.parse("2023-04-06T12:57:12.601Z")
        assertThat(instant.atZone(ZoneId.of("Africa/Douala")))
            .isEqualTo(ZonedDateTime.parse("2023-04-06T13:57:12.601+01:00[Africa/Douala]"))

        // when
        val result = hourlyScheduling.nextSchedule(instant)

        // then
        assertThat(result).isEqualTo(Instant.parse("2023-04-06T13:57:12.601Z"))
        assertThat(result.atZone(ZoneId.of("Africa/Douala")))
            .isEqualTo(ZonedDateTime.parse("2023-04-06T14:57:12.601+01:00[Africa/Douala]"))
    }

    @Test
    internal fun `should schedule when the offSet GMT-12H`() = testDispatcherProvider.runTest {
        // given
        val hourlyScheduling = HourlyScheduling(
            timeZone = "Etc/GMT+12",
            restrictions = setOf(0, 17, 11, 9)
        )

        val instant =
            Instant.parse("2023-04-06T11:57:12.601Z")
        assertThat(instant.atZone(ZoneId.of("Etc/GMT+12")))
            .isEqualTo(ZonedDateTime.parse("2023-04-05T23:57:12.601-12:00[Etc/GMT+12]"))

        // when
        val result = hourlyScheduling.nextSchedule(instant)

        // then
        assertThat(result).isEqualTo(Instant.parse("2023-04-06T12:57:12.601Z"))
        assertThat(result.atZone(ZoneId.of("Etc/GMT+12")))
            .isEqualTo(ZonedDateTime.parse("2023-04-06T00:57:12.601-12:00[Etc/GMT+12]"))
    }

    @Test
    internal fun `should schedule when the offSet GMT+05H30`() = testDispatcherProvider.runTest {
        // given
        val hourlyScheduling = HourlyScheduling(
            timeZone = "Asia/Colombo",
            restrictions = setOf(0, 17, 11, 9)
        )

        val instant =
            Instant.parse("2023-04-06T19:57:12.601Z")
        assertThat(instant.atZone(ZoneId.of("Asia/Colombo")))
            .isEqualTo(ZonedDateTime.parse("2023-04-07T01:27:12.601+05:30[Asia/Colombo]"))

        // when
        val result = hourlyScheduling.nextSchedule(instant)

        // then
        assertThat(result).isEqualTo(Instant.parse("2023-04-07T03:57:12.601Z"))
        assertThat(result.atZone(ZoneId.of("Asia/Colombo")))
            .isEqualTo(ZonedDateTime.parse("2023-04-07T09:27:12.601+05:30[Asia/Colombo]"))
    }

    @Test
    internal fun `should schedule when it matches the next day`() = testDispatcherProvider.runTest {
        // given
        val hourlyScheduling = HourlyScheduling(
            timeZone = "Africa/Douala",
            restrictions = setOf(0, 11, 9)
        )

        val instant =
            Instant.parse("2023-04-06T12:57:12.601Z")
        assertThat(instant.atZone(ZoneId.of("Africa/Douala")))
            .isEqualTo(ZonedDateTime.parse("2023-04-06T13:57:12.601+01:00[Africa/Douala]"))

        // when
        val result = hourlyScheduling.nextSchedule(instant)

        // then
        assertThat(result).isEqualTo(Instant.parse("2023-04-06T23:57:12.601Z"))
        assertThat(result.atZone(ZoneId.of("Africa/Douala")))
            .isEqualTo(ZonedDateTime.parse("2023-04-07T00:57:12.601+01:00[Africa/Douala]"))
    }
}