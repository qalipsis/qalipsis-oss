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
internal class DailySchedulingTest {

    @RegisterExtension
    @JvmField
    val testDispatcherProvider = TestDispatcherProvider()

    @Test
    internal fun `should not accept daily scheduling when the restriction set contains an integer less than 1`() =
        testDispatcherProvider.runTest {
            // when
            val exception = assertThrows<IllegalArgumentException> {
                DailyScheduling(
                    timeZone = "Africa/Douala",
                    restrictions = setOf(0, 1, 7)
                )
            }

            // then
            assertThat(exception.message).isEqualTo("Daily restrictions should be a set of 1-7 based")
        }

    @Test
    internal fun `should not accept daily scheduling when the restriction set contains an integer greater than 7`() =
        testDispatcherProvider.runTest {
            // when
            val exception = assertThrows<IllegalArgumentException> {
                DailyScheduling(
                    timeZone = "Africa/Douala",
                    restrictions = setOf(1, 7, 8)
                )
            }

            // then
            assertThat(exception.message).isEqualTo("Daily restrictions should be a set of 1-7 based")
        }

    @Test
    internal fun `should schedule next instant when the restriction set contains the minimal value`() = testDispatcherProvider.runTest {
        // given
        val dailyScheduling = DailyScheduling(
            timeZone = "Africa/Douala",
            restrictions = setOf(1, 3, 5)
        )
        val instant =
            Instant.parse("2023-04-06T12:57:12.601Z")
        assertThat(instant.atZone(ZoneId.of("Africa/Douala")))
            .isEqualTo(ZonedDateTime.parse("2023-04-06T13:57:12.601+01:00[Africa/Douala]"))

        // when
        val result = dailyScheduling.nextSchedule(instant)

        // then
        assertThat(result).isEqualTo(Instant.parse("2023-04-07T12:57:12.601Z"))
        assertThat(result.atZone(ZoneId.of("Africa/Douala")))
            .isEqualTo(ZonedDateTime.parse("2023-04-07T13:57:12.601+01:00[Africa/Douala]"))
    }

    @Test
    internal fun `should schedule next instant when the restriction set contains the maximal value`() = testDispatcherProvider.runTest {
        // given
        val dailyScheduling = DailyScheduling(
            timeZone = "Africa/Douala",
            restrictions = setOf(2, 5, 7)
        )
        val instant =
            Instant.parse("2023-04-06T12:57:12.601Z")
        assertThat(instant.atZone(ZoneId.of("Africa/Douala")))
            .isEqualTo(ZonedDateTime.parse("2023-04-06T13:57:12.601+01:00[Africa/Douala]"))

        // when
        val result = dailyScheduling.nextSchedule(instant)

        // then
        assertThat(result).isEqualTo(Instant.parse("2023-04-07T12:57:12.601Z"))
        assertThat(result.atZone(ZoneId.of("Africa/Douala")))
            .isEqualTo(ZonedDateTime.parse("2023-04-07T13:57:12.601+01:00[Africa/Douala]"))
    }

    @Test
    internal fun `should schedule when the restriction set is empty`() = testDispatcherProvider.runTest {
        // given
        val dailyScheduling = DailyScheduling(
            timeZone = "Africa/Douala",
            restrictions = setOf()
        )
        val instant =
            Instant.parse("2023-04-06T12:57:12.601Z")
        assertThat(instant.atZone(ZoneId.of("Africa/Douala")))
            .isEqualTo(ZonedDateTime.parse("2023-04-06T13:57:12.601+01:00[Africa/Douala]"))

        // when
        val result = dailyScheduling.nextSchedule(instant)

        // then
        assertThat(result).isEqualTo(Instant.parse("2023-04-07T12:57:12.601Z"))
        assertThat(result.atZone(ZoneId.of("Africa/Douala")))
            .isEqualTo(ZonedDateTime.parse("2023-04-07T13:57:12.601+01:00[Africa/Douala]"))
    }

    @Test
    internal fun `should schedule when the offSet GMT-12H`() = testDispatcherProvider.runTest {
        // given
        val dailyScheduling = DailyScheduling(
            timeZone = "Etc/GMT+12",
            restrictions = setOf(1, 2, 3, 7)
        )
        val instant =
            Instant.parse("2023-04-06T12:57:12.601Z")
        assertThat(instant.atZone(ZoneId.of("Etc/GMT+12")))
            .isEqualTo(ZonedDateTime.parse("2023-04-06T00:57:12.601-12:00[Etc/GMT+12]"))

        // when
        val result = dailyScheduling.nextSchedule(instant)

        // then
        assertThat(result).isEqualTo(Instant.parse("2023-04-09T12:57:12.601Z"))
        assertThat(result.atZone(ZoneId.of("Etc/GMT+12")))
            .isEqualTo(ZonedDateTime.parse("2023-04-09T00:57:12.601-12:00[Etc/GMT+12]"))
    }

    @Test
    internal fun `should schedule when the offSet GMT+05H30`() = testDispatcherProvider.runTest {
        // given
        val dailyScheduling = DailyScheduling(
            timeZone = "Asia/Colombo",
            restrictions = setOf(1, 2, 3, 7)
        )
        val instant =
            Instant.parse("2023-04-06T12:57:12.601Z")
        assertThat(instant.atZone(ZoneId.of("Asia/Colombo")))
            .isEqualTo(ZonedDateTime.parse("2023-04-06T18:27:12.601+05:30[Asia/Colombo]"))

        // when
        val result = dailyScheduling.nextSchedule(instant)

        // then
        assertThat(result).isEqualTo(Instant.parse("2023-04-09T12:57:12.601Z"))
        assertThat(result.atZone(ZoneId.of("Asia/Colombo")))
            .isEqualTo(ZonedDateTime.parse("2023-04-09T18:27:12.601+05:30[Asia/Colombo]"))
    }

    @Test
    internal fun `should schedule when it matches the next week`() = testDispatcherProvider.runTest {
        // given
        val dailyScheduling = DailyScheduling(
            timeZone = "Africa/Douala",
            restrictions = setOf(2, 3, 5)
        )
        val instant =
            Instant.parse("2023-04-07T12:57:12.601Z")
        assertThat(instant.atZone(ZoneId.of("Africa/Douala")))
            .isEqualTo(ZonedDateTime.parse("2023-04-07T13:57:12.601+01:00[Africa/Douala]"))

        // when
        val result = dailyScheduling.nextSchedule(instant)

        // then
        assertThat(result).isEqualTo(Instant.parse("2023-04-11T12:57:12.601Z"))
        assertThat(result.atZone(ZoneId.of("Africa/Douala")))
            .isEqualTo(ZonedDateTime.parse("2023-04-11T13:57:12.601+01:00[Africa/Douala]"))
    }
}