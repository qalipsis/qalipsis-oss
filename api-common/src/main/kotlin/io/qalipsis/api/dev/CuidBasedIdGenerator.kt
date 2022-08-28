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
