package io.qalipsis.test.lang

import io.qalipsis.api.dev.UuidBasedIdGenerator
import io.qalipsis.api.lang.IdGenerator

/**
 * [IdGenerator] for test purpose, that can be used as instance or statically.
 *
 * @author Eric Jessé
 */
class TestIdGenerator : IdGenerator by wrapped {

    companion object : IdGenerator {

        @JvmStatic
        private val wrapped = UuidBasedIdGenerator()

        override fun long() = wrapped.long()

        override fun short() = wrapped.short()

    }

}
