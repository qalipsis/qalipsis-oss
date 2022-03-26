package io.qalipsis.runtime

import io.qalipsis.runtime.bootstrap.QalipsisBootstrap
import kotlin.system.exitProcess

/**
 * Starter object to launch the application QALIPSIS as a JVM process.
 *
 * @author Eric Jessé
 */
internal object Qalipsis {

    private lateinit var qalipsisBootstrap: QalipsisBootstrap

    @JvmStatic
    fun main(args: Array<String>) {
        qalipsisBootstrap = QalipsisBootstrap()
        exitProcess(qalipsisBootstrap.start(args))
    }
}
