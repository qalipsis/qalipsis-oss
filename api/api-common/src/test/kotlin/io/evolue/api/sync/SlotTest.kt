package io.evolue.api.sync

import io.evolue.api.lang.concurrentSet
import io.evolue.test.time.EvolueTimeAssertions
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * @author Eric Jessé
 */
internal class SlotTest {

    @Test
    internal fun `should block calls at start until a value is set`() {
        // given
        val slot = Slot<Unit>()
        Assertions.assertTrue(slot.isEmpty())
        Assertions.assertFalse(slot.isPresent())

        val releaseTimes = concurrentSet<Instant>()
        val suspendedCountLatch = SuspendedCountLatch(3)

        repeat(3) {
            GlobalScope.launch {
                Assertions.assertEquals(Unit, slot.get())
                releaseTimes.add(Instant.now())
                suspendedCountLatch.decrement()
            }
        }

        // when
        Thread.sleep(50)
        val now = Instant.now()
        runBlocking {
            slot.set(Unit)
            suspendedCountLatch.await()
        }

        // then
        Assertions.assertFalse(slot.isEmpty())
        Assertions.assertTrue(slot.isPresent())
        releaseTimes.forEach { releaseTime -> EvolueTimeAssertions.assertAfterOrEqual(now, releaseTime) }
    }

    @Test
    internal fun `should not block calls at start until a value is set`() {
        // given
        val slot = Slot(Unit)
        Assertions.assertFalse(slot.isEmpty())
        Assertions.assertTrue(slot.isPresent())

        val releaseTimes = concurrentSet<Instant>()
        val suspendedCountLatch = SuspendedCountLatch(3)

        repeat(3) {
            GlobalScope.launch {
                Assertions.assertEquals(Unit, slot.get())
                releaseTimes.add(Instant.now())
                suspendedCountLatch.decrement()
            }
        }

        // when
        Thread.sleep(50)
        val now = Instant.now()
        runBlocking {
            suspendedCountLatch.await()
        }

        // then
        Assertions.assertFalse(slot.isEmpty())
        Assertions.assertTrue(slot.isPresent())
        releaseTimes.forEach { releaseTime -> EvolueTimeAssertions.assertBefore(now, releaseTime) }
    }

    @Test
    internal fun `should remove and set and remove again`() {
        // given
        val slot = Slot(Unit)
        val releaseTimes = concurrentSet<Instant>()
        val suspendedCountLatch = SuspendedCountLatch(3)

        // when
        runBlocking {
            slot.remove()
        }

        // then
        Assertions.assertTrue(slot.isEmpty())
        Assertions.assertFalse(slot.isPresent())

        // when
        repeat(3) {
            GlobalScope.launch {
                Assertions.assertEquals(Unit, slot.get())
                releaseTimes.add(Instant.now())
                suspendedCountLatch.decrement()
            }
        }
        Thread.sleep(50)
        var now = Instant.now()
        runBlocking {
            slot.set(Unit)
            suspendedCountLatch.await()
        }

        // then
        Assertions.assertFalse(slot.isEmpty())
        Assertions.assertTrue(slot.isPresent())
        releaseTimes.forEach { releaseTime -> EvolueTimeAssertions.assertAfterOrEqual(now, releaseTime) }
        releaseTimes.clear()

        // when
        runBlocking {
            suspendedCountLatch.reset()
            slot.remove()
        }

        // then
        Assertions.assertTrue(slot.isEmpty())
        Assertions.assertFalse(slot.isPresent())

        // when
        repeat(3) {
            GlobalScope.launch {
                Assertions.assertEquals(Unit, slot.get())
                releaseTimes.add(Instant.now())
                suspendedCountLatch.decrement()
            }
        }
        Thread.sleep(50)
        now = Instant.now()
        runBlocking {
            slot.set(Unit)
            suspendedCountLatch.await()
        }

        // then
        Assertions.assertFalse(slot.isEmpty())
        Assertions.assertTrue(slot.isPresent())
        releaseTimes.forEach { releaseTime -> EvolueTimeAssertions.assertAfterOrEqual(now, releaseTime) }
    }
}