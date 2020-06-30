package io.evolue.runtime

import io.evolue.api.logging.LoggerHelper.logger
import io.micronaut.context.annotation.Context

/**
 *
 * @author Eric Jessé
 */
@Context
class MicronautBootstrap(
    configurers: Collection<Configurer>
)  {

    init {
        configurers.forEach { it.configure() }
    }

    companion object {

        @Suppress("JAVA_CLASS_ON_COMPANION")
        @JvmStatic
        private val log = logger()
    }
}
