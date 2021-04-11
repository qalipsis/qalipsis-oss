package io.qalipsis.api.dev

import io.qalipsis.api.lang.IdGenerator
import java.util.UUID

import javax.inject.Singleton

/**
 * Implementation of [IdGenerator] using random [UUID]s.
 *
 * @author Eric Jessé
 */
@Singleton
class UuidBasedIdGenerator : IdGenerator {

    override fun long(): String {
        return UUID.randomUUID().toString().toLowerCase().replace("-", "")
    }

    override fun short(): String {
        return UUID.randomUUID().toString().substring(25, 35).toLowerCase()
    }

}
