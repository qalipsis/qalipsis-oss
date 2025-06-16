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

package io.qalipsis.test.coroutines

import io.qalipsis.api.coroutines.CoroutineScopeProvider
import io.qalipsis.api.coroutines.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

class TestDispatcherProvider(private val testDispatcher: TestCoroutineDispatcher = TestCoroutineDispatcher()) :
    BeforeAllCallback, AfterAllCallback, DispatcherProvider {

    private val normalDispatcherProvider = TestCoroutineScopeProvider()

    override fun default(): CoroutineDispatcher {
        return normalDispatcherProvider.default()
    }

    override fun io(): CoroutineDispatcher {
        return normalDispatcherProvider.io()
    }

    override fun unconfined(): CoroutineDispatcher {
        return normalDispatcherProvider.unconfined()
    }

    override fun afterAll(context: ExtensionContext?) {
        Dispatchers.resetMain()
        testDispatcher.cleanupTestCoroutines()
    }

    override fun beforeAll(p0: ExtensionContext?) {
        Dispatchers.setMain(normalDispatcherProvider.default())
    }

    /**
     * Executes the test body with a test dispatcher, that fails if some started coroutines are not completed
     * at the end of the test.
     */
    fun runTest(testBody: suspend CoroutineScope.() -> Unit): Unit = testDispatcher.runBlockingTest(testBody)

    /**
     * Executes the test body with a normal dispatcher and stops all the started children coroutines.
     * It is however recommended to use [runTest] whenever possible.
     */
    fun run(testBody: suspend CoroutineScope.() -> Unit) {
        val scope = normalDispatcherProvider.global
        val latch = CountDownLatch(1)
        val resultSlot = AtomicReference<Result<Unit>>()
        val job = scope.launch {
            try {
                testBody()
                resultSlot.set(Result.success(Unit))
            } catch (t: Throwable) {
                resultSlot.set(Result.failure(t))
            } finally {
                latch.countDown()
            }
        }
        latch.await()
        job.children.filter { it.isActive }.forEach {
            kotlin.runCatching { it.cancel() }
        }
        resultSlot.get().getOrThrow()
    }
}

class TestCoroutineScopeProvider : AfterAllCallback, CoroutineScopeProvider, DispatcherProvider {

    private val globalContext = newFixedThreadPoolContext(4, "global-scope")

    private val campaignContext = newFixedThreadPoolContext(2, "campaign-scope")

    private val ioContext = newFixedThreadPoolContext(2, "io-scope")

    private val backgroundContext = newFixedThreadPoolContext(2, "background-scope")

    private val orchestrationContext = newFixedThreadPoolContext(2, "orchestration-scope")

    override val global: CoroutineScope = CoroutineScope(globalContext)

    override val campaign: CoroutineScope = CoroutineScope(campaignContext)

    override val io: CoroutineScope = CoroutineScope(ioContext)

    override val background: CoroutineScope = CoroutineScope(backgroundContext)

    override val orchestration: CoroutineScope = CoroutineScope(orchestrationContext)

    override fun default(): CoroutineDispatcher {
        return globalContext
    }

    override fun io(): CoroutineDispatcher {
        return ioContext
    }

    override fun unconfined(): CoroutineDispatcher {
        return Dispatchers.Unconfined
    }

    override fun close() = Unit

    override fun afterAll(context: ExtensionContext?) {
        listOf(globalContext, campaignContext, ioContext, backgroundContext, orchestrationContext)
            .forEach { kotlin.runCatching { it.close() } }
    }

}