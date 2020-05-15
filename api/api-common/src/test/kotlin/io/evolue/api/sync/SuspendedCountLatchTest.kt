package io.evolue.api.sync

import io.evolue.test.time.EvolueTimeAssertions
import io.evolue.test.time.coMeasureTime
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author Eric Jessé
 */
internal class SuspendedCountLatchTest {

    @Test
    @Timeout(1)
    internal fun shouldAwaitUntilZeroAndExecuteOnDoneAction() {
        // given
        val doneFlag = AtomicBoolean(false)
        val rendezVousFlag = Channel<Unit>(Channel.RENDEZVOUS)
        val countLatch = SuspendedCountLatch(5) { doneFlag.set(true) }
        val delayMs = 100L

        GlobalScope.launch {
            rendezVousFlag.send(Unit)
            delay(delayMs)
            countLatch.decrement(3)
            countLatch.decrement()
            // Count is now 1.
            countLatch.increment(4)
            countLatch.increment()
            // Count is now 6.
            countLatch.decrement(6)
        }

        // when
        Assertions.assertTrue(countLatch.isSuspended())
        val executionDuration = coMeasureTime {
            rendezVousFlag.receive()
            countLatch.await()
        }

        // then
        Assertions.assertFalse(countLatch.isSuspended())
        EvolueTimeAssertions.assertLongerOrEqualTo(Duration.ofMillis(delayMs), executionDuration)
        Assertions.assertTrue(doneFlag.get())
    }

    @Test
    @Timeout(1)
    internal fun shouldResetThenAwaitUntilZeroAndExecuteOnDoneAction() {
        // given
        val doneFlag = AtomicInteger()
        val rendezVousFlag = Channel<Unit>(Channel.RENDEZVOUS)
        val countLatch = SuspendedCountLatch(1) { doneFlag.incrementAndGet() }

        GlobalScope.launch {
            rendezVousFlag.send(Unit)
            delay(50)
            countLatch.decrement()
            delay(50)
            countLatch.reset()
            delay(50)
            countLatch.decrement()
        }

        // when
        runBlocking {
            rendezVousFlag.receive()
            Assertions.assertTrue(countLatch.isSuspended())
            countLatch.await()
            Assertions.assertFalse(countLatch.isSuspended())
            // Wait until reset.
            delay(60)
            Assertions.assertTrue(countLatch.isSuspended())
            countLatch.await()
            Assertions.assertFalse(countLatch.isSuspended())
        }
        // then
        Assertions.assertEquals(2, doneFlag.get())
    }

    @Test
    @Timeout(1)
    internal fun shouldNotSuspendWhenInitialValueIsZeroButNotExecuteOnDoneAction() {
        // given
        val doneFlag = AtomicBoolean(false)
        val countLatch = SuspendedCountLatch(0) { doneFlag.set(true) }

        // when
        Assertions.assertFalse(countLatch.isSuspended())
        runBlocking {
            countLatch.await()
        }

        // then
        Assertions.assertFalse(doneFlag.get())
    }

    @Test
    @Timeout(1)
    internal fun shouldReleaseButNotExecuteOnDoneAction() {
        // given
        val doneFlag = AtomicBoolean(false)
        val countLatch = SuspendedCountLatch(20) { doneFlag.set(true) }

        GlobalScope.launch {
            delay(50)
            countLatch.release()
        }

        // when
        Assertions.assertTrue(countLatch.isSuspended())
        runBlocking {
            countLatch.await()
        }

        // then
        Assertions.assertFalse(doneFlag.get())
    }

    @Test
    @Timeout(1)
    internal fun shouldThrowExceptionWhenDecrementingToNegative() {
        // given
        val countLatch = SuspendedCountLatch(0)

        // when
        assertThrows<IllegalArgumentException> {
            runBlocking {
                countLatch.decrement()
            }
        }
    }

    @Test
    @Timeout(1)
    internal fun shouldThrowExceptionWhenInitialValueIsNegative() {
        assertThrows<IllegalArgumentException> {
            SuspendedCountLatch(-1)
        }
    }
}