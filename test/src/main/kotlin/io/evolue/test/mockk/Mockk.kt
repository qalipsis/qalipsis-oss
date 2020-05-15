package io.evolue.test.mockk

import io.mockk.MockKVerificationScope
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify

/**
 *
 * @author Eric Jessé
 */

inline fun <reified T : Any> relaxedMockk(block: T.() -> Unit = {}) = mockk<T>(relaxed = true, block = block)

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