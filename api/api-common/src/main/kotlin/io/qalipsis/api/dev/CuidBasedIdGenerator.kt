package io.qalipsis.api.dev

import cool.graph.cuid.Cuid
import io.micronaut.context.annotation.Primary
import io.qalipsis.api.lang.IdGenerator
import jakarta.inject.Singleton

/**
 * Implementation of [IdGenerator] using random [Cuid]s.
 *
 * @author Eric Jessé
 */
@Primary
@Singleton
class CuidBasedIdGenerator : IdGenerator {

    override fun long(): String {
        return Cuid.createCuid()
    }

    override fun short(): String {
        return Cuid.createCuid().substring(15, 25).lowercase()
    }

}
