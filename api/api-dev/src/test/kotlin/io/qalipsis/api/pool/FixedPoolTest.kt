package io.qalipsis.api.pool

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.index
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isSameAs
import assertk.assertions.isTrue
import io.mockk.coEvery
import io.mockk.confirmVerified
import io.mockk.spyk
import io.qalipsis.api.io.Closeable
import io.qalipsis.api.lang.concurrentSet
import io.qalipsis.api.sync.SuspendedCountLatch
import io.qalipsis.test.assertk.typedProp
import io.qalipsis.test.mockk.coVerifyNever
import io.qalipsis.test.mockk.coVerifyOnce
import io.qalipsis.test.mockk.relaxedMockk
import io.qalipsis.test.utils.getProperty
import io.qalipsis.test.utils.setProperty
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.atomic.AtomicInteger

internal class FixedPoolTest {

    @Test
    @Timeout(1)
    internal fun `should create a pool with 10 items`() = runBlocking {
        // when
        val pool = FixedPool(10) { relaxedMockk<MyTestObject>() }.awaitReadiness()

        // then
        assertThat(pool).all {
            typedProp<List<MyTestObject>>("items").hasSize(10)
            typedProp<Channel<MyTestObject>>("itemPool").transform("itemPool is empty") { it.isEmpty }.isFalse()
        }

        // when
        repeat(10) { pool.acquire() }

        // then
        assertThat(pool).all {
            typedProp<List<MyTestObject>>("items").hasSize(10)
            typedProp<Channel<MyTestObject>>("itemPool").transform("itemPool is empty") { it.isEmpty }.isTrue()
        }

        pool.close()
    }

    @Test
    @Timeout(5)
    internal fun `should only acquire items from the pool`() = runBlocking {
        // given
        val pool = FixedPool(5) { relaxedMockk<MyTestObject>() }.awaitReadiness()
        val uniqueItems = concurrentSet<MyTestObject>()
        val latch = SuspendedCountLatch(100)

        // when
        repeat(100) {
            this.launch {
                val item = pool.acquire()
                uniqueItems.add(item)
                pool.release(item)
                latch.decrement()
            }
        }

        // then
        latch.await()
        // The captured values always are the same from the pool.
        assertThat(uniqueItems).hasSize(5)

        pool.close()
    }

    @Test
    @Timeout(5)
    internal fun `should execute only with items from the pool`() = runBlocking {
        // given
        val pool = FixedPool(5) { relaxedMockk<MyTestObject>() }.awaitReadiness()
        val uniqueItems = concurrentSet<MyTestObject>()
        val latch = SuspendedCountLatch(100)

        // when
        repeat(100) {
            this.launch {
                pool.withPoolItem { item ->
                    uniqueItems.add(item)
                }
                latch.decrement()
            }
        }

        // then
        latch.await()
        // The captured values always are the same from the pool.
        assertThat(uniqueItems).hasSize(5)

        pool.close()
    }


    @Test
    @Timeout(1)
    internal fun `should execute, return the value and return the pooled item even in case of exception`() =
        runBlocking {
            // given
            val pool = FixedPool(1) { relaxedMockk<MyTestObject>() }.awaitReadiness()

            // when
            val result = pool.withPoolItem { 123.456 }

            // then
            assertThat(result).isEqualTo(123.456)
            assertThat(pool).all {
                typedProp<List<MyTestObject>>("items").hasSize(1)
                typedProp<Channel<MyTestObject>>("itemPool").transform("itemPool is empty") { it.isEmpty }.isFalse()
            }

            pool.close()
        }

    @Test
    @Timeout(1)
    internal fun `should execute and return even in case of exception`() = runBlocking {
        // given
        val pool = FixedPool(1) { relaxedMockk<MyTestObject>() }.awaitReadiness()

        // when
        assertThrows<RuntimeException> {
            pool.withPoolItem {
                throw RuntimeException()
            }
        }

        // then
        assertThat(pool).all {
            typedProp<List<MyTestObject>>("items").hasSize(1)
            typedProp<Channel<MyTestObject>>("itemPool").transform("itemPool is empty") { it.isEmpty }.isFalse()
        }

        pool.close()
    }

    @Test
    @Timeout(1)
    internal fun `should acquire and release without health checks`() = runBlocking {
        // given
        val cleanerCalls = AtomicInteger(0)
        val healthChecks = AtomicInteger(0)
        val additionToInternalPool = SuspendedCountLatch(1)
        val pool = FixedPool(
            1,
            cleaner = { cleanerCalls.incrementAndGet() },
            healthCheck = { healthChecks.incrementAndGet(); false }) { relaxedMockk<MyTestObject>() }.awaitReadiness()
        val spiedInternalPool = spyk(pool.getProperty<Channel<MyTestObject>>("itemPool")) {
            coEvery { send(any()) } coAnswers {
                additionToInternalPool.decrement()
                this.invocation.originalCall.invoke()
            }
        }
        pool.setProperty("itemPool", spiedInternalPool)

        // when
        val result = pool.acquire()
        pool.release(result)

        // then
        additionToInternalPool.await()
        assertThat(cleanerCalls.get()).isEqualTo(1)
        assertThat(healthChecks.get()).isEqualTo(0)
    }

    @Test
    @Timeout(1)
    internal fun `should check the health on acquire and returns the item when healthy`() = runBlocking {
        // given
        val healthChecks = AtomicInteger(0)
        val pooledItem = relaxedMockk<MyTestObject>()
        val pool = FixedPool(
            1,
            checkOnAcquire = true,
            healthCheck = { healthChecks.incrementAndGet(); true }) { pooledItem }.awaitReadiness()

        // when
        val result = pool.acquire()

        // then
        assertThat(healthChecks.get()).isEqualTo(1)
        assertThat(result).isSameAs(pooledItem)
        assertThat(pool).all {
            typedProp<List<MyTestObject>>("items").all {
                hasSize(1)
                index(0).isSameAs(result)
            }
            typedProp<Channel<MyTestObject>>("itemPool").transform("itemPool is empty") { it.isEmpty }.isTrue()
        }

        // when
        pool.release(result)

        // then
        assertThat(healthChecks.get()).isEqualTo(1)
        assertThat(pool).all {
            typedProp<List<MyTestObject>>("items").all {
                hasSize(1)
                index(0).isSameAs(pooledItem)
            }
            typedProp<Channel<MyTestObject>>("itemPool").transform("itemPool is empty") { it.isEmpty }.isFalse()
        }

        confirmVerified(pooledItem)

        pool.close()
    }

    @Test
    @Timeout(1)
    internal fun `should check the health on acquire and returns a new item when unhealthy`() = runBlocking {
        // given
        val healthChecks = AtomicInteger(0)

        val pooledItems = mutableListOf(relaxedMockk<MyTestObject>(), relaxedMockk())
        val originalPooledItem = pooledItems[0]
        val substitutePooledItem = pooledItems[1]
        val pool = FixedPool(
            1,
            checkOnAcquire = true,
            healthCheck = { healthChecks.incrementAndGet(); false }) { pooledItems.removeAt(0) }.awaitReadiness()

        // when
        val result = pool.acquire()

        // then
        assertThat(healthChecks.get()).isEqualTo(1)
        assertThat(result).isSameAs(substitutePooledItem)
        assertThat(pool).all {
            typedProp<List<MyTestObject>>("items").all {
                hasSize(1)
                index(0).isSameAs(result)
            }
            typedProp<Channel<MyTestObject>>("itemPool").transform("itemPool is empty") { it.isEmpty }.isTrue()
        }

        // when
        pool.release(result)

        // then
        assertThat(healthChecks.get()).isEqualTo(1)
        assertThat(pool).all {
            typedProp<List<MyTestObject>>("items").all {
                hasSize(1)
                index(0).isSameAs(substitutePooledItem)
            }
            typedProp<Channel<MyTestObject>>("itemPool").transform("itemPool is empty") { it.isEmpty }.isFalse()
        }

        coVerifyOnce { originalPooledItem.close() }
        coVerifyNever { substitutePooledItem.close() }

        pool.close()
    }

    @Test
    @Timeout(10)
    internal fun `should check the health on release and add the item to the pool when healthy`() = runBlocking {
        // given
        val healthChecks = AtomicInteger(0)
        val additionToInternalPool = SuspendedCountLatch(1)
        val pooledItem = relaxedMockk<MyTestObject>()
        val pool = FixedPool(
            1,
            checkOnRelease = true,
            healthCheck = { healthChecks.incrementAndGet(); true }) { pooledItem }.awaitReadiness()
        val spiedInternalPool = spyk(pool.getProperty<Channel<MyTestObject>>("itemPool")) {
            coEvery { send(any()) } coAnswers {
                additionToInternalPool.decrement()
                this.invocation.originalCall.invoke()
            }
        }
        pool.setProperty("itemPool", spiedInternalPool)

        // when
        val result = pool.acquire()

        // then
        assertThat(healthChecks.get()).isEqualTo(0)
        assertThat(result).isSameAs(pooledItem)
        assertThat(pool).all {
            typedProp<List<MyTestObject>>("items").all {
                hasSize(1)
                index(0).isSameAs(result)
            }
            typedProp<Channel<MyTestObject>>("itemPool").transform("itemPool is empty") { it.isEmpty }.isTrue()
        }

        // when
        pool.release(result)

        // then
        additionToInternalPool.await()
        assertThat(healthChecks.get()).isEqualTo(1)
        assertThat(pool).all {
            typedProp<List<MyTestObject>>("items").all {
                hasSize(1)
                index(0).isSameAs(pooledItem)
            }
            typedProp<Channel<MyTestObject>>("itemPool").transform("itemPool is empty") { it.isEmpty }.isFalse()
        }

        confirmVerified(pooledItem)

        pool.close()
    }

    @Test
    @Timeout(3)
    internal fun `should check the health on release and add a new item to the pool when unhealthy`() = runBlocking {
        // given
        val healthChecks = AtomicInteger(0)
        val additionToInternalPool = SuspendedCountLatch(1)
        val pooledItems = mutableListOf(relaxedMockk<MyTestObject>(), relaxedMockk())
        val originalPooledItem = pooledItems[0]
        val substitutePooledItem = pooledItems[1]
        val pool = FixedPool(
            1,
            checkOnRelease = true,
            healthCheck = { healthChecks.incrementAndGet(); false }) { pooledItems.removeAt(0) }.awaitReadiness()
        val spiedInternalPool = spyk(pool.getProperty<Channel<MyTestObject>>("itemPool")) {
            coEvery { send(any()) } coAnswers {
                additionToInternalPool.decrement()
                this.invocation.originalCall.invoke()
            }
        }
        pool.setProperty("itemPool", spiedInternalPool)

        // when
        val result = pool.acquire()

        // then
        assertThat(healthChecks.get()).isEqualTo(0)
        assertThat(result).isSameAs(originalPooledItem)
        assertThat(pool).all {
            typedProp<List<MyTestObject>>("items").all {
                hasSize(1)
                index(0).isSameAs(result)
            }
            typedProp<Channel<MyTestObject>>("itemPool").transform("itemPool is empty") { it.isEmpty }.isTrue()
        }

        // when
        pool.release(result)

        // then
        additionToInternalPool.await()
        assertThat(healthChecks.get()).isEqualTo(1)
        assertThat(pool).all {
            typedProp<List<MyTestObject>>("items").all {
                hasSize(1)
                index(0).isSameAs(substitutePooledItem)
            }
            typedProp<Channel<MyTestObject>>("itemPool").transform("itemPool is empty") { it.isEmpty }.isFalse()
        }
        coVerifyOnce { originalPooledItem.close() }
        coVerifyNever { substitutePooledItem.close() }

        pool.close()
    }

    @Test
    @Timeout(1)
    internal fun `should not serve items after closing`(): Unit = runBlocking {
        // given
        val pool = FixedPool(1) { relaxedMockk<MyTestObject>() }.awaitReadiness()

        // when
        pool.close()

        // then
        assertThrows<IllegalStateException> { pool.withPoolItem { } }
        assertThrows<IllegalStateException> { pool.acquire() }

        assertThat(pool).all {
            typedProp<List<MyTestObject>>("items").isEmpty()
            typedProp<Channel<MyTestObject>>("itemPool").transform { it.isClosedForReceive }.isTrue()
        }
    }

    @Test
    @Timeout(1)
    internal fun `should close all the items when closing the pool`(): Unit = runBlocking {
        // given
        val mocks = mutableListOf<MyTestObject>()
        val pool = FixedPool(5) { relaxedMockk<MyTestObject>().also { mocks.add(it) } }.awaitReadiness()

        // when
        pool.acquire()
        pool.acquire()
        pool.acquire()

        pool.close()

        // then
        assertThat(mocks).hasSize(5)
        mocks.forEach {
            coVerifyOnce { it.close() }
        }
    }

    @Test
    @Timeout(3)
    internal fun `should ignore release after closing`(): Unit = runBlocking {
        // given
        val pool = FixedPool(1) { relaxedMockk<MyTestObject>() }.awaitReadiness()

        // when
        val item = pool.acquire()
        pool.close()
        pool.release(item)

        // then
        assertThat(pool).all {
            typedProp<List<MyTestObject>>("items").isEmpty()
            typedProp<Channel<MyTestObject>>("itemPool").transform { it.isClosedForReceive }.isTrue()
        }
    }

    private class MyTestObject : Closeable {

        override suspend fun close() {
            // The object is mocked.
        }
    }
}
