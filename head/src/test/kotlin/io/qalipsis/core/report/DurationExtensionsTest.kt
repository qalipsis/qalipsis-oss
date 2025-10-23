/*
 * QALIPSIS
 * Copyright (C) 2025 AERIS IT Solutions GmbH
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

package io.qalipsis.core.report

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.qalipsis.core.head.report.toReadableString
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * @author Francisca Eze
 */
class DurationExtensionsTest {

    @Test
    fun `it formats days hours and minutes`() {
        //given
        val duration = Duration.ofDays(2).plusHours(3).plusMinutes(15)

        //when + then
        assertThat(duration.toReadableString()).isEqualTo("2d 3h 15m")
    }

    @Test
    fun `it formats hours and minutes`() {
        //given
        val duration = Duration.ofHours(4).plusMinutes(30)

        //when + then
        assertThat(duration.toReadableString()).isEqualTo("4h 30m")
    }

    @Test
    fun `it formats only minutes`() {
        //given
        val duration = Duration.ofMinutes(45)

        //when + then
        assertThat(duration.toReadableString()).isEqualTo("45m")
    }

    @Test
    fun `it formats only seconds`() {
        //given
        val duration = Duration.ofSeconds(30)

        //when + then
        assertThat(duration.toReadableString()).isEqualTo("30s")
    }

    @Test
    fun `zero duration becomes 0s`() {
        //given
        val duration = Duration.ZERO

        //when + then
        assertThat(duration.toReadableString()).isEqualTo("0s")
    }

    @Test
    fun `seconds overflow into minutes`() {
        //given
        val duration = Duration.ofSeconds(61)

        //when + then
        assertThat(duration.toReadableString()).isEqualTo("1m 1s")
    }
}