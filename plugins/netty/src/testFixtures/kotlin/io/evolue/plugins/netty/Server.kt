package io.evolue.plugins.netty

import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Interface of a test server usable as a JUnit5 extension.
 *
 * @author Eric Jessé
 */
interface Server : BeforeAllCallback, AfterAllCallback {

    val port: Int

    fun start()

    fun stop()

    override fun beforeAll(context: ExtensionContext) {
        // If the test suite has nested classes, the action is only performed on the upper class.
        if (context.requiredTestClass.enclosingClass == null) {
            this.start()
        }
    }

    override fun afterAll(context: ExtensionContext) {
        // If the test suite has nested classes, the action is only performed on the upper class.
        if (context.requiredTestClass.enclosingClass == null) {
            this.stop()
        }
    }
}