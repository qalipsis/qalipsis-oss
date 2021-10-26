package io.qalipsis.test.coroutines

import io.qalipsis.api.coroutines.CoroutineScopeProvider
import io.qalipsis.api.coroutines.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

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
     * Executes the test body with a normal dispatcher. It is however recommended to use [runTest] whenever possible.
     */
    fun run(testBody: suspend CoroutineScope.() -> Unit) {
        normalDispatcherProvider.global.launch(normalDispatcherProvider.default(), CoroutineStart.DEFAULT, testBody)
        normalDispatcherProvider.default()[Job]?.children?.filter { it.isActive }?.forEach { it.cancel() }
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