/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package io.qalipsis.test.mockk

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

@Suppress("CHANGING_ARGUMENTS_EXECUTION_ORDER_FOR_NAMED_VARARGS")
inline fun <reified T : Any> relaxedMockk(
    vararg moreInterfaces: KClass<*> = emptyArray(),
    block: T.() -> Unit = {}
) = mockk(relaxed = true, moreInterfaces = moreInterfaces, block = block)

@Suppress("CHANGING_ARGUMENTS_EXECUTION_ORDER_FOR_NAMED_VARARGS")
inline fun <reified T : Any> relaxedMockk(
    name: String? = null,
    vararg moreInterfaces: KClass<*> = emptyArray(),
    block: T.() -> Unit = {}
) = mockk(name = name, relaxed = true, moreInterfaces = moreInterfaces, block = block)

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

@ExtendWith(MockKExtension::class, MockkCleanRecordedCallsExtension::class)
annotation class CleanMockkRecordedCalls
