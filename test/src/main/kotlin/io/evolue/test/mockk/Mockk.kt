package io.evolue.test.mockk

import io.mockk.MockKVerificationScope
import io.mockk.clearAllMocks
import io.mockk.coVerify
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import kotlin.reflect.KClass

/**
 *
 * @author Eric Jessé
 */

inline fun <reified T : Any> relaxedMockk(vararg moreInterfaces: KClass<*> = emptyArray(), block: T.() -> Unit = {}) =
    mockk<T>(moreInterfaces = moreInterfaces, relaxed = true, block = block)

fun verifyOnce(verifyBlock: MockKVerificationScope.() -> Unit) = verify(exactly = 1, verifyBlock = verifyBlock)

fun coVerifyOnce(verifyBlock: suspend MockKVerificationScope.() -> Unit) =
    coVerify(exactly = 1, verifyBlock = verifyBlock)

fun verifyExactly(times: Int, verifyBlock: MockKVerificationScope.() -> Unit) =
    verify(exactly = times, verifyBlock = verifyBlock)

fun coVerifyExactly(times: Int, verifyBlock: suspend MockKVerificationScope.() -> Unit) =
    coVerify(exactly = times, verifyBlock = verifyBlock)

fun verifyNever(verifyBlock: MockKVerificationScope.() -> Unit) = verify(exactly = 0, verifyBlock = verifyBlock)

fun coVerifyNever(verifyBlock: suspend MockKVerificationScope.() -> Unit) =
    coVerify(exactly = 0, verifyBlock = verifyBlock)


@ExtendWith(MockKExtension::class, MockkCleanExtension::class)
annotation class WithMockk

/**
 * JUnit 5 extension in charge of cleaning all the current mocks.
 *
 * @author Eric Jessé
 */
class MockkCleanExtension : AfterEachCallback, AfterAllCallback {

    override fun afterEach(context: ExtensionContext?) {
        clearAllMocks()
    }

    override fun afterAll(context: ExtensionContext?) {
        unmockkAll()
    }
}

/**
 * JUnit 5 extension in charge of cleaning the recorded calls only.
 *
 * @author Eric Jessé
 */
class MockkCleanRecordedCallsExtension : AfterEachCallback, AfterAllCallback {

    override fun afterEach(context: ExtensionContext?) {
        clearAllMocks(answers = false)
    }

    override fun afterAll(context: ExtensionContext?) {
        unmockkAll()
    }
}

@ExtendWith(MockkCleanRecordedCallsExtension::class)
annotation class CleanMockkRecordedCalls
