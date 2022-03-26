package io.qalipsis.runtime

import io.micronaut.context.annotation.Context

/**
 * Class to prepare and configure the Micronaut environment.
 *
 * @author Eric Jessé
 */
@Context
internal class MicronautBootstrap(
    configurers: Collection<Configurer>
) {

    init {
        configurers.forEach { it.configure() }
    }
}
