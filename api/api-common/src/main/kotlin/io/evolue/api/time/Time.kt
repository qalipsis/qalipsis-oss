package io.evolue.api.time

import java.time.Duration
import java.time.Instant

/**
 *
 * @author Eric Jessé
 */
fun durationSinceMillis(start: Long) = Duration.ofMillis(System.currentTimeMillis() - start)

fun durationSinceNanos(start: Long) = Duration.ofNanos(System.nanoTime() - start)

fun durationSince(start: Instant) = durationSinceMillis(start.toEpochMilli())

fun Duration.isLongerThan(duration: Duration) = this.compareTo(duration) > 0
fun Duration.isLongerThan(duration: Long) = this.toMillis() > duration
fun Duration.isLongerOrEqualTo(duration: Duration) = this.compareTo(duration) >= 0
fun Duration.isLongerOrEqualTo(duration: Long) = this.toMillis() >= duration
fun Duration.isShorterThan(duration: Duration) = this.compareTo(duration) < 0
fun Duration.isShorterThan(duration: Long) = this.toMillis() < duration
fun Duration.isShorterOrEqualTo(duration: Duration) = this.compareTo(duration) <= 0
fun Duration.isShorterOrEqualTo(duration: Long) = this.toMillis() <= duration
