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

package io.qalipsis.api.lang

import java.time.Duration
import java.time.Instant

/**
 *
 * @author Eric Jessé
 */
fun Long.durationSinceMillis(): Duration = Duration.ofMillis(System.currentTimeMillis() - this)
fun Long.durationSinceNanos(): Duration = Duration.ofNanos(System.nanoTime() - this)
fun Instant.durationSince(): Duration = this.toEpochMilli().durationSinceMillis()

fun Duration.isLongerThan(duration: Duration) = this > duration
fun Duration.isLongerThan(duration: Long) = this.toMillis() > duration
fun Duration.isLongerOrEqualTo(duration: Duration) = this >= duration
fun Duration.isLongerOrEqualTo(duration: Long) = this.toMillis() >= duration
fun Duration.isShorterThan(duration: Duration) = this < duration
fun Duration.isShorterThan(duration: Long) = this.toMillis() < duration
fun Duration.isShorterOrEqualTo(duration: Duration) = this <= duration
fun Duration.isShorterOrEqualTo(duration: Long) = this.toMillis() <= duration

fun Long.millis() = Duration.ofMillis(this)
fun Long.seconds() = Duration.ofSeconds(this)
fun Int.millis() = Duration.ofMillis(this.toLong())
fun Int.seconds() = Duration.ofSeconds(this.toLong())
