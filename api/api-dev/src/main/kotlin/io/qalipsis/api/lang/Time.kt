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
