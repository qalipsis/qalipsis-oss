package io.qalipsis.api.sync

import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.test.time.QalipsisTimeAssertions
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * @author Eric Jessé
 */
internal class LatchTest {

    @Test
    internal fun `should block calls at start until release`() = runBlockingTest {
        // given
        val latch = Latch(true)
        Assertions.assertTrue(latch.isLocked)
        val releaseTimes = concurrentSet<Instant>()
        val suspendedCountLatch = SuspendedCountLatch(3)

        repeat(3) {
            launch {
                latch.await()
                releaseTimes.add(Instant.now())
                suspendedCountLatch.decrement()
            }
        }

        // when
        Thread.sleep(50)
        val now = Instant.now()
            latch.release()
            suspendedCountLatch.await()

        // then
        Assertions.assertFalse(latch.isLocked)
        releaseTimes.forEach { releaseTime -> QalipsisTimeAssertions.assertAfterOrEqual(now, releaseTime) }
    }

    @Test
    internal fun `should not block calls at start until release`() = runBlockingTest {
        // given
        val latch = Latch(false)
        Assertions.assertFalse(latch.isLocked)
        val releaseTimes = concurrentSet<Instant>()
        val suspendedCountLatch = SuspendedCountLatch(3)

        repeat(3) {
            launch {
                latch.await()
                releaseTimes.add(Instant.now())
                suspendedCountLatch.decrement()
            }
        }

        // when
        Thread.sleep(50)
        val now = Instant.now()
            latch.release()
            suspendedCountLatch.await()

        // then
        Assertions.assertFalse(latch.isLocked)
        releaseTimes.forEach { releaseTime -> QalipsisTimeAssertions.assertBefore(now, releaseTime) }
    }

    @Test
    internal fun `should lock and release and lock and release`() = runBlockingTest {
        // given
        val latch = Latch(false)
        val releaseTimes = concurrentSet<Instant>()
        val suspendedCountLatch = SuspendedCountLatch(3)

        // when
        latch.lock()

        // then
        Assertions.assertTrue(latch.isLocked)

        // when
        repeat(3) {
            launch {
                latch.await()
                releaseTimes.add(Instant.now())
                suspendedCountLatch.decrement()
            }
        }
        Thread.sleep(50)
        var now = Instant.now()
            latch.release()
            suspendedCountLatch.await()

        // then
        Assertions.assertFalse(latch.isLocked)
        releaseTimes.forEach { releaseTime -> QalipsisTimeAssertions.assertAfterOrEqual(now, releaseTime) }
        releaseTimes.clear()

        // when
        suspendedCountLatch.reset()
            latch.lock()

        // then
        Assertions.assertTrue(latch.isLocked)

        // when
        repeat(3) {
            launch {
                latch.await()
                releaseTimes.add(Instant.now())
                suspendedCountLatch.decrement()
            }
        }
        Thread.sleep(50)
        now = Instant.now()
            latch.release()
            suspendedCountLatch.await()

        // then
        Assertions.assertFalse(latch.isLocked)
        releaseTimes.forEach { releaseTime -> QalipsisTimeAssertions.assertAfterOrEqual(now, releaseTime) }
    }
}
