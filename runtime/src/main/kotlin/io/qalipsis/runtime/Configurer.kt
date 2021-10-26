package io.qalipsis.runtime

import io.micronaut.core.order.Ordered

/**
 *
 * @author Eric Jessé
 */
interface Configurer : Ordered {

    fun configure()

}
