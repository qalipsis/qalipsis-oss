package io.evolue.test.coroutines

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * JUnit 5 extension in charge of cancelling the undone running coroutines to avoid frictions
 * between the tests.
 *
 * @author Eric Jessé
 */
class CoroutineCleanExtension : AfterEachCallback {

    override fun afterEach(context: ExtensionContext?) {
        try {
            GlobalScope.cancel()
        } catch (e: Exception) {
            // Just ignore.
        }
    }
}