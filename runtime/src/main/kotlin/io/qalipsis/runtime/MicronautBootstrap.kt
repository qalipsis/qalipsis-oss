package io.qalipsis.runtime

import io.micronaut.context.annotation.Context
import io.qalipsis.api.logging.LoggerHelper.logger

/**
 *
 * @author Eric Jessé
 */
@Context
class MicronautBootstrap(
    configurers: Collection<Configurer>
) {

    init {
        configurers.forEach { it.configure() }
    }

    companion object {

        @JvmStatic
        private val log = logger()
    }
}
