package io.qalipsis.api.sync

import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.test.time.QalipsisTimeAssertions
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * @author Eric Jessé
 */
internal class LatchTest {

    @Test
    internal fun `should block calls at start until release`() {
        // given
        val latch = Latch(true)
        Assertions.assertTrue(latch.isLocked)
        val releaseTimes = concurrentSet<Instant>()
        val suspendedCountLatch = SuspendedCountLatch(3)

        repeat(3) {
            GlobalScope.launch {
                latch.await()
                releaseTimes.add(Instant.now())
                suspendedCountLatch.decrement()
            }
        }

        // when
        Thread.sleep(50)
        val now = Instant.now()
        runBlocking {
            latch.release()
            suspendedCountLatch.await()
        }

        // then
        Assertions.assertFalse(latch.isLocked)
        releaseTimes.forEach { releaseTime -> QalipsisTimeAssertions.assertAfterOrEqual(now, releaseTime) }
    }

    @Test
    internal fun `should not block calls at start until release`() {
        // given
        val latch = Latch(false)
        Assertions.assertFalse(latch.isLocked)
        val releaseTimes = concurrentSet<Instant>()
        val suspendedCountLatch = SuspendedCountLatch(3)

        repeat(3) {
            GlobalScope.launch {
                latch.await()
                releaseTimes.add(Instant.now())
                suspendedCountLatch.decrement()
            }
        }

        // when
        Thread.sleep(50)
        val now = Instant.now()
        runBlocking {
            latch.release()
            suspendedCountLatch.await()
        }

        // then
        Assertions.assertFalse(latch.isLocked)
        releaseTimes.forEach { releaseTime -> QalipsisTimeAssertions.assertBefore(now, releaseTime) }
    }

    @Test
    internal fun `should lock and release and lock and release`() {
        // given
        val latch = Latch(false)
        val releaseTimes = concurrentSet<Instant>()
        val suspendedCountLatch = SuspendedCountLatch(3)

        // when
        runBlocking {
            latch.lock()
        }

        // then
        Assertions.assertTrue(latch.isLocked)

        // when
        repeat(3) {
            GlobalScope.launch {
                latch.await()
                releaseTimes.add(Instant.now())
                suspendedCountLatch.decrement()
            }
        }
        Thread.sleep(50)
        var now = Instant.now()
        runBlocking {
            latch.release()
            suspendedCountLatch.await()
        }

        // then
        Assertions.assertFalse(latch.isLocked)
        releaseTimes.forEach { releaseTime -> QalipsisTimeAssertions.assertAfterOrEqual(now, releaseTime) }
        releaseTimes.clear()

        // when
        suspendedCountLatch.reset()
        runBlocking {
            latch.lock()
        }

        // then
        Assertions.assertTrue(latch.isLocked)

        // when
        repeat(3) {
            GlobalScope.launch {
                latch.await()
                releaseTimes.add(Instant.now())
                suspendedCountLatch.decrement()
            }
        }
        Thread.sleep(50)
        now = Instant.now()
        runBlocking {
            latch.release()
            suspendedCountLatch.await()
        }

        // then
        Assertions.assertFalse(latch.isLocked)
        releaseTimes.forEach { releaseTime -> QalipsisTimeAssertions.assertAfterOrEqual(now, releaseTime) }
    }
}
