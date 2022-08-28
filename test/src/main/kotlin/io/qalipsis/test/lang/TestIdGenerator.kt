/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

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
